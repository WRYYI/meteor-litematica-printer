package com.kkllffaa.meteor_litematica_printer.crud.engine

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.*
import com.kkllffaa.meteor_litematica_printer.Modules.Tools.AutoTool
import com.kkllffaa.meteor_litematica_printer.crud.source.CursorProxy
import com.kkllffaa.meteor_litematica_printer.mixins.MultiPlayerGameModeAccessor
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.utils.world.BlockUtils
import meteordevelopment.orbit.EventHandler
import meteordevelopment.meteorclient.systems.modules.player.AutoEat as MeteorAutoEat
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.FireworkRocketItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.min
import meteordevelopment.meteorclient.systems.modules.player.AutoTool as MeteorAutoTool

/**
 * 常驻执行引擎：所有 CRUD 来源（投影构建 / Deleter / 后续工蜂…）共用的唯一“手”。
 *
 * 每 tick 三步走：
 *  1. [collectDemands]：按各来源的 mergePriority 合并诉求，高者独占坐标。
 *  2. [advance]：对照世界现状推进每个坐标的统一状态机 [ExecState]，并算出本 tick 应做的 [ActionKind]。
 *  3. [act]：一个 tick 只做一类动作——破坏 ＞ 交互 ＞ 放置；同类内部按 秒破 → posPriority → 多级几何排序 排序。
 *
 * 换物品全部下放：破坏由 AutoTool 自动切工具，放置由 PlaceSettings 自切方块，交互前回避会误触发的手持物（食物 / 烟花）。
 */
object Executor : Module(Addon.SettingsForCRUD, "Executor", "Shared CRUD execution engine.") {

    // ============================== 设置 ==============================

    private val sgBuild = settings.defaultGroup
    private val sgBreak = settings.createGroup("Break")
    private val sgRender = settings.createGroup("Render")

    private val buildDelay = sgBuild.add(
        IntSetting.Builder()
            .name("build-delay").description("Ticks between place/interact batches.")
            .defaultValue(2).range(0, 100).sliderRange(0, 40).build()
    )

    private val buildPerTick = sgBuild.add(
        IntSetting.Builder()
            .name("build-per-tick").description("Max place + interact actions per acting tick.")
            .defaultValue(3).range(1, 100).sliderRange(1, 100).build()
    )

    private val interactEnabled = sgBuild.add(
        BoolSetting.Builder()
            .name("enable-interaction")
            .description("Adjust block states by right-clicking (note blocks, repeaters, doors…).")
            .defaultValue(true).build()
    )

    /** 放置 / 交互的多级几何排序：预算受限时先做哪些坐标。 */
    private val placeOrder = LayeredSort(sgBuild, "build-order", GeometricSort.Nearest)

    private val breakDelay = sgBreak.add(
        IntSetting.Builder()
            .name("break-delay").description("Delay before mining the next non-instant block.")
            .defaultValue(0).range(0, 1024).sliderRange(0, 20).build()
    )

    private val randomDelay = sgBreak.add(
        EnumSetting.Builder<RandomDelayMode>()
            .name("random-delay").description("Random delay distribution added to break-delay.")
            .defaultValue(RandomDelayMode.None).build()
    )

    private val breaksPerTick = sgBreak.add(
        IntSetting.Builder()
            .name("breaks-per-tick").description("Max instant-breaks per acting tick.")
            .defaultValue(20).range(1, 1024).sliderRange(1, 30).build()
    )

    /** 破坏的多级几何排序：同等 posPriority 下先挖哪些坐标。 */
    private val breakOrder = LayeredSort(sgBreak, "break-order", GeometricSort.Nearest)

    private val timeoutEnabled = sgBreak.add(
        BoolSetting.Builder()
            .name("timeout-protection").description("Skip a position taking too long, so the worklist can't stall.")
            .defaultValue(true).build()
    )

    private val timeoutSeconds = sgBreak.add(
        DoubleSetting.Builder()
            .name("timeout-seconds").description("Max seconds spent on one position before skipping.")
            .defaultValue(9.0).min(0.5).max(1024.0).sliderRange(0.5, 15.0)
            .visible { timeoutEnabled.get() }.build()
    )

