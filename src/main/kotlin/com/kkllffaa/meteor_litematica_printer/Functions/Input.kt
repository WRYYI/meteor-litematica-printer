package com.kkllffaa.meteor_litematica_printer.Functions


import meteordevelopment.meteorclient.utils.misc.input.Input
import net.minecraft.client.KeyMapping

fun 恢复按键到物理状态(vararg keys: KeyMapping) {
    keys.forEach { it.isDown = Input.isPressed(it) }
}

fun 松开按键(vararg keys: KeyMapping) {
    keys.forEach { it.isDown = false }
}

fun 按下按键(vararg keys: KeyMapping) {
    keys.forEach { it.isDown = true }
}
