package com.kkllffaa.meteor_litematica_printer.swarms.protocol

/**
 * 某个蜂群节点最近一次上报的状态——移动基座驮人时需要的最小信息：朝向 [yaw] + 移动按键 [keyMask]。
 */
data class RemoteState(
    val name: String,
    val yaw: Float,
    val keyMask: Int
)