    private val reboundSeconds = sgBreak.add(
        DoubleSetting.Builder()
            .name("rebound-seconds").description("How long a finished position is watched for rebound before settling.")
            .defaultValue(1.7).min(0.06).max(15.0).sliderRange(0.5, 15.0).build()
    )

    private val shapeMode = sgRender.add(
        EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How state boxes are rendered.")
            .defaultValue(ShapeMode.Lines).build()
    )

    /** 每个有颜色的状态各配一个渲染开关。 */
    private val renderStates = ExecState.entries.filter { it.color != null }.associateWith { state ->
        sgRender.add(
            BoolSetting.Builder()
                .name("render-${state.name.toKebab()}")
                .description("Render positions in state ${state.name}.")
                .defaultValue(true).build()
        )
    }

    // ============================== 公共 API ==============================

    /** 常驻模块，禁止被关闭。 */
    override fun toggle() {
        if (!isActive) super.toggle()
    }

    init {
        toggle()
    }

    fun register(source: DemandSource) {
        sources += source
    }

    val isRunning: Boolean
        get() = tracked.values.any { it.state.isActionable }

    override fun getInfoString() = "${tracked.size} positions"

    // ============================== 运行时状态 ==============================

    private val sources = mutableListOf<DemandSource>(CursorProxy)

    /** 坐标 → 跟踪项：唯一权威工作表，承载统一状态机。 */
    private val tracked = HashMap<BlockPos, TrackedBlock>()

    /** 本 tick 每个坐标已被认领的最高 mergePriority（高者独占）。复用以免每 tick 重建。 */
    private val claims = HashMap<BlockPos, Int>()

    private val buildTimer = Cooldown()
    private val breakPacer = BreakPacer()

    /** 本 tick 的统一时钟：所有状态机时间戳共用同一基准，顺带省去重复的 syscall。 */
    private var tickStartMs = 0L

    // ============================== Tick 主循环 ==============================

    @EventHandler
    private fun TickEvent.Pre.onTick() {
        val player = mc.player ?: return reset()
        val level = mc.level ?: return reset()

        if (sources.none { it.active }) {
            if (tracked.isNotEmpty()) reset()
            return
        }

        tickStartMs = System.currentTimeMillis()
        collectDemands()
        advance(level)
        if (isEating()) {
            suspendForEating()
            return
        }
        act(player, level)
    }

    /** meteor 的 AutoEat 正在进食：占着右键的这段时间整机挂起，不抢手、不发动作。 */
    private fun isEating(): Boolean = Modules.get().get(MeteorAutoEat::class.java)?.eating == true

    /**
     * 进食期间挂起执行：把在途挖掘(蓝)让出 → 取消(青)，下一 tick 被回收后由 [claim] 重登记为挂起(红)，
     * 不让它卡在进行中(蓝)。本 tick 跳过 [act]，[BreakSettings] 见引擎停手会自动 stopDestroyBlock 收尾。
     */
    private fun suspendForEating() {
        for (t in tracked.values) {
            if (t.state == ExecState.Active && t.hasActed) t.yieldMining()
        }
    }

    override fun onDeactivate() = reset()

    private fun reset() {
        tracked.clear()
        claims.clear()
    }

    // -------------------- 1. 收集诉求 --------------------

    private fun collectDemands() {
        claims.clear()
        for (source in sources) {
            if (!source.active) continue
            val merge = source.mergePriority
            source.collect { pos, target, posPriority -> claim(pos, target, merge, posPriority) }
        }
        tracked.values.forEach { it.wanted = it.pos in claims }
    }

    /** 把单条诉求并入工作表：同坐标 mergePriority 高者独占，平手沿用先到者。 */
    private fun claim(pos: BlockPos, target: BlockState?, mergePriority: Int, posPriority: Int) {
        val key = pos.immutable()
        val owner = claims[key]
        if (owner != null && owner >= mergePriority) return
        claims[key] = mergePriority

        tracked[key]?.let {
            it.target = target
            it.posPriority = posPriority
            return
        }

        val world = mc.level?.getBlockState(key) ?: return
        if (requiredAction(target, world, key) == ActionKind.Settled) return // 一来就达成，无需跟踪
        tracked[key] = TrackedBlock(key, target, posPriority, world.block)
    }

