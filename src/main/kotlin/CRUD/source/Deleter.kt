package com.kkllffaa.meteor_litematica_printer.crud.source

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.*
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSink
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSource
import com.kkllffaa.meteor_litematica_printer.crud.engine.want
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.systems.modules.world.EChestFarmer
import meteordevelopment.meteorclient.systems.modules.world.HighwayBuilder
import meteordevelopment.meteorclient.systems.modules.world.Nuker
import meteordevelopment.meteorclient.systems.modules.world.VeinMiner
import meteordevelopment.orbit.EventHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Blocks
import kotlin.math.*

/**
 * 主观需求来源：把"哪些坐标应该消失"翻译成破坏诉求（[DemandSink.want] 的 target = null）。
 *
 * 这里只有**主观判断**——黑白名单、触发方式（手动连片 / 自动半径）、以及一整套保护
 * （流体相邻、相邻方块、站立、高度、宽度、区域、朝向、地面、网格挖掘、打通路径）。
 * 全部保护**每 tick 重判**：某坐标一旦不再满足，就不再喊它，[Executor] 随即取消该坐标的动作。
 *
 * 破坏的时序、节奏、换工具、回弹/超时状态全部交给 [Executor]，本类一概不碰。
 */
open class Deleter(name: String) : Module(Addon.CRUD, name, "Demands matching blocks be removed."), DemandSource {

    companion object {
        /** 与本模块抢"破坏权"、激活时自动关掉的 Meteor 挖掘类模块。 */
        private val conflictingModules = arrayOf(
            EChestFarmer::class.java, HighwayBuilder::class.java, Nuker::class.java, VeinMiner::class.java,
        )
        private const val INTENT_CAP = 8192
    }

    // region 砖块偏移常量
    private val faceNeighbours = arrayOf(
        Vec3i(0, 1, 0), Vec3i(0, -1, 0),
        Vec3i(0, 0, 1), Vec3i(0, 0, -1), Vec3i(1, 0, 0), Vec3i(-1, 0, 0),
    )
    private val blockNeighbours = buildList {
        for (y in -1..1) for (x in -1..1) for (z in -1..1) if (x != 0 || y != 0 || z != 0) add(Vec3i(x, y, z))
    }.toTypedArray()
    // endregion

    // region Settings — General
    private val sgGeneral = settings.defaultGroup
    private val sgProtection = settings.createGroup("Mining Protection")

    private val prioritySetting = sgGeneral.add(
        IntSetting.Builder()
            .name("priority").description("Higher wins when sources disagree on a position.")
            .defaultValue(10).sliderRange(-10, 20).build()
    )

    private val blockListMode = sgGeneral.add(
        EnumSetting.Builder<ListMode>()
            .name("block-list-mode").description("Selection mode.")
            .defaultValue(ListMode.Whitelist).build()
    )

    private val whiteListBlocks = sgGeneral.add(
        BlockListSetting.Builder()
            .name("white-blocks").description("Which blocks to mine.")
            .defaultValue(Blocks.NETHERRACK)
            .visible { blockListMode.get() == ListMode.Whitelist }.build()
    )

    private val blackListBlocks = sgGeneral.add(
        BlockListSetting.Builder()
            .name("black-blocks").description("Which blocks to ignore.")
            .defaultValue(Blocks.BEDROCK)
            .visible { blockListMode.get() == ListMode.Blacklist }.build()
    )

    private val triggerMode = sgGeneral.add(
        EnumSetting.Builder<触发模式>()
            .name("trigger-mode").description("How positions are picked.")
            .defaultValue(触发模式.手动相连同类)
            .onChanged { intent.clear() }.build()
    )

    private val depth = sgGeneral.add(
        IntSetting.Builder()
            .name("depth").description("Flood-fill depth for connected same-block mining.")
            .defaultValue(3).min(1).sliderRange(1, 15)
            .visible { triggerMode.get() == 触发模式.手动相连同类 }.build()
    )

    private val meshMine = sgGeneral.add(
        BoolSetting.Builder()
            .name("mesh-mining").description("Only mine blocks already exposed (faster sweeps).")
            .defaultValue(false)
            .visible { triggerMode.get() == 触发模式.自动半径全部 }.build()
    )

