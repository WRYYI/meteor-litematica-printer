package com.kkllffaa.meteor_litematica_printer.swarms.net

import com.kkllffaa.meteor_litematica_printer.swarms.protocol.SwarmProtocol
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 蜂群协调服务器——**纯传输 / 路由层**，不掺杂任何业务（基座分配等）语义。
 *
 * 职责：
 *  - 监听 TCP 端口，接受所有工蜂（含主脑自己进程内的工蜂）连接。
 *  - 维护“连接名 -> 连接”的映射（节点用 HELLO 报名）。
 *  - 按协议做消息路由：STATE 转发给其它人、CALL 广播、TPREQ 单播给目标本人、断线广播 BYE。
 *  - 向上层暴露 [sendTo]（按名字单播）与 [onJoin]（节点报名回调），让 brain 层在此之上实现
 *    “节点连上来就补发它的基座分配”等业务，而服务器本身对此一无所知（避免传输层依赖业务层）。
 *
 * 线程模型：全部网络 I/O 在守护线程里完成，绝不触碰 Minecraft 主线程；回调 [onJoin] 也在网络线程触发。
 */
class SwarmServer(
    private val bindIp: String,
    private val port: Int,
    private val log: (String) -> Unit,
    /** 某节点完成报名（HELLO）后回调，参数是节点名。在网络线程调用。 */
    private val onJoin: (String) -> Unit = {}
) {
    private inner class Conn(val socket: Socket) {
        private val out = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

        @Volatile
        var name: String = ""

        fun send(line: String) {
            try {
                synchronized(out) {
                    out.write(line)
                    out.flush()
                }
            } catch (e: Exception) {
                close()
            }
        }

        fun close() {
            socket.closeQuietly()
            conns.remove(this)
        }
    }

    private val conns = CopyOnWriteArrayList<Conn>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(bindIp, port))
        serverSocket = ss
        running = true
        Thread({ acceptLoop(ss) }, "Swarm-Server-Accept").apply {
            isDaemon = true
            start()
        }
        log("Brain server listening on $bindIp:$port")
    }

    fun stop() {
        running = false
        serverSocket.closeQuietly()
        serverSocket = null
        for (c in conns) c.close()
        conns.clear()
    }

    /** 按名字单播一整行（须自带换行）。命中返回 true。 */
    fun sendTo(name: String, line: String): Boolean {
        val c = conns.firstOrNull { it.name == name } ?: return false
        c.send(line)
        return true
    }

    // ---- 内部：网络线程 ------------------------------------------------------

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            val sock = try {
                ss.accept()
            } catch (e: Exception) {
                if (running) log("accept error: ${e.message}")
                break
            }
            try {
                sock.tcpNoDelay = true
            } catch (_: Exception) {
            }
            val conn = Conn(sock)
            conns.add(conn)
            Thread({ readLoop(conn) }, "Swarm-Server-Conn").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun readLoop(conn: Conn) {
        try {
            val reader = BufferedReader(InputStreamReader(conn.socket.getInputStream(), Charsets.UTF_8))
            while (running) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) continue
                handle(conn, SwarmProtocol.decode(line), line)
            }
        } catch (e: Exception) {
            // 连接断开，统一走 finally 清理
        } finally {
            val n = conn.name
            conn.close()
            if (n.isNotEmpty()) {
                broadcast(SwarmProtocol.encode(SwarmProtocol.BYE, n), except = null)
                log("node left: $n")
            }
        }
    }

    private fun handle(conn: Conn, fields: List<String>, raw: String) {
        when (SwarmProtocol.typeOf(fields)) {
            SwarmProtocol.HELLO -> {
                val newName = fields.getOrElse(1) { "" }
                if (newName.isNotBlank()) {
                    conn.name = newName
                    log("node joined: $newName")
                    onJoin(newName)
                }
            }
            // STATE：转发给除发送者外的所有人，让各基座能读到自己要驮的玩家 A 的输入
            SwarmProtocol.STATE -> if (conn.name.isNotEmpty()) broadcast(raw + SwarmProtocol.DELIM, except = conn)
            // CALL：呼唤信号广播给所有人（发送者自身会在 worker 侧按目标过滤，故无需特殊处理）
            SwarmProtocol.CALL -> broadcast(raw + SwarmProtocol.DELIM, except = null)
            // TPREQ：只发给被传送的目标本人
            SwarmProtocol.TPREQ -> fields.getOrNull(2)?.let { target -> sendTo(target, raw + SwarmProtocol.DELIM) }
        }
    }

    /** 把一整行（须自带换行）发给除 [except] 外所有已报名连接。 */
    private fun broadcast(line: String, except: Conn?) {
        for (c in conns) {
            if (c !== except && c.name.isNotEmpty()) c.send(line)
        }
    }
}
