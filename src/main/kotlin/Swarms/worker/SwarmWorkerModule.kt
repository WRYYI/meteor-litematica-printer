package com.kkllffaa.meteor_litematica_printer.swarms.worker

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.servers.大区
import com.kkllffaa.meteor_litematica_printer.servers.当前大区
import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.DoubleSetting
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.KeybindSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.settings.StringSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.misc.Keybind
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler

/**
 * 蜂群「工蜂」模块（Meteor 接入层，仅客户端）。
 *
 * 连入主脑参与协作；每个进程（含主脑机器）都可启用本模块。本节点可同时是：
 *  - 潜在乘客 A：每 tick 把自己的移动意图（Yaw + 移动按键）广播出去，并可按呼唤键叫基座、
 *    自动接受基座发来的 tp 请求。
 *  - 移动基座：被主脑分配或本地配置后，驮着目标玩家 A 跟随移动（见 [MovingBase]）。
 *
 * 注意：host / node-name 在模块激活时读取一次；改动后请重新开关模块以生效。
 */
object SwarmWorker : Module(Addon.TOOLS, "swarm-worker", "蜂群工蜂：连入主脑，可作移动基座或被驮乘客。") {

    // region ---- 连接 / 身份 ----
    private val sgConn: SettingGroup = settings.createGroup("connection")

    private val hostAddr: Setting<String> = sgConn.add(
        StringSetting.Builder()
            .name("host")
            .description("连接的主脑地址，host:port 格式。主脑机器上填 127.0.0.1:47000 即可连回本机。")
            .defaultValue("127.0.0.1:47000")
            .build()
    )

    private val nodeName: Setting<String> = sgConn.add(
        StringSetting.Builder()
            .name("node-name")
            .description("本节点标识。留空则使用游戏内玩家名。")
            .defaultValue("")
            .build()
    )
    // endregion

    // region ---- 基座（驮人行为）----
    private val sgBase: SettingGroup = settings.createGroup("moving-base")

    private val enableBase: Setting<Boolean> = sgBase.add(
        BoolSetting.Builder()
            .name("enable-base")
            .description("本节点是否担任移动基座（接受自动化操控）。被主脑分配时也会自动生效。")
            .defaultValue(false)
            .build()
    )

    private val target: Setting<String> = sgBase.add(
        StringSetting.Builder()
            .name("target")
            .description("本基座要驮的玩家 A 的名字（本地设置；主脑分配会覆盖它）。")
            .defaultValue("")
            .build()
    )

    private val radius: Setting<Double> = sgBase.add(
        DoubleSetting.Builder()
            .name("radius")
            .description("判定 A 是否在身边的半径（格）。")
            .defaultValue(5.0)
            .range(1.0, 32.0)
            .sliderRange(1.0, 16.0)
            .build()
    )

    private val syncKeys: Setting<Boolean> = sgBase.add(
        BoolSetting.Builder()
            .name("sync-keys")
            .description("同步 A 的前后左右 / 跳跃 / 潜行按键。")
            .defaultValue(true)
            .build()
    )

    private val syncYaw: Setting<Boolean> = sgBase.add(
        BoolSetting.Builder()
            .name("sync-yaw")
            .description("同步 A 的 Yaw 旋转。")
            .defaultValue(true)
            .build()
    )

    private val tpCommand: Setting<String> = sgBase.add(
        StringSetting.Builder()
            .name("tp-command")
            .description("发送 tp 请求时使用的聊天指令，{target} 会被替换成 A 的名字。")
            .defaultValue("/tpa {target}")
            .build()
    )
    // endregion

    // region ---- 乘客（被驮的一方）----
    private val sgRider: SettingGroup = settings.createGroup("rider")

    private val callKey: Setting<Keybind> = sgRider.add(
        KeybindSetting.Builder()
            .name("call-key")
            .description("按下后向蜂群发出呼唤信号，把驮自己的基座叫到身边。")
            .defaultValue(Keybind.none())
            .build()
    )

    private val autoAccept: Setting<Boolean> = sgRider.add(
        BoolSetting.Builder()
            .name("auto-accept-tp")
            .description("当驮自己的基座发起 tp 请求时，自动执行接受指令。")
            .defaultValue(true)
            .build()
    )

    private val acceptCommand: Setting<String> = sgRider.add(
        StringSetting.Builder()
            .name("accept-command")
            .description("自动接受 tp 时执行的聊天指令。{from} 会被替换成发起请求的基座名，用名字接受可避免接错人。")
            .defaultValue("/tpaccept {from}")
            .build()
    )

    private val acceptDelay: Setting<Int> = sgRider.add(
        IntSetting.Builder()
            .name("accept-delay")
            .description("收到 tp 请求后，延迟多久再执行接受指令（毫秒）。给基座完成发包、避免抢答留出时间。")
            .defaultValue(1500)
            .range(0, 10000)
            .sliderRange(0, 5000)
            .build()
    )
    // endregion

    // region ---- 运行时状态 ----

    /** 呼唤键上一 tick 是否按下（用于边沿触发）。 */
    private var callKeyWasPressed = false

    /** 上一 tick 是否在担任基座（用于在卸任那一刻只释放一次按键）。 */
    private var wasActingAsBase = false

    /**
     * 上一次广播出去的移动态；null 表示（重新）激活后还没发过、下一 tick 必发。
     * 用它做去重：朝向 / 按键没变且未到心跳间隔时就不再发包，避免空闲时每秒刷 20 包。
     */
    private var lastSent: SentState? = null

    /** 待执行的 tp 自动接受；null 表示当前没有待处理请求。 */
    private var pendingAccept: PendingAccept? = null
    // endregion