    private val meshMineProbeMode = sgGeneral.add(
        EnumSetting.Builder<网格挖掘模式>()
            .name("mesh-mining-probe").description("Grid strategy: exposure-based (探查点2) or sparse probe lattice (探查点6 / 探查点7).")
            .defaultValue(网格挖掘模式.探查点2)
            .visible { meshMine.生效 }.build()
    )

    private val meshMineMode = sgGeneral.add(
        EnumSetting.Builder<MeshMineMode>()
            .name("mesh-mining-mode").description("What counts as 'exposed'.")
            .defaultValue(MeshMineMode.CacheAndAir)
            .visible { meshMine.生效 && meshMineProbeMode.get() == 网格挖掘模式.探查点2 }.build()
    )

    private val meshBlockingMode = sgGeneral.add(
        EnumSetting.Builder<挡路模式>()
            .name("mesh-blocking-mode").description("Force-mine blocks that block you: wide box (广范围) or narrow 1×2 channel (窄通道).")
            .defaultValue(挡路模式.广范围)
            .visible { meshMine.生效 }.build()
    )

    private val oreChannel = sgGeneral.add(
        BoolSetting.Builder()
            .name("ore-channel").description("Carve a path to ores.")
            .defaultValue(false)
            .visible { triggerMode.get() == 触发模式.自动半径全部 }.build()
    )

    private val oreMode = sgGeneral.add(
        EnumSetting.Builder<OreMode>()
            .name("ore-mode").description("How ores are treated within the channel.")
            .defaultValue(OreMode.强制不挖掘)
            .visible { oreChannel.生效 }.build()
    )

    private val oreBlocksForChannel = sgGeneral.add(
        BlockListSetting.Builder()
            .name("ore-blocks-for-channel").description("The vertical path to these ores skips mesh-mining.")
            .defaultValue(
                Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.ANCIENT_DEBRIS,
            )
            .visible { oreChannel.生效 }.build()
    )
    // endregion

    // region Settings — Protection
    private val standProtectionMode = sgProtection.add(
        EnumSetting.Builder<ProtectMode>()
            .name("stand-protection").description("Reference frame for 'don't mine the block under your feet'.")
            .defaultValue(ProtectMode.ReferencePlayerY).build()
    )

    private val 站立高度 = sgProtection.add(
        IntSetting.Builder()
            .name("stand-world-y").description("World Y you stand on (for ReferenceWorldY).")
            .defaultValue(62).min(-64).max(320)
            .visible { standProtectionMode.get() == ProtectMode.ReferenceWorldY }.build()
    )

    private val heightProtectionMode = sgProtection.add(
        EnumSetting.Builder<ProtectMode>()
            .name("height-protection").description("Reference frame for the mining height band.")
            .defaultValue(ProtectMode.Off).build()
    )

    private val minHeight = sgProtection.add(
        IntSetting.Builder()
            .name("min-height").description("Minimum height (relative to reference).")
            .defaultValue(0).min(-64).max(320).sliderRange(-20, 20)
            .visible { heightProtectionMode.get() != ProtectMode.Off }.build()
    )

    private val maxHeight = sgProtection.add(
        IntSetting.Builder()
            .name("max-height").description("Maximum height (relative to reference).")
            .defaultValue(1).min(-64).max(320).sliderRange(-20, 20)
            .visible { heightProtectionMode.get() != ProtectMode.Off }.build()
    )

    private val widthProtection = sgProtection.add(
        BoolSetting.Builder()
            .name("width-protection").description("Limit mining to a tunnel width relative to facing.")
            .defaultValue(false).build()
    )

    private val widthLeft = sgProtection.add(
        IntSetting.Builder()
            .name("width-left").description("Blocks left of facing (negative = right).")
            .defaultValue(0).min(-10).max(10).sliderRange(-5, 5)
            .visible { widthProtection.get() }.build()
    )

    private val widthRight = sgProtection.add(
        IntSetting.Builder()
            .name("width-right").description("Blocks right of facing (negative = left).")
            .defaultValue(0).min(-10).max(10).sliderRange(-5, 5)
            .visible { widthProtection.get() }.build()
    )

    private val regionProtection = sgProtection.add(
        BoolSetting.Builder()
            .name("region-protection").description("Only mine inside a world-coordinate box.")
            .defaultValue(false).build()
    )

