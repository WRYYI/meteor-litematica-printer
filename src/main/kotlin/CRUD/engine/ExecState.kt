package com.kkllffaa.meteor_litematica_printer.crud.engine

import com.kkllffaa.meteor_litematica_printer.Functions.ColorScheme

/**
 * 每个坐标的统一执行状态机。无论这次动作是破坏、放置还是交互，生命周期都走同一套：
 * 任意时刻若该坐标不再被任何来源需要（或主观条件失效）→ [Cancelled]。
 * [Cancelled] 与 [DoneSettled] 是终态，会在下一 tick 被回收。
 */
enum class ExecState(val color: ColorScheme?) {
    /** 将要X：已登记、还没动手。 */
    Pending(ColorScheme.红),

    /** X中：动作已发出、世界还没到位。 */
    Active(ColorScheme.蓝),

    /** X超时：Active了太久，放弃。 留作可见标记、不再动手。*/
    Timeout(ColorScheme.黄),

    /** X完成等待回弹：世界已到位，仍在回弹观察窗内。 */
    DoneWaitingRebound(ColorScheme.紫),

    /** X回弹：观察窗内世界又变回原样（被服务器回滚 / 他人干预），留作可见标记、不再动手。 */
    Rebounded(ColorScheme.绿),

    /** X取消：不再被需要或主观条件失效。 */
    Cancelled(ColorScheme.青),

    /** X完成未回弹：到位且过了观察窗（或我们没动手它就已到位）。 */
    DoneSettled(ColorScheme.白);

    val isTerminal: Boolean get() = this == Cancelled || this == DoneSettled

    /**
     * 还需要动手的状态。
     */
    val isActionable: Boolean get() = this == Pending || this == Active
}

/** 对照「需求 vs 世界现状」后，某坐标这一刻需要的动作类别。 */
enum class ActionKind {
    /** 已满足，无需动作。 */
    Settled,

    /** 需要破坏（删除诉求，或建造诉求下被错误方块占住且允许清场）。 */
    Break,

    /** 需要放置。 */
    Place,

    /** 同种方块、仅状态不同，右键调整（音符盒/中继器/门…）。 */
    Interact,

    /** 想动但当前不可动。 */
    Blocked,
}
