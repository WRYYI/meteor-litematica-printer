package com.kkllffaa.meteor_litematica_printer.swarms.net

import com.kkllffaa.meteor_litematica_printer.swarms.protocol.SwarmProtocol
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 工蜂 ↔ 主脑的一条客户端连接——**纯传输层**。
 *
 * 特性：
 *  - 后台守护线程里连接、读取；断线后按固定间隔自动重连（主脑崩溃重启后工蜂会自动接回）。
 *  - 连上来先发 HELLO 报名（名字由 [selfName] 惰性提供，避免在网络线程访问游戏状态），
 *    之后由外部按 tick 调用 [send] 推送消息。
 *  - 收到的每一行交给 [onMessage]（在网络线程上调用，回调内禁止触碰游戏主线程状态）。
 */
class SwarmClient(
    private val host: String,
    private val port: Int,
    private val selfName: () -> String,
    private val onMessage: (List<String>) -> Unit,
    private val log: (String) -> Unit
) {
    @Volatile
    private var running = false

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var out: BufferedWriter? = null

    fun start() {
        if (running) return
        running = true
        Thread({ loop() }, "Swarm-Client").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        socket.closeQuietly()
    }

    fun send(line: String) {
        val w = out ?: return
        try {
            synchronized(w) {
                w.write(line)
                w.flush()
            }
        } catch (e: Exception) {
            socket.closeQuietly()
        }
    }

    // ---- 内部：网络线程 ------------------------------------------------------

    private fun loop() {
        while (running) {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket = s
                out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                log("connected to brain $host:$port")

                send(SwarmProtocol.encode(SwarmProtocol.HELLO, selfName()))

                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (running) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    try {
                        onMessage(SwarmProtocol.decode(line))
                    } catch (e: Exception) {
                        // 单条消息处理失败不该拖垮整条连接
                    }
                }
            } catch (e: Exception) {
                // 连接失败 / 中途掉线：下面统一清理后退避重连
            } finally {
                socket.closeQuietly()
                socket = null
                out = null
            }
            if (running) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4000
        const val RECONNECT_DELAY_MS = 2000L
    }
}