    override fun onActivate() {
        forceResend()
        val initialName = WorkerService.resolveName(nodeName.get())
        WorkerService.start(
            hostAddr = hostAddr.get(),
            initialName = initialName,
            log = { msg -> MeteorClient.LOG.info("[Swarm] {}", msg) }
        )
        info("Swarm worker started (name=$initialName).")
    }

    override fun onDeactivate() {
        MovingBase.release()
        wasActingAsBase = false
        pendingAccept = null
        WorkerService.stop()
        info("Swarm worker stopped.")
    }

    @EventHandler
    private fun onTickPre(event: TickEvent.Pre) {
        if (!WorkerService.running) return
        val player = mc.player ?: return

        // 只在已识别、且非登录大区（大厅）时工作；大厅或大区未知时停手，但保持连接。
        val region = 当前大区
        if (region == null || region == 大区.登录大区) {
            stopWorking()
            return
        }

        val name = WorkerService.resolveName(nodeName.get())
        WorkerService.updateName(name)

        broadcastInputState(name, player.yRot) // 作为潜在乘客 A，把自己的移动意图发出去
        handleCallKey(name)                     // 乘客：呼唤键
        handleTpAccept()                        // 乘客：延迟、按名字自动接受 tp
        tickAsBase(name)                        // 基座：跟随 / 呼唤目标
    }

    /** 暂不工作（仍保持连接）：释放基座按键、清空所驮目标与待接受请求，并复位去重以便复工即重发。 */
    private fun stopWorking() {
        if (wasActingAsBase) {
            wasActingAsBase = false
            MovingBase.release()
        }
        WorkerService.servedTarget = null
        pendingAccept = null
        forceResend()
    }

    /** 把本节点当前朝向与移动意图广播出去，供驮自己的基座读取；仅在变化或到心跳间隔时才发包。 */
    private fun broadcastInputState(name: String, yaw: Float) {
        val mask = localKeyMask
        val now = System.currentTimeMillis()
        val last = lastSent
        if (last != null && yaw == last.yaw && mask == last.mask && now - last.at < STATE_HEARTBEAT_MS) return
        WorkerService.sendState(name, yaw, mask)
        lastSent = SentState(yaw, mask, now)
    }

    /** 呼唤键边沿触发：按下瞬间向蜂群发一次呼唤信号，把驮自己的基座叫到身边。 */
    private fun handleCallKey(name: String) {
        val pressed = callKey.get().isPressed
        if (pressed && !callKeyWasPressed) WorkerService.sendCall(name)
        callKeyWasPressed = pressed
    }

    /**
     * 乘客侧的 tp 自动接受：
     *  - 新到的 tp 请求登记为「待接受」，安排在 [acceptDelay] 毫秒后执行；
     *  - 到点后用发起者（基座）的名字执行接受指令，避免多人请求时接错人。
     */
    private fun handleTpAccept() {
        WorkerService.consumeTpRequest()
            ?.takeIf { autoAccept.get() && it.isNotBlank() }
            ?.let { from -> pendingAccept = PendingAccept(from, System.currentTimeMillis() + acceptDelay.get()) }

        val pending = pendingAccept ?: return
        if (System.currentTimeMillis() < pending.dueAt) return
        pendingAccept = null
        val cmd = acceptCommand.get().replace("{from}", pending.from)
        if (cmd.isNotBlank()) ChatUtils.sendPlayerMsg(cmd)
    }

    /**
     * 基座侧：按「主脑分配优先、其次本地设置」确定要驮的目标并驱动 [MovingBase]；
     * 把当前所驮目标同步给 [WorkerService.servedTarget]（供呼唤信号过滤）；
     * 不再担任基座时释放被托管的按键。
     */
    private fun tickAsBase(name: String) {
        val effectiveTarget = WorkerService.assignedTarget ?: target.get()
        val acting = (enableBase.get() || WorkerService.assignedTarget != null) &&
            effectiveTarget.isNotBlank() && effectiveTarget != name

        WorkerService.servedTarget = if (acting) effectiveTarget else null

        if (acting) {
            wasActingAsBase = true
            MovingBase.tickPre(effectiveTarget, radius.get(), syncKeys.get(), syncYaw.get()) {
                requestTp(name, effectiveTarget)
            }
        } else if (wasActingAsBase) {
            wasActingAsBase = false
            MovingBase.release()
        }
    }

    /** 发一次 tp 请求：用聊天指令把自己传送到目标，同时通过蜂群通知目标自动接受。 */
    private fun requestTp(base: String, target: String) {
        val cmd = tpCommand.get().replace("{target}", target)
        if (cmd.isNotBlank()) ChatUtils.sendPlayerMsg(cmd)
        WorkerService.sendTpRequest(base, target)
    }

    /**
     * 本节点的「移动意图」掩码：读各移动键的逻辑按下状态并编码成位掩码（见 [MovementKeys]）。
     * 逻辑按下在打字 / 开 GUI 时为 false，故广播的是真实移动意图，而非手指恰好压着键。
     */
    private val localKeyMask: Int get() = MovementKeys.maskOf(mc.options)

    /** 清空广播去重缓存，使（重新）激活或复工后下一 tick 立即重发一次当前态。 */
    private fun forceResend() {
        lastSent = null
    }

    /** 一次状态广播的快照：朝向 + 按键掩码 + 发出时刻（毫秒）。 */
    private data class SentState(val yaw: Float, val mask: Int, val at: Long)

    /** 一条排队中的 tp 自动接受：发起者（基座名）+ 到点执行时刻（毫秒）。 */
    private data class PendingAccept(val from: String, val dueAt: Long)

    private const val STATE_HEARTBEAT_MS = 1000L
}