    private val region1X = sgProtection.add(
        IntSetting.Builder().name("region-x1").description("Corner 1 X.")
            .defaultValue(0).min(-30000000).max(30000000).visible { regionProtection.get() }.build()
    )
    private val region1Y = sgProtection.add(
        IntSetting.Builder().name("region-y1").description("Corner 1 Y.")
            .defaultValue(0).min(-64).max(320).visible { regionProtection.get() }.build()
    )
    private val region1Z = sgProtection.add(
        IntSetting.Builder().name("region-z1").description("Corner 1 Z.")
            .defaultValue(0).min(-30000000).max(30000000).visible { regionProtection.get() }.build()
    )
    private val region2X = sgProtection.add(
        IntSetting.Builder().name("region-x2").description("Corner 2 X.")
            .defaultValue(10).min(-30000000).max(30000000).visible { regionProtection.get() }.build()
    )
    private val region2Y = sgProtection.add(
        IntSetting.Builder().name("region-y2").description("Corner 2 Y.")
            .defaultValue(10).min(-64).max(320).visible { regionProtection.get() }.build()
    )
    private val region2Z = sgProtection.add(
        IntSetting.Builder().name("region-z2").description("Corner 2 Z.")
            .defaultValue(10).min(-30000000).max(30000000).visible { regionProtection.get() }.build()
    )

    private val directionalProtection = sgProtection.add(
        BoolSetting.Builder()
            .name("directional-protection").description("Only mine roughly in the direction you face.")
            .defaultValue(false).build()
    )

    private val directionalAngle = sgProtection.add(
        IntSetting.Builder()
            .name("directional-angle").description("Half-angle from yaw, in degrees.")
            .defaultValue(90).min(30).max(180).sliderRange(30, 180)
            .visible { directionalProtection.get() }.build()
    )

    private val groundProtection = sgProtection.add(
        BoolSetting.Builder()
            .name("ground-protection").description("Stop mining while airborne.")
            .defaultValue(false).build()
    )
    // endregion

    // region Runtime
    /** 想删的坐标。手动模式持续累积（带上限淘汰），自动/蓝图模式由扫描重建。 */
    private val intent = LinkedHashSet<BlockPos>()
    private val floodVisited = HashSet<BlockPos>()

    private var lastPlayerPos: BlockPos? = null
    private var scanThrottle = 0

    /** 打通路径预计算：扫描范围内的矿物坐标 + 当时玩家坐标；不适用时为 null。 */
    private var oreChannelCtx: OreChannelContext? = null

    private data class OreChannelContext(val ores: Set<Vec3i>, val playerPos: Vec3i)

    /** 「按可见性生效」：开关隐藏时即使残留 true 也不算启用，让运行前提与 GUI 同一处条件。 */
    private val Setting<Boolean>.生效: Boolean get() = isVisible && get()

    override val active: Boolean get() = isActive
    override val mergePriority: Int get() = prioritySetting.get()
    // endregion

    override fun onActivate() {
        conflictingModules.forEach { Modules.get().get(it)?.disable() }
    }

    override fun onDeactivate() {
        intent.clear()
        floodVisited.clear()
        oreChannelCtx = null
    }

    // region 需求产出
    override fun collect(sink: DemandSink) {
        if (triggerMode.get() != 触发模式.手动相连同类) refreshScan()
        intent.forEach { if (canDemandDelete(it)) sink.want(it, null) }   // target = null → 破坏
    }

    /** 手动模式：点哪儿，从那儿洪水填充同种方块，加入想删集合。 */
    @EventHandler
    private fun onStartBreakingBlock(event: StartBreakingBlockEvent) {
        if (!isActive || triggerMode.get() != 触发模式.手动相连同类) return
        val pos = event.blockPos
        if (!canDemandDelete(pos)) return
        val item = mc.level!!.getBlockState(pos).block.asItem()
        addIntent(pos)
        floodVisited.clear()
        floodFill(item, pos, depth.get())
    }