    /** 对照「诉求 vs 世界现状」，得出某坐标此刻需要的动作类别。 */
    private fun requiredAction(target: BlockState?, world: BlockState, pos: BlockPos): ActionKind = when (target) {
        null -> when {                                       // 破坏诉求
            world.isAirOrFluid -> ActionKind.Settled
            pos.canBreakIt(world) -> ActionKind.Break
            else -> ActionKind.Blocked
        }

        else -> when {                                       // 放置 / 交互诉求
            Projection.matches(world, target) ->
                if (world.needInteractionCountsTo(target) > 0) ActionKind.Interact else ActionKind.Settled

            world.canBeReplaced() -> ActionKind.Place        // 空气 / 流体 / 可替换 → 直接放
            else -> ActionKind.Blocked                       // 占位的错误方块：是否清障由来源决定
        }
    }

    // -------------------- 2. 推进状态机 --------------------

    private fun advance(level: Level) {
        val iter = tracked.values.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            if (t.state.isTerminal) {
                iter.remove(); continue
            }
            val world = level.getBlockState(t.pos)
            t.kind = requiredAction(t.target, world, t.pos)
            t.recompute(world, reached = t.kind == ActionKind.Settled)
        }
    }

    // -------------------- 3. 执行动作 --------------------

    private fun act(player: LocalPlayer, level: Level) {
        val canInstaBreak = instaBreakTest(player, level)
        val batch = Batch()
        for (t in tracked.values) {
            if (!t.state.isActionable) continue
            when (t.kind) {
                ActionKind.Break -> (if (canInstaBreak(t.pos)) batch.instaBreaks else batch.hardBreaks) += t
                ActionKind.Interact -> if (interactEnabled.get()) batch.interacts += t
                ActionKind.Place -> batch.places += t
                ActionKind.Settled, ActionKind.Blocked -> Unit
            }
        }

        // 一个 tick 只做一类动作：破坏 ＞ 交互 ＞ 放置。排序器仅在真要建造时才构建。
        when {
            batch.hasBreaks -> breakPacer.tick(player, batch.instaBreaks, batch.hardBreaks)
            batch.interacts.isNotEmpty() -> interact(level, player, batch.interacts.sortedWith(actionOrder(player)))
            batch.places.isNotEmpty() -> place(batch.places.sortedWith(actionOrder(player)))
        }
    }

    /** 放置 / 交互的执行顺序：posPriority 优先，其次按多级几何排序。 */
    private fun actionOrder(player: LocalPlayer): Comparator<TrackedBlock> =
        byPriorityThen(placeOrder.comparator(player.blockPosition()))

    /** posPriority 高者先做，并列时交给给定的几何比较器。破坏与放置 / 交互共用。 */
    private fun byPriorityThen(geometry: Comparator<BlockPos>): Comparator<TrackedBlock> =
        compareByDescending<TrackedBlock> { it.posPriority }
            .thenComparator { a, b -> geometry.compare(a.pos, b.pos) }

    private fun interact(level: Level, player: LocalPlayer, targets: List<TrackedBlock>) {
        if (!buildTimer.ready(buildDelay.get())) return
        if (!ensureSafeInteractHand(player)) return
        var budget = buildPerTick.get()
        for (t in targets) {
            if (budget <= 0) break
            val state = t.target ?: continue
            val want = level.getBlockState(t.pos).needInteractionCountsTo(state)
            val done = t.pos.TryInteractIt(min(want, budget))
            if (done > 0) {
                t.markActed(); buildTimer.reset(); budget -= done
            }
        }
    }

    private fun place(targets: List<TrackedBlock>) {
        if (!buildTimer.ready(buildDelay.get())) return
        var budget = buildPerTick.get()
        for (t in targets) {
            if (budget <= 0) break
            val state = t.target ?: continue
            if (state.TryPlaceIt(t.pos)) {
                t.markActed(); buildTimer.reset(); budget--
            }
        }
    }

    /** 交互前若主手物会被误触发（吃掉 / 放烟花），切到一个安全槽位。 */
    private fun ensureSafeInteractHand(player: LocalPlayer): Boolean {
        if (!player.mainHandItem.triggersOnUse) return true
        val safeSlot = (0..8).firstOrNull { player.inventory.getItem(it).isSafeToHold } ?: return true
        return player.switchTo(safeSlot)
    }

    private val ItemStack.isSafeToHold get() = !isEmpty && !triggersOnUse
    private val ItemStack.triggersOnUse
        get() = get(DataComponents.FOOD) != null || item is FireworkRocketItem

    /**
     * 当前能否对某坐标秒破——返回闭包，便于一次扫描里对多个坐标复用同一前提。
     * 开了 AutoTool 时按其可切换槽位预判工具；否则退回 [BlockUtils.canInstaBreak]。
     */
    private fun instaBreakTest(player: LocalPlayer, level: Level): (BlockPos) -> Boolean {
        val slots = when {
            Modules.get().isActive(AutoTool::class.java) -> 0..35
            Modules.get().isActive(MeteorAutoTool::class.java) -> 0..8
            else -> return { BlockUtils.canInstaBreak(it) }
        }
        if (player.isCreative) return { true }
        return { pos ->
            val state = level.getBlockState(pos)
            slots.any {
                BlockUtils.getBreakDelta(it, state) >= 1 && !AutoTool.shouldStopUsing(player.inventory.getItem(it))
            }
        }
    }

    // ============================== 渲染 ==============================

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        val mode = shapeMode.get()
        for (t in tracked.values) {
            val color = t.state.color ?: continue
            if (renderStates[t.state]?.get() != true) continue
            t.pos.Render(event, color, mode)
        }
    }

    // ============================== 支撑类型 ==============================

    /** 时刻换算：均以本 tick 起点为基准，仅作用于已确定发生（非空）的时刻。 */
    private val Long.elapsedSec get() = (tickStartMs - this) / 1000.0
    private val Long.withinRebound get() = elapsedSec < reboundSeconds.get()
    private val Long.timedOut get() = timeoutEnabled.get() && elapsedSec > timeoutSeconds.get()

    /** [act] 一趟扫描的产物，按动作类别分桶。 */
    private class Batch {
        val instaBreaks = ArrayList<TrackedBlock>()
        val hardBreaks = ArrayList<TrackedBlock>()
        val interacts = ArrayList<TrackedBlock>()
        val places = ArrayList<TrackedBlock>()
        val hasBreaks get() = instaBreaks.isNotEmpty() || hardBreaks.isNotEmpty()
    }

    /**
     * 单坐标的执行跟踪与统一状态机：破坏 / 放置 / 交互共用同一生命周期 [ExecState]，
     * 状态完全由两个事件时刻 ＋ [wanted] ＋ 世界现状推导：
     *  - [actedAt] ：第一次对它发出动作的时刻（null＝从未动手）。
     *  - [reachedAt]：世界第一次达成目标的时刻（null＝从未达成）。
     */
    private class TrackedBlock(
        val pos: BlockPos,
        var target: BlockState?,
        var posPriority: Int,
        private val originBlock: Block,
    ) {
        var wanted = true
        var kind = ActionKind.Blocked

        var state = ExecState.Pending
            private set

        private var actedAt: Long? = null
        private var reachedAt: Long? = null

        val hasActed get() = actedAt != null

        fun markActed() {
            actedAt = actedAt ?: tickStartMs
        }

        /**
         * 让出独占挖掘权（posPriority 变化致改挖他块）：直接落入取消(青)终态，下一 tick 被回收。
         * 因坐标仍在需求表里，回收后会被 [claim] 当作新条目重登记为挂起(红)——
         * 即「曾动手」不再让它滞留为进行中(蓝)。
         */
        fun yieldMining() {
            state = ExecState.Cancelled
        }

        /** 依「诉求 vs 世界」推进状态机；[reached] 为本 tick 世界是否已达成目标。 */
        fun recompute(world: BlockState, reached: Boolean) {
            if (reached) reachedAt = reachedAt ?: tickStartMs
            state = nextState(world, reached)
        }

        private fun nextState(world: BlockState, reached: Boolean): ExecState {
            val actedAt = actedAt                               // 收窄到局部，下方得以智能转换为非空
            val reachedAt = reachedAt

            if (!wanted) when {                                 // 不再被需要
                reachedAt == null        -> return ExecState.Cancelled    // 没达成过：取消
                !reachedAt.withinRebound -> return ExecState.DoneSettled  // 回弹窗已过：落定
            }                                                   // 仍在回弹窗内 → 沿用下方常规推导
            return when {
                actedAt == null   -> if (reachedAt == null) ExecState.Pending else ExecState.DoneSettled
                reachedAt == null -> if (actedAt.timedOut) ExecState.Timeout else ExecState.Active
                reached           -> if (reachedAt.withinRebound) ExecState.DoneWaitingRebound else ExecState.DoneSettled
                world.block !== originBlock -> ExecState.DoneSettled    // 到位后又变成别的方块，视为落定
                reachedAt.withinRebound -> ExecState.DoneWaitingRebound
                else              -> ExecState.Rebounded               // 观察窗内变回原样
            }
        }
    }

    /** 升序节流：连续 [ready] 累加，达到 interval 即就绪（放置 / 交互批次之间的间隔）。 */
    private class Cooldown {
        private var ticks = 0

        fun ready(interval: Int): Boolean {
            if (ticks >= interval) return true
            ticks++
            return false
        }

        fun reset() {
            ticks = 0
        }
    }

    /** 递减节流：[arm] 置数后逐次倒数，[due] 在归零时放行（true），否则倒数一格拦下（false）。 */
    private class Countdown {
        private var left = 0
        fun arm(ticks: Int) { left = ticks }
        fun clear() { left = 0 }
        fun due(): Boolean = if (left == 0) true else { left--; false }
    }

    /**
     * 破坏节奏：一个 tick 内最多秒破一批方块，外加最多推进一块「硬」方块。
     * 仅一道节流 [afterInsta]：刚秒破过后隔几 tick 才续推硬方块（break-delay ＋ 随机抖动），
     * 使秒破优先且不被硬砖拖累。
     */
    private class BreakPacer {
        private val afterInsta = Countdown()

        fun tick(player: LocalPlayer, insta: List<TrackedBlock>, hard: List<TrackedBlock>) {

            val playerPos = player.blockPosition()
            val budget = breaksPerTick.get()
            val instaCount = min(insta.size, budget)
            val hardTarget = if (instaCount < budget) pickHard(hard, playerPos) else null

            // posPriority 变化改挖他块：失去独占挖掘权的在途硬砖让出，转入取消(青)→回收→挂起(红)。
            if (hardTarget != null) hard.forEach { if (it !== hardTarget && it.hasActed) it.yieldMining() }
            (mc.gameMode as? MultiPlayerGameModeAccessor)?.let {
                if(it.destroyDelay > 0) return
            }

            insta.sortedWith(byPriorityThen(breakOrder.comparator(playerPos))).take(instaCount).forEach(::mine)

            if (instaCount > 0) afterInsta.arm(breakDelay.get() + randomDelay.get().theDelay)
            if (!afterInsta.due()) return

            hardTarget?.let(::mine)
        }

        private fun pickHard(hard: List<TrackedBlock>, playerPos: BlockPos): TrackedBlock? {
            val geometry = breakOrder.comparator(playerPos)
            return hard.minWithOrNull(
                compareByDescending<TrackedBlock> { it.posPriority }
                    .thenByDescending { it.state == ExecState.Active }
                    .thenComparator { a, b -> geometry.compare(a.pos, b.pos) }
            )
        }

        private fun mine(t: TrackedBlock) {
            t.markActed(); t.pos.BreakIt()
        }
    }
}

/** CamelCase → kebab-case，用于设置名。 */
private fun String.toKebab(): String =
    fold(StringBuilder()) { sb, c ->
        if (c.isUpperCase() && sb.isNotEmpty()) sb.append('-')
        sb.append(c.lowercaseChar())
    }.toString()
