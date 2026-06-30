package com.kkllffaa.meteor_litematica_printer.crud.atomic

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.*
import com.kkllffaa.meteor_litematica_printer.mixins.MultiPlayerGameModeAccessor
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BlockListSetting
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.systems.modules.player.InstantRebreak
import meteordevelopment.meteorclient.utils.world.BlockUtils
import meteordevelopment.orbit.EventHandler
import meteordevelopment.orbit.EventPriority
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object BreakSettings : Module(Addon.SettingsForCRUD, "Break", "Module to configure AtomicSettings.") {
    override fun toggle() {
        if (isActive) return
        super.toggle()
    }

    init {
        toggle()
    }

    private val sgGeneral = settings.defaultGroup
    private val instantRotation: Setting<ActionMode> = sgGeneral.add(
        EnumSetting.Builder<ActionMode>()
            .name("instant-rotate")
            .description("rotation pre mining.")
            .defaultValue(ActionMode.None)
            .build()
    )

    private val swingMode: Setting<ActionMode> = sgGeneral.add(
        EnumSetting.Builder<ActionMode>()
            .name("swing-hand")
            .description("swing hand post mining.")
            .defaultValue(ActionMode.None)
            .build()
    )

    private val FaceBy: Setting<SafetyFace> = sgGeneral.add(
        EnumSetting.Builder<SafetyFace>()
            .name("mining-face-by")
            .description("")
            .defaultValue(SafetyFace.PlayerPosition)
            .build()
    )

    // region 相邻方块保护（客观约束）
    // 目标某个方向贴着列表里的方块时，禁止破坏——经 BlockPos.canBreakIt 对所有破坏来源统一生效。
    private val sgAdjacent = settings.createGroup("Adjacent Protection")

    private val 相邻保护开关 = sgAdjacent.add(
        BoolSetting.Builder()
            .name("custom-adjacent-protection")
            .description("Never break a block touching a listed block (per direction).")
            .defaultValue(true)
            .build()
    )

    private val 相邻保护上 = sgAdjacent.add(
        BlockListSetting.Builder()
            .name("adjacent-protection-upper")
            .description("Protect when one of these is directly above the target.")
            .defaultValue(
                Blocks.LAVA, Blocks.WATER, Blocks.SAND, Blocks.GRAVEL, Blocks.RED_SAND,
                Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
                Blocks.WHITE_CONCRETE_POWDER, Blocks.ORANGE_CONCRETE_POWDER, Blocks.MAGENTA_CONCRETE_POWDER,
                Blocks.LIGHT_BLUE_CONCRETE_POWDER, Blocks.YELLOW_CONCRETE_POWDER, Blocks.LIME_CONCRETE_POWDER,
                Blocks.PINK_CONCRETE_POWDER, Blocks.GRAY_CONCRETE_POWDER, Blocks.LIGHT_GRAY_CONCRETE_POWDER,
                Blocks.CYAN_CONCRETE_POWDER, Blocks.PURPLE_CONCRETE_POWDER, Blocks.BLUE_CONCRETE_POWDER,
                Blocks.BROWN_CONCRETE_POWDER, Blocks.GREEN_CONCRETE_POWDER, Blocks.RED_CONCRETE_POWDER,
                Blocks.BLACK_CONCRETE_POWDER,
            )
            .visible { 相邻保护开关.get() }
            .build()
    )

    private val 相邻保护侧 = sgAdjacent.add(
        BlockListSetting.Builder()
            .name("adjacent-protection-side")
            .description("Protect when one of these is beside the target.")
            .defaultValue(Blocks.LAVA, Blocks.WATER)
            .visible { 相邻保护开关.get() }
            .build()
    )

    private val 相邻保护下 = sgAdjacent.add(
        BlockListSetting.Builder()
            .name("adjacent-protection-lower")
            .description("Protect when one of these is directly below the target.")
            .defaultValue(Blocks.LAVA, Blocks.WATER)
            .visible { 相邻保护开关.get() }
            .build()
    )

    private val 上方偏移 = arrayOf(Vec3i(0, 1, 0))
    private val 侧向偏移 = arrayOf(Vec3i(0, 0, 1), Vec3i(0, 0, -1), Vec3i(1, 0, 0), Vec3i(-1, 0, 0))
    private val 下方偏移 = arrayOf(Vec3i(0, -1, 0))

    /** 该坐标是否被相邻方块保护（true = 禁止破坏）。客观约束，对所有破坏来源生效。 */
    fun 被相邻方块保护(pos: BlockPos): Boolean {
        if (!相邻保护开关.get()) return false
        val world = mc.level ?: return false
        return world.贴着(pos, 上方偏移, 相邻保护上)
            || world.贴着(pos, 侧向偏移, 相邻保护侧)
            || world.贴着(pos, 下方偏移, 相邻保护下)
    }

    private fun Level.贴着(pos: BlockPos, offsets: Array<Vec3i>, list: Setting<MutableList<Block>>): Boolean {
        val blocks = list.get()
        return offsets.any { getBlockState(pos.offset(it)).block in blocks }
    }
    // endregion

    fun breakBlockWithRotationCfg(blockPos: BlockPos) {
        RotateAndDo(blockPos, instantRotation.get()) { breakBlock(blockPos) }
    }

    fun breakBlock(blockPos: BlockPos) {
        val pos = blockPos.immutable()

        fun meteorAdapt(): Boolean {
            val ir = Modules.get().get<InstantRebreak?>(InstantRebreak::class.java)
            if (ir != null && ir.isActive && ir.blockPos == pos && ir.shouldMine()) {
                ir.sendPacket()
                return true
            }
            return false
        }

        if (meteorAdapt()) return

        mc.gameMode?.run {
            val player = mc.player ?: return
            val direction = when (FaceBy.get()) {
                SafetyFace.PlayerRotation -> BlockUtils.getDirection(pos)
                SafetyFace.PlayerPosition -> pos.PickAFaceFromPlayerPosition(player)
            }
            withEngineTagDestroying {
                continueDestroyBlock(pos, direction)
            }

            player.swing(InteractionHand.MAIN_HAND,swingMode.get())
        }

    }

    @JvmField
    var destroyingFromEngine: Boolean = false

    private inline fun <T> withEngineTagDestroying(block: () -> T): T {

        destroyingFromEngine = true
        try {
            val r = block()
            destroyingInEngine = true
            wasDestroyingInEngine = true
            return r
        } finally {
            destroyingFromEngine = false
        }

    }

    @JvmField
    var destroyingInEngine: Boolean = false
    @JvmField
    var wasDestroyingInEngine: Boolean = false
    @JvmField
    var destroyingInVanilla: Boolean = false

    @EventHandler(priority = EventPriority.HIGHEST + 100)
    private fun onTickPre(event: TickEvent.Pre) {
        destroyingInEngine = false
        destroyingInVanilla = false
    }
    @EventHandler(priority = EventPriority.LOWEST - 100)
    private fun onTickPost(event: TickEvent.Post) {
        if(!destroyingInEngine&& wasDestroyingInEngine) {
            wasDestroyingInEngine = false
            if (!destroyingInVanilla) mc.gameMode?.stopDestroyBlock()
        }
        (mc.gameMode as? MultiPlayerGameModeAccessor)?.let{
            if(it.destroyDelay>0&&!destroyingInEngine&&!destroyingInVanilla ) it.destroyDelay--
        }
    }
}
