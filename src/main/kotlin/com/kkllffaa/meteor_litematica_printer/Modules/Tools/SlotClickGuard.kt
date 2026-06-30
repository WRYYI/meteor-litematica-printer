package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.mixins.ServerSyncAccessor
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.misc.input.Input as PhysicalInput
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input

object SlotClickGuard : Module(
    Addon.TOOLS,
    "slot-click-guard",
    "容器点击前补一帧静止快照（两种包），让服务器认可这次点击。",
) {

    private val movementKeys: List<KeyMapping>
        get() = mc.options.let { listOf(it.keyUp, it.keyDown, it.keyLeft, it.keyRight, it.keyJump) }

    /** 容器点击包出网前调用。 */
    fun freezeBeforeClick() {
        if (!isActive) return
        val player = mc.player ?: return
        val connection = mc.connection ?: return
        val sync = player as ServerSyncAccessor

        // 点击包须由「静止」在前铺垫，而原版的移动/疾跑包要本 tick 稍后才发、赶不及 → 这里先手发一帧快照对齐记账。
        sync.lastSentInput.takeIf { it.isMoving }?.stilled?.let { still ->
            connection.send(ServerboundPlayerInputPacket(still))
            sync.lastSentInput = still
        }
        if (sync.wasSprinting) {
            connection.send(ServerboundPlayerCommandPacket(player, STOP_SPRINTING))
            sync.wasSprinting = false
        }

        // 松开移动键，使原版本 tick 采样到静止——判等天然成立。
        movementKeys.forEach {
            if (it.isDown) {
                it.isDown = false
                HandKeyThisTick = true
            }
        }
    }

    private var HandKeyThisTick = false

    /** tick 末把移动键拨回物理真值；连续换物时下一 tick 又被 [freezeBeforeClick] 松开，空闲 tick 则照常移动。 */
    @EventHandler
    private fun onPostTick(event: TickEvent.Post) {
        if (HandKeyThisTick) {
            HandKeyThisTick = false
            syncPhysicKey()
        }
    }

    override fun onDeactivate() = syncPhysicKey()

    private fun syncPhysicKey() = movementKeys.forEach { it.isDown = PhysicalInput.isPressed(it) }

    private val Input.isMoving: Boolean
        get() = forward || backward || left || right || jump

    /** 同一帧输入，仅松开全部移动键；潜行 [Input.shift] 与疾跑键位 [Input.sprint] 原样保留。 */
    private val Input.stilled: Input
        get() = Input(false, false, false, false, false, shift, sprint)
}
