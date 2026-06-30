package com.kkllffaa.meteor_litematica_printer.swarms.worker

import com.kkllffaa.meteor_litematica_printer.swarms.protocol.RemoteState
import java.util.concurrent.ConcurrentHashMap

/**
 * 本进程对“蜂群里其它节点最近状态”的本地视图，按节点名索引。
 *
 * 由网络线程写入（收到 STATE / BYE），由游戏 tick 线程读取（基座同步输入）。
 * 用 [ConcurrentHashMap] 保证跨线程读写安全。
 */
class RemoteStateTable {
    private val states = ConcurrentHashMap<String, RemoteState>()

    fun put(s: RemoteState) {
        states[s.name] = s
    }

    fun get(name: String): RemoteState? = states[name]

    fun remove(name: String) {
        states.remove(name)
    }

    fun clear() = states.clear()
}
