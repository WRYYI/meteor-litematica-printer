package com.kkllffaa.meteor_litematica_printer.swarms.worker

import com.kkllffaa.meteor_litematica_printer.Functions.恢复按键到物理状态
import meteordevelopment.meteorclient.MeteorClient.mc
import net.minecraft.client.player.LocalPlayer

/**
 * 协作功能 1：移动基座（工蜂侧行为）。
 *
 * 本节点被指定为“另一个客户端玩家实体 A 的移动基座”后，每个 client tick（Pre 阶段）调用 [tickPre]：
 *
 *  - 自己 5 格半径内 **没有 A** -> [State.WAITING]：松开被托管的按键；并在“刚进入等待”或“收到呼唤信号”
 *    时发送一次 tp 请求（去 A 身边）。
 *  - 自己 5 格半径内 **有 A** -> [State.MOVING]：把 A 客户端实例上报的“前后左右 / 跳跃 / 潜行”按键
 *    与 Yaw 旋转，同步到自己身上，从而像基座一样跟随 A 移动。
 *  - 任意时刻 **收到呼唤信号** -> 发送一次 tp 请求。
 *
 * 所有对 mc.* 的访问都只在这里（tick 主线程）发生。
 */
object MovingBase {

    enum class State { IDLE, WAITING, MOVING }

    @Volatile
    var state: State = State.IDLE
        private set

    /** 本基座当前是否正在托管（强制写入）移动按键。仅用于让“恢复物理状态”在交还控制权时只发生一次。 */
    private var keysHeld = false

    /**
     * @param target      要驮的玩家 A 的名字
     * @param radius      判定 A 是否在身边的半径（格）
     * @param syncKeys    是否同步移动按键
     * @param syncYaw     是否同步 Yaw 旋转
     * @param onTpRequest 发送一次 tp 请求的动作（由上层提供，内部会发聊天指令 + TPREQ 信号）
     */
    fun tickPre(
        target: String,
        radius: Double,
        syncKeys: Boolean,
        syncYaw: Boolean,
        onTpRequest: () -> Unit
    ): State {
        val player = mc.player ?: return reset()
        val level = mc.level ?: return reset()
        if (target.isBlank()) return reset()

        // 呼唤信号：无条件先消费，收到就要发一次 tp
        val called = WorkerService.consumeCallFor(target)

        // 在自己世界里找名为 target 的玩家实体，并判断是否在半径内
        val a = level.players().firstOrNull { it !== player && it.name.string == target }
        val near = a != null && player.distanceTo(a) <= radius

        if (!near) {
            restoreKeys()
            // “刚进入等待”或“收到呼唤”时发送一次 tp 请求（避免每 tick 刷屏）
            if (state != State.WAITING || called) onTpRequest()
            state = State.WAITING
            return state
        }

        // A 就在身边
        if (called) onTpRequest()

        val rs = WorkerService.remoteStates.get(target)
        if (rs == null) {
            // 人在旁边但还没收到它的输入数据，先别乱动
            restoreKeys()
            state = State.MOVING
            return state
        }

        if (syncYaw) applyYaw(player, rs.yaw)
        if (syncKeys) applyKeys(rs.keyMask) else restoreKeys()
        state = State.MOVING
        return state
    }

    /** 模块停用 / 不再担任基座时调用：把按键恢复到物理状态，状态归零。 */
    fun release() {
        restoreKeys()
        state = State.IDLE
    }

    private fun reset(): State {
        restoreKeys()
        state = State.IDLE
        return state
    }

    private fun applyKeys(mask: Int) {
        MovementKeys.apply(mc.options, mask)
        keysHeld = true
    }

    private fun applyYaw(player: LocalPlayer, yaw: Float) {
        player.yRot = yaw
        player.yRotO = yaw
        player.setYHeadRot(yaw)
    }

    /**
     * 交还移动按键控制权——仅在“上一刻还在托管”时，把按键恢复到玩家真实物理状态一次。
     */
    private fun restoreKeys() {
        if (!keysHeld) return
        val o = mc.options
        恢复按键到物理状态(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint)
        keysHeld = false
    }
}
