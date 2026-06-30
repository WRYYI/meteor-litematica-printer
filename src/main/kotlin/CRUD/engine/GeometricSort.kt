package com.kkllffaa.meteor_litematica_printer.crud.engine

import com.kkllffaa.meteor_litematica_printer.Functions.ManhattanDistanceTo
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.settings.SettingGroup
import net.minecraft.core.BlockPos

/**
 * 一种几何排序：给定参照点（通常是玩家脚下），把坐标排出先后。
 *
 * 每个常量自带一个比较器工厂，没有 when / 一串方法判断——新增一种排序只加一行。
 * [Off] 是“本层不参与”的哨兵：比较器恒等，既可叠进多级链里不产生影响，也用作截断标记。
 *
 * 轴向约定（Minecraft）：东 = +X，西 = -X，南 = +Z，北 = -Z。
 */
enum class GeometricSort(private val comparatorOf: (origin: BlockPos) -> Comparator<BlockPos>) {
    Off({ Comparator { _, _ -> 0 } }),

    Nearest({ origin -> compareBy<BlockPos> { it ManhattanDistanceTo origin } }),
    Furthest({ origin -> compareByDescending<BlockPos> { it ManhattanDistanceTo origin } }),

    TopDown({ compareByDescending<BlockPos> { it.y } }),
    BottomUp({ compareBy<BlockPos> { it.y } }),

    WestToEast({ compareBy<BlockPos> { it.x } }),
    EastToWest({ compareByDescending<BlockPos> { it.x } }),
    NorthToSouth({ compareBy<BlockPos> { it.z } }),
    SouthToNorth({ compareByDescending<BlockPos> { it.z } });

    fun comparator(origin: BlockPos): Comparator<BlockPos> = comparatorOf(origin)
}

/** 把多级几何排序折叠成单个比较器：逐层 thenComparing，前层并列才看后层；空链则不排序。 */
fun List<GeometricSort>.toComparator(origin: BlockPos): Comparator<BlockPos> =
    map { it.comparator(origin) }
        .reduceOrNull { acc, next -> acc.thenComparing(next) }
        ?: Comparator { _, _ -> 0 }

/**
 * 可配置的多级几何排序。第 1 层固定存在（至少一层），其后每层在上一层选定后才显现，
 * 选回 [GeometricSort.Off] 即截断。对外只暴露 [comparator]，内部那排 EnumSetting 不外泄。
 *
 * 截断处自我清场：某层选回 [GeometricSort.Off] 时，其后各层一并归位 Off——既不在配置里
 * 留下残值，也保证日后前层改回非 Off、被隐藏的层重新显现时，呈现的是干净的 Off 而非旧选择。
 */
class LayeredSort(
    group: SettingGroup,
    namePrefix: String,
    primary: GeometricSort,
    levelCount: Int = DEFAULT_LEVELS,
) {
    private val levels: List<Setting<GeometricSort>> = List(levelCount) { i ->
        group.add(
            EnumSetting.Builder<GeometricSort>()
                .name("$namePrefix-${i + 1}")
                .description(
                    if (i == 0) "Primary geometric sort."
                    else "Tie-break #${i + 1}: applied only where all earlier levels rank equal."
                )
                .defaultValue(if (i == 0) primary else GeometricSort.Off)
                // 任一前置层为 Off 即截断本层：将其隐藏。
                .visible { (0 until i).all { j -> levels[j].get() != GeometricSort.Off } }
                // 本层选回 Off 即抹平其后所有层，免得隐藏的残值在前层改回后重新冒出来。
                .onChanged { if (it == GeometricSort.Off) collapseAfter(i) }
                .build()
        )
    }

    /**
     * 第 [index] 层之后各层一律归位 [GeometricSort.Off]。set 会回调 onChanged 自我级联，
     * 故每次都现取 [Setting.get] 实时判断：已是 Off 的跳过，不重复 set，也就不多触发一轮 onChanged。
     */
    private fun collapseAfter(index: Int) {
        (index + 1 until levels.size).forEach { i ->
            if (levels[i].get() != GeometricSort.Off) levels[i].set(GeometricSort.Off)
        }
    }

    /** 各层折叠成一个比较器；遇到首个 [GeometricSort.Off] 即截断，其后（被隐藏的）层不参与。 */
    fun comparator(origin: BlockPos): Comparator<BlockPos> =
        levels.asSequence()
            .map { it.get() }
            .takeWhile { it != GeometricSort.Off }
            .toList()
            .toComparator(origin)

    companion object {
        /** 默认层数。编译期常量，按需调高即可让每个多级排序支持更多层。 */
        const val DEFAULT_LEVELS = 4
    }
}
