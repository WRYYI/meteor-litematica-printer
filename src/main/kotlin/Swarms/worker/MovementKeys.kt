package com.kkllffaa.meteor_litematica_printer.swarms.worker

import com.kkllffaa.meteor_litematica_printer.swarms.protocol.KeyMask
import net.minecraft.client.KeyMapping
import net.minecraft.client.Options

/**
 * 移动按键 ↔ [KeyMask] 位掩码的唯一绑定（工蜂侧）。
 *
 * 潜在乘客读自己的键编码成掩码发出（[maskOf]）；基座收到后把掩码写回被托管的键（[apply]）。
 * 编码端与解码端共用同一份顺序，新增 / 调整按键只改这一处，绝不会两边错位。
 *
 * 读的是「逻辑按下」[KeyMapping.isDown] 而非物理按键：打字 / 开 GUI 时它为 false，
 * 因而广播出去的是「真想移动」的意图，不会被界面操作误导。
 */
internal object MovementKeys {

    /** 绑定表：游戏内按键访问器 + 对应位。改这一处即可。 */
    private val bindings: List<Pair<(Options) -> KeyMapping, Int>> = listOf(
        Options::keyUp to KeyMask.FORWARD,
        Options::keyDown to KeyMask.BACK,
        Options::keyLeft to KeyMask.LEFT,
        Options::keyRight to KeyMask.RIGHT,
        Options::keyJump to KeyMask.JUMP,
        Options::keyShift to KeyMask.SNEAK,
        Options::keySprint to KeyMask.SPRINT,
    )

    /** 把当前「逻辑按下」的移动键编码成位掩码。 */
    fun maskOf(options: Options): Int {
        var mask = 0
        for ((key, bit) in bindings) {
            if (key(options).isDown) mask = mask or bit
        }
        return mask
    }

    /** 把位掩码写回被托管的移动键（基座跟随时用）。 */
    fun apply(options: Options, mask: Int) {
        for ((key, bit) in bindings) {
            key(options).isDown = KeyMask.has(mask, bit)
        }
    }
}
