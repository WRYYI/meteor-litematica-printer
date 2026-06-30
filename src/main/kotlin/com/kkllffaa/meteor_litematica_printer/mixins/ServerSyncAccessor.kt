package com.kkllffaa.meteor_litematica_printer.mixins

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Input
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/**
 * 暴露 [LocalPlayer] 记账「上次告诉服务器的状态」的两个私有字段。
 *
 * 客户端只在这两个字段与真实状态不符时才补发包：[lastSentInput] 不等于当前按键即重发输入包，
 * [wasSprinting] 不等于当前疾跑态即重发疾跑命令。改写它们，就能借原版自身的变更检测在下一 tick
 * 自动把真实状态补回去——无需自己另存一份服务器视图。详见 [com.kkllffaa.meteor_litematica_printer.Modules.Tools.SlotClickGuard]。
 */
@Mixin(LocalPlayer::class)
interface ServerSyncAccessor {

    @get:Accessor("lastSentInput")
    @set:Accessor("lastSentInput")
    var lastSentInput: Input

    @get:Accessor("wasSprinting")
    @set:Accessor("wasSprinting")
    var wasSprinting: Boolean
}