    private fun floodFill(item: Item, pos: BlockPos, depth: Int) {
        if (depth <= 0 || !floodVisited.add(pos)) return
        for (offset in blockNeighbours) {
            val neighbour = pos.offset(offset)
            if (!canDemandDelete(neighbour)) continue
            if (mc.level!!.getBlockState(neighbour).block.asItem() === item) {
                addIntent(neighbour)
                floodFill(item, neighbour, depth - 1)
            }
        }
    }

    private fun addIntent(pos: BlockPos) {
        if (intent.size >= INTENT_CAP) intent.iterator().run { next(); remove() }
        intent.add(pos.immutable())
    }
    // endregion

    // region 自动 / 蓝图扫描（位移节流）
    private fun refreshScan() {
        val player = mc.player ?: return
        val current = player.blockPosition()
        scanThrottle++
        if (lastPlayerPos == current && scanThrottle <= 10) return
        lastPlayerPos = current.immutable()
        scanThrottle = 0

        val center = player.EyeCenterPos
        val r = PlayerHandDistance
        val minX = (floor(center.x - r) + 0.01).toInt()
        val maxX = (floor(center.x + r) + 0.01).toInt()
        val minY = (floor(center.y - r) + 0.01).toInt()
        val maxY = (floor(center.y + r) + 0.01).toInt()
        val minZ = (floor(center.z - r) + 0.01).toInt()
        val maxZ = (floor(center.z + r) + 0.01).toInt()

        precomputeOreChannel(minX, maxX, minY, maxY, minZ, maxZ)

        intent.clear()
        scanRadius(minX, maxX, minY, maxY, minZ, maxZ)
    }

