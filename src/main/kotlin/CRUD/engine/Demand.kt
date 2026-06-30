package com.kkllffaa.meteor_litematica_printer.crud.engine

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/**
 * 主观需求层与执行层之间唯一的契约。这个坐标此刻应该长什么样
 */
interface DemandSource {
    /** 仅在激活时被 [Executor] 轮询。 */
    val active: Boolean

    /**
     * 合并优先级：同一坐标被多条诉求争夺时，高者独占、低者被丢弃。
     */
    val mergePriority: Int

    /** 把本来源本 tick 的诉求灌进 [sink]。 */
    fun collect(sink: DemandSink)
}

/** [DemandSource.collect] 的回收口；由 [Executor] 提供并完成按 [DemandSource.mergePriority] 的合并去重。 */
fun interface DemandSink {

    /**
     * 目标用一个 [BlockState]? 表达：
     *  - 非 null：放置 / 交互，让该坐标变成这个状态（含“调状态”如音符盒、中继器）。
     *  - null  ：破坏
     *
     * [posPriority] 是合并后的执行顺序（高者先做），自带排序的来源或玩家光标代理破坏会抬高它，
     * 好让它插到多级几何排序之前。
     */
    fun want(pos: BlockPos, target: BlockState?, posPriority: Int)
}

/** posPriority 取默认 0 的便捷写法——大多数来源不关心顺序。 */
fun DemandSink.want(pos: BlockPos, target: BlockState?) = want(pos, target, 0)
