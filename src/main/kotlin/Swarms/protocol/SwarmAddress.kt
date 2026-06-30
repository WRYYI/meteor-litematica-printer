package com.kkllffaa.meteor_litematica_printer.swarms.protocol

/** "host:port" 地址解析。主脑绑定地址与工蜂连接地址都用它，保证两端口径一致。 */
object SwarmAddress {
    const val DEFAULT_PORT = 47000
    const val DEFAULT_HOST = "127.0.0.1"

    /** 解析 "host:port"，缺省主机 [DEFAULT_HOST]、缺省端口 [DEFAULT_PORT]。按最后一个 ':' 切分（兼容 IPv6 之外的常见写法）。 */
    fun parse(addr: String): Pair<String, Int> {
        val s = addr.trim()
        val idx = s.lastIndexOf(':')
        if (idx < 0) return (s.ifBlank { DEFAULT_HOST }) to DEFAULT_PORT
        val host = s.substring(0, idx).ifBlank { DEFAULT_HOST }
        val port = s.substring(idx + 1).toIntOrNull() ?: DEFAULT_PORT
        return host to port
    }
}
