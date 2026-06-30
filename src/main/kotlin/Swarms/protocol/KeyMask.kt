package com.kkllffaa.meteor_litematica_printer.swarms.protocol

/**
 * 移动按键位掩码：把“前后左右 / 跳跃 / 潜行 / 疾跑”紧凑成一个 int，在网络上传输。
 *
 * 位定义一旦发布就不要改动（不同版本的节点要能互相解析）。
 */
object KeyMask {
    const val FORWARD = 1
    const val BACK = 2
    const val LEFT = 4
    const val RIGHT = 8
    const val JUMP = 16
    const val SNEAK = 32
    const val SPRINT = 64

    fun has(mask: Int, bit: Int): Boolean = mask and bit != 0
}
