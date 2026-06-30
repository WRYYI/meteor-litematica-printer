package com.kkllffaa.meteor_litematica_printer.crud.source

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSink
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSource
import com.kkllffaa.meteor_litematica_printer.crud.engine.Projection
import com.kkllffaa.meteor_litematica_printer.crud.engine.want
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.world.SchematicWorldHandler
import meteordevelopment.meteorclient.settings.BlockListSetting
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.systems.modules.Module
import net.minecraft.core.BlockPos

/**
 * 投影构建：把当前打开的 Litematica 投影“应有的样子”翻译成执行层需求。
 *
 * 本类只产出诉求——某坐标应放什么、哪处多余该清——换物品、时序、先挖后放一律交给 [Executor]。
 * “符合投影”的判定见 [Projection]；如何处理不符合投影的方块由 [工作模式] 决定。
 */
object ProjectionBuilder : Module(Addon.CRUD, "投影构建", "Build the open Litematica schematic."), DemandSource {

    /**
     * 工作模式：对“不符合投影的方块”采取的策略。
     *
     * @param 清障   占位的错误方块是否允许先挖后放。
     * @param 清理空气位 投影为空气之处多出来的方块是否挖除。
     */
    enum class 工作模式(val 清障: Boolean, val 清理空气位: Boolean) {
        /** 全部放置：挖掘全部不符合投影的砖（含投影中的空气部分）。 */
        全部放置(清障 = true, 清理空气位 = true),

        /** 仅在空气放置：等价于禁用挖掘，只往空气/可替换处放。 */
        仅在空气放置(清障 = false, 清理空气位 = false),

        /** 仅放置非空气方块：挖掘不符合投影的砖，但不动投影中的空气部分。 */
        仅放置非空气方块(清障 = true, 清理空气位 = false),
    }

    private val sgGeneral = settings.defaultGroup
    private val sgWhitelist = settings.createGroup("Whitelist")

    private val workMode = sgGeneral.add(
        EnumSetting.Builder<工作模式>()
            .name("work-mode").description("How blocks that disagree with the projection are handled.")
            .defaultValue(工作模式.仅放置非空气方块).build()
    )

    private val rangeSetting = sgGeneral.add(
        IntSetting.Builder()
            .name("range").description("Half-extent of the build scan cube around you.")
            .defaultValue(6).range(1, 64).sliderRange(1, 16).build()
    )

    private val prioritySetting = sgGeneral.add(
        IntSetting.Builder()
            .name("priority").description("Higher wins when sources disagree on a position.")
            .defaultValue(0).sliderRange(-10, 10).build()
    )

    private val whitelistEnabled = sgWhitelist.add(
        BoolSetting.Builder()
            .name("whitelist-enabled").description("Only build the selected blocks.")
            .defaultValue(false).build()
    )

    private val whitelist = sgWhitelist.add(
        BlockListSetting.Builder()
        .name("whitelist").description("Blocks to build.")
        .visible { whitelistEnabled.get() }.build()
    )

    override val active: Boolean get() = isActive
    override val mergePriority: Int get() = prioritySetting.get()

    override fun collect(sink: DemandSink) {
        val schematic = SchematicWorldHandler.getSchematicWorld() ?: return
        val world = mc.level ?: return
        val player = mc.player ?: return
        val layer = DataManager.getRenderLayerRange()

        val mode = workMode.get()
        // 清理空气位会越过投影范围铲平四周，故仅在投影足迹内进行。
        val footprint = if (mode.清理空气位) schematicFootprint() else null
        val onlyBlocks = if (whitelistEnabled.get()) whitelist.get() else null

        val center = player.blockPosition()
        val r = rangeSetting.get()
        val cursor = BlockPos.MutableBlockPos()

        for (dx in -r..r) for (dy in -r..r) for (dz in -r..r) {
            cursor.set(center.x + dx, center.y + dy, center.z + dz)
            if (!layer.isPositionWithinRange(cursor)) continue

            val current = world.getBlockState(cursor)
            val want = schematic.getBlockState(cursor)
            if (Projection.matches(current, want)) continue   // 已符合投影

            when {
                !want.isAir -> {
                    if (onlyBlocks != null && want.block !in onlyBlocks) continue
                    val key = cursor.immutable()
                    // 占位错误方块（不可替换且非目标）：允许清障就先发破坏诉求，破开后下个 tick 自然转放置。
                    if (mode.清障 && !current.canBeReplaced()) sink.want(key, null)
                    else sink.want(key, want)
                }

                footprint?.contains(cursor) == true ->
                    sink.want(cursor.immutable(), null)         // 投影空气位的多余方块 → 破坏
            }
        }
    }

    /** 选中投影在世界坐标下的包围盒；缺省时返回 null（不清理空气位）。 */
    private fun schematicFootprint(): Box? {
        val placement = DataManager.getSchematicPlacementManager().selectedSchematicPlacement ?: return null
        val origin = placement.origin ?: return null
        val size = placement.schematic?.metadata?.enclosingSize ?: return null
        return Box(
            origin.x, origin.y, origin.z,
            origin.x + size.x - 1, origin.y + size.y - 1, origin.z + size.z - 1
        )
    }

    private class Box(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
    ) {
        operator fun contains(p: BlockPos) =
            p.x in minX..maxX && p.y in minY..maxY && p.z in minZ..maxZ
    }
}
