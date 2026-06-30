package com.kkllffaa.meteor_litematica_printer.swarms.worker

import com.kkllffaa.meteor_litematica_printer.swarms.net.SwarmClient
import com.kkllffaa.meteor_litematica_printer.swarms.protocol.RemoteState
import com.kkllffaa.meteor_litematica_printer.swarms.protocol.SwarmAddress
import com.kkllffaa.meteor_litematica_printer.swarms.protocol.SwarmProtocol
import meteordevelopment.meteorclient.MeteorClient.mc
import java.util.concurrent.ConcurrentHashMap

/**
 * 工蜂服务（单例，仅客户端）——把传输层 [SwarmClient] 与本节点的协作收发态黏合起来。
 *
 * 每个进程（含主脑机器）都用它连入主脑参与协作；在主脑机器上同时启用本服务，就实现了
 * “主脑客户端也可以是工蜂”。
 *
 * 角色相关的接收态都集中在这里：作为基座时收主脑分配 [assignedTarget] 与呼唤信号；
 * 作为乘客时收针对自己的 tp 请求。
 *
 * 线程模型：
 *  - 网络回调 [onMessage] 跑在网络线程，只写并发集合 / volatile，绝不碰 mc.*。
 *  - 解析名字、读取玩家实体等只在游戏 tick（主线程）里做。
 */
object WorkerService {

    /** 蜂群里其它节点的最近状态（基座据此驮人）。 */
    val remoteStates = RemoteStateTable()

    @Volatile
    private var client: SwarmClient? = null

    @Volatile
    var running = false
        private set

    /** 本节点名字（缓存，供网络线程发 HELLO 用，避免在网络线程访问 mc）。 */
    @Volatile
    var selfName: String = "node"
        private set

    /** 主脑下发给“本节点作为基座”的目标玩家名；null 表示未被分配。 */
    @Volatile
    var assignedTarget: String? = null
        private set

    /**
     * 本节点当前实际驮的目标（由工蜂模块每 tick 写入，不驮人时为 null）。
     * 网络线程用它过滤呼唤信号：只收下“发给我所驮目标”的呼唤，从而让 [calledTargets] 始终有界。
     */
    @Volatile
    var servedTarget: String? = null

    /** 已收到、且确实发给“我所驮目标”的呼唤信号（被基座消费一次后清除）。 */
    private val calledTargets = ConcurrentHashMap.newKeySet<String>()

    /** 收到针对“我本人”的 tp 请求时记录发起者（基座名），供我自动接受 tp。 */
    @Volatile
    private var incomingTpRequestFrom: String? = null

    // ---- 身份 ----------------------------------------------------------------

    /** 解析本节点名字：优先用户自定义，其次游戏内玩家名，最后退回上次已知值。 */
    fun resolveName(custom: String): String {
        if (custom.isNotBlank()) return custom
        val p = mc.player?.name?.string
        if (!p.isNullOrBlank()) return p
        return selfName
    }

    /** 在主线程更新本节点名字；若变化且已连接则补发一次 HELLO。 */
    fun updateName(name: String) {
        if (name.isNotBlank() && name != selfName) {
            selfName = name
            client?.send(SwarmProtocol.encode(SwarmProtocol.HELLO, name))
        }
    }

    // ---- 生命周期 ------------------------------------------------------------

    fun start(hostAddr: String, initialName: String, log: (String) -> Unit) {
        if (running) return
        selfName = initialName.ifBlank { "node" }
        val (host, port) = SwarmAddress.parse(hostAddr)
        val cli = SwarmClient(host, port, { selfName }, ::onMessage, log)
        cli.start()
        client = cli
        running = true
    }

    fun stop() {
        running = false
        client?.stop()
        client = null
        remoteStates.clear()
        assignedTarget = null
        servedTarget = null
        calledTargets.clear()
        incomingTpRequestFrom = null
    }

    // ---- 对外发送 ------------------------------------------------------------

    fun sendState(name: String, yaw: Float, mask: Int) {
        client?.send(SwarmProtocol.encode(SwarmProtocol.STATE, name, yaw.toString(), mask.toString()))
    }

    /** 发出呼唤信号（caller = 发起呼唤的玩家名，通常是想把基座叫过来的乘客 A）。 */
    fun sendCall(caller: String) {
        client?.send(SwarmProtocol.encode(SwarmProtocol.CALL, caller))
    }

    /** 基座发起 tp 请求信号，服务器会转发给 target 本人以便其自动接受。 */
    fun sendTpRequest(base: String, target: String) {
        client?.send(SwarmProtocol.encode(SwarmProtocol.TPREQ, base, target))
    }

    // ---- 对外消费 ------------------------------------------------------------

    /** 目标 target 是否有未处理的呼唤信号；消费一次。 */
    fun consumeCallFor(target: String): Boolean = calledTargets.remove(target)

    /** 取出并清除“针对我本人的 tp 请求发起者”，无则返回 null。 */
    fun consumeTpRequest(): String? {
        val v = incomingTpRequestFrom
        incomingTpRequestFrom = null
        return v
    }

    // ---- 内部：网络线程回调 --------------------------------------------------

    /** 网络线程回调：仅写并发集合 / volatile。 */
    private fun onMessage(fields: List<String>) {
        when (SwarmProtocol.typeOf(fields)) {
            SwarmProtocol.STATE -> {
                val name = fields.getOrNull(1) ?: return
                if (name == selfName) return // 忽略自己（理论上服务器已排除）
                remoteStates.put(
                    RemoteState(
                        name = name,
                        yaw = fields.getOrNull(2)?.toFloatOrNull() ?: 0f,
                        keyMask = fields.getOrNull(3)?.toIntOrNull() ?: 0
                    )
                )
            }
            SwarmProtocol.BYE -> fields.getOrNull(1)?.let { remoteStates.remove(it) }
            SwarmProtocol.ASSIGN -> assignedTarget = fields.getOrNull(2)?.takeIf { it.isNotBlank() }
            SwarmProtocol.UNASSIGN -> assignedTarget = null
            SwarmProtocol.CALL -> {
                // 呼唤是广播来的；只收下“发给我当前所驮目标”的那一条，避免囤积与己无关的名字。
                val caller = fields.getOrNull(1) ?: return
                if (caller.isNotBlank() && caller == servedTarget) calledTargets.add(caller)
            }
            SwarmProtocol.TPREQ -> incomingTpRequestFrom = fields.getOrNull(1)
        }
    }
}
