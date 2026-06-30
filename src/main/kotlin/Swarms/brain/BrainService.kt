package com.kkllffaa.meteor_litematica_printer.swarms.brain

import com.kkllffaa.meteor_litematica_printer.swarms.net.SwarmServer
import com.kkllffaa.meteor_litematica_printer.swarms.protocol.SwarmAddress
import com.kkllffaa.meteor_litematica_printer.swarms.protocol.SwarmProtocol
import java.nio.file.Path

/**
 * 主脑服务（单例）——把传输层 [SwarmServer] 与公共信息 [BrainStore] 黏合起来，
 * 在“纯路由的服务器”之上实现全部**基座分配业务**：
 *  - 节点连上来（[onNodeJoined]）就补发它当前的基座分配；
 *  - 把分配表的增删改下发给对应工蜂（[pushAssignments]）；
 *  - 周期性 + 退出时把公共信息落盘，下次启动自动恢复。
 *
 * 本服务**不参与**工蜂行为。若希望主脑机器同时作为工蜂，请在同一客户端再启用工蜂模块连回本机
 * 服务器（这就是“主脑客户端也可以是工蜂”）。
 *
 * 线程模型：网络 I/O 全在守护线程；落盘可由任意线程（含 JVM 关闭钩子）触发。
 */
object BrainService {

    @Volatile
    private var store: BrainStore? = null

    @Volatile
    private var server: SwarmServer? = null

    @Volatile
    var running = false
        private set

    private var shutdownHook: Thread? = null

    fun start(bindAddr: String, statePath: Path, log: (String) -> Unit) {
        if (running) return
        val st = BrainStore(statePath)
        st.load() // 从磁盘恢复公共信息 —— “下次自动恢复”
        store = st

        val (bindIp, bindPort) = SwarmAddress.parse(bindAddr)
        val srv = SwarmServer(bindIp, bindPort, log, onJoin = ::onNodeJoined)
        try {
            srv.start()
            server = srv
        } catch (e: Exception) {
            log("Failed to start brain server: ${e.message}")
            server = null
        }

        installShutdownHook()
        running = true
    }

    fun stop() {
        running = false
        store?.save() // 退出时落盘
        server?.stop()
        server = null
        store = null
        removeShutdownHook()
    }

    /** 立即把公共信息落盘（周期性调用 + 退出时调用）。 */
    fun saveStore() {
        store?.save()
    }

    /**
     * 把一整张基座分配表同步给工蜂们（仅在变化时发包），并更新持久化存储。
     * map: baseName -> targetName。
     */
    fun pushAssignments(map: Map<String, String>) {
        val st = store ?: return
        // 新增 / 变更
        for ((base, target) in map) {
            if (st.assignments[base] != target) {
                st.setAssignment(base, target)
                pushAssignment(base, target)
            }
        }
        // 删除：存储里有、但本次分配表里没有的（直接遍历并发表，无需复制快照）
        for (base in st.assignments.keys) {
            if (!map.containsKey(base)) {
                st.removeAssignment(base)
                pushAssignment(base, "")
            }
        }
    }

    /** 节点报名回调（网络线程）：如果它是某人的基座，连上来就立刻把分配下发给它。 */
    private fun onNodeJoined(name: String) {
        val target = store?.assignments?.get(name) ?: return
        if (target.isNotBlank()) {
            server?.sendTo(name, SwarmProtocol.encode(SwarmProtocol.ASSIGN, name, target))
        }
    }

    /** 把一条基座分配下发给对应工蜂（target 为空表示解除分配）。 */
    private fun pushAssignment(base: String, target: String) {
        val srv = server ?: return
        srv.sendTo(
            base,
            if (target.isBlank()) SwarmProtocol.encode(SwarmProtocol.UNASSIGN, base)
            else SwarmProtocol.encode(SwarmProtocol.ASSIGN, base, target)
        )
    }

    private fun installShutdownHook() {
        if (shutdownHook != null) return
        val h = Thread({
            try {
                store?.save()
            } catch (_: Exception) {
            }
        }, "Swarm-Save-OnExit")
        try {
            Runtime.getRuntime().addShutdownHook(h)
            shutdownHook = h
        } catch (_: Exception) {
        }
    }

    private fun removeShutdownHook() {
        val h = shutdownHook ?: return
        try {
            Runtime.getRuntime().removeShutdownHook(h)
        } catch (_: Exception) {
        }
        shutdownHook = null
    }
}