    private fun scanRadius(minX: Int, maxX: Int, minY: Int, maxY: Int, minZ: Int, maxZ: Int) {
        val cursor = BlockPos.MutableBlockPos()
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            cursor.set(x, y, z)
            if (canDemandDelete(cursor)) addIntent(cursor)
        }
    }

    private fun precomputeOreChannel(minX: Int, maxX: Int, minY: Int, maxY: Int, minZ: Int, maxZ: Int) {
        val world = mc.level
        val player = mc.player
        if (world == null || player == null || !oreChannel.生效) {
            oreChannelCtx = null; return
        }
        val ores = oreBlocksForChannel.get()
        val found = HashSet<Vec3i>()
        if (ores.isNotEmpty()) {
            val cursor = BlockPos.MutableBlockPos()
            for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
                cursor.set(x, y, z)
                if (world.getBlockState(cursor).block in ores) found.add(cursor.immutable())
            }
        }
        oreChannelCtx = OreChannelContext(found, player.blockPosition().immutable())
    }
    // endregion

    // region 保护判定（每 tick 重判）
    /** 该坐标此刻是否应当被喊"删除"——这是 Deleter 的全部主观判断。 */
    private fun canDemandDelete(pos: BlockPos): Boolean {
        val player = mc.player ?: return false
        val world = mc.level ?: return false
        val state = world.getBlockState(pos)
        val block = state.block

        if (!pos.canBreakIt(null)) return false
        if (groundProtection.get() && !player.onGround()) return false
        if (!pos.不保护于在宽度范围内) return false
        if (!pos.不保护于玩家高度范围内) return false
        if (!pos.不保护于在世界区域范围内) return false
        if (!pos.不保护于在Yaw内) return false

        val 在通道 = pos.在打通路径内
        if (!在通道 && !pos.不保护于不在玩家脚下) return false
        if (!在通道 && pos.保护于网格挖掘 && !pos.会挡路) return false

        if (oreChannel.生效 && block in oreBlocksForChannel.get()) {
            when (oreMode.get()) {
                OreMode.强制挖掘 -> return true
                OreMode.强制不挖掘 -> return false
                OreMode.遵循黑白名单 -> {}
            }
        }

        if (state.isAirOrFluid) return false
        return when (blockListMode.get()) {
            ListMode.Whitelist -> block in whiteListBlocks.get()
            ListMode.Blacklist -> block !in blackListBlocks.get()
            ListMode.None -> true
        }
    }

    private val BlockPos.不保护于不在玩家脚下: Boolean
        get() = when (standProtectionMode.get()) {
            ProtectMode.Off -> true
            ProtectMode.ReferencePlayerY -> y != (mc.player?.blockPosition()?.y ?: return false) - 1
            ProtectMode.ReferenceWorldY -> y != 站立高度.get()
        }

    private val BlockPos.不保护于玩家高度范围内: Boolean
        get() = when (heightProtectionMode.get()) {
            ProtectMode.Off -> true
            ProtectMode.ReferencePlayerY -> (y - (mc.player?.blockPosition()?.y
                ?: return false)) in minHeight.get()..maxHeight.get()

            ProtectMode.ReferenceWorldY -> y in minHeight.get()..maxHeight.get()
        }

    private val BlockPos.不保护于在世界区域范围内: Boolean
        get() {
            if (!regionProtection.get()) return true
            return x in min(region1X.get(), region2X.get())..max(region1X.get(), region2X.get())
                && y in min(region1Y.get(), region2Y.get())..max(region1Y.get(), region2Y.get())
                && z in min(region1Z.get(), region2Z.get())..max(region1Z.get(), region2Z.get())
        }

    private val BlockPos.不保护于在宽度范围内: Boolean
        get() {
            if (!widthProtection.get()) return true
            val player = mc.player ?: return false
            val foot = player.blockPosition()
            val offset = when (player.yRot.AsYawToDirectionNSWE) {
                Direction.SOUTH, Direction.NORTH -> abs(foot.z - z)
                Direction.EAST, Direction.WEST -> abs(foot.x - x)
                else -> Int.MAX_VALUE
            }
            return offset in min(widthLeft.get(), widthRight.get())..max(widthLeft.get(), widthRight.get())
        }

    private val BlockPos.不保护于在Yaw内: Boolean
        get() {
            if (!directionalProtection.get()) return true
            val player = mc.player ?: return false
            val yaw = player.yRot.normalizeAsYaw
            val corners = arrayOf(
                x.toDouble() to z.toDouble(), (x + 1).toDouble() to z.toDouble(),
                x.toDouble() to (z + 1).toDouble(), (x + 1).toDouble() to (z + 1).toDouble(),
            )
            return corners.all { (cx, cz) ->
                val dx = cx - player.x
                val dz = cz - player.z
                if (dx == 0.0 && dz == 0.0) return@all true
                val angle = -Math.toDegrees(atan2(dx, dz))
                val diff = abs(angle - yaw).let { if (it > 180) 360 - it else it }
                diff <= directionalAngle.get()
            }
        }

    /** 网格挖掘保护（true=不喊删，除非它在挡路）。探查点2 看邻面暴露；探查点6/7 看本格是否落在稀疏探查点阵上。 */
    private val BlockPos.保护于网格挖掘: Boolean
        get() {
            if (!meshMine.生效) return false
            return when (meshMineProbeMode.get()) {
                网格挖掘模式.探查点2 -> {
                    val world = mc.level ?: return true
                    faceNeighbours.any {
                        val neighbour = offset(it)
                        val state = world.getBlockState(neighbour)
                        neighbour in intent || when (meshMineMode.get()) {
                            MeshMineMode.Cache -> false
                            MeshMineMode.CacheAndAir -> state.isAir
                            MeshMineMode.CacheAndAirAndFluid -> state.isAirOrFluid
                        }
                    }
                }
                网格挖掘模式.探查点6 -> !IsProb6
                网格挖掘模式.探查点7 -> !IsProb7
            }
        }

    /** 挡路豁免：被网格保护的方块若正挡住玩家前进，仍允许破坏，避免卡住。 */
    private val BlockPos.会挡路: Boolean
        get() = when (meshBlockingMode.get()) {
            挡路模式.广范围 -> 会阻挡玩家移动
            挡路模式.窄通道 -> 会阻挡玩家移动窄通道
        }

    private val BlockPos.在打通路径内: Boolean
        get() {
            if (!oreChannel.生效) return false
            val ctx = oreChannelCtx ?: return false
            return ctx.ores.isNotEmpty() && 在矿物通道范围内(ctx.ores, ctx.playerPos)
        }
    // endregion

    override fun getInfoString(): String = "${triggerMode.get()} ${intent.size}"
}

class DeleterPs1 : Deleter("deleter-ps1")
class DeleterPs2 : Deleter("deleter-ps2")
class DeleterPs3 : Deleter("deleter-ps3")
class DeleterPs4 : Deleter("deleter-ps4")
class DeleterPs5 : Deleter("deleter-ps5")
