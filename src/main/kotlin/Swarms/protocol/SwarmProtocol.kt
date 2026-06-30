package com.kkllffaa.meteor_litematica_printer.swarms.protocol

/**
 * 蜂群跨进程通信的**线协议**（与传输、角色逻辑无关，处于依赖最底层）。
 *
 * 一条消息 = 一行以 [DELIM] 结尾的文本；字段之间用 [SEP] 分隔。字段值内部保证不含 TAB/CR/LF
 * （发送前由 [sanitize] 清洗）。零外部依赖、便于抓包调试、解析开销极低。
 *
 * 消息一览（首字段为类型）：
 *  - [HELLO]    \t name                       节点报名（连上来 / 改名时发）
 *  - [BYE]      \t name                       节点离开（服务器在连接断开时广播）
 *  - [STATE]    \t name \t yaw \t keyMask     节点上报朝向与移动按键，服务器转发给其它人
 *  - [CALL]     \t caller                     呼唤信号，服务器广播给所有人
 *  - [ASSIGN]   \t base \t target             主脑下发：base 成为 target 的移动基座
 *  - [UNASSIGN] \t base                       主脑下发：解除 base 的基座身份
 *  - [TPREQ]    \t base \t target             基座发起 tp 请求，服务器只转发给 target 本人
 */
object SwarmProtocol {
    const val SEP = '\t'
    const val DELIM = '\n'

    const val HELLO = "HELLO"
    const val BYE = "BYE"
    const val STATE = "STATE"
    const val CALL = "CALL"
    const val ASSIGN = "ASSIGN"
    const val UNASSIGN = "UNASSIGN"
    const val TPREQ = "TPREQ"

    /** 把若干字段编码成一整行（含结尾换行），可直接写进 socket。 */
    fun encode(vararg parts: String): String =
        parts.joinToString(SEP.toString()) { sanitize(it) } + DELIM

    /** 把一行（不含换行）解析成字段列表。 */
    fun decode(line: String): List<String> = line.split(SEP)

    /** 字段类型（首字段），等价于 `decode(line).firstOrNull()`，可读性更好。 */
    fun typeOf(fields: List<String>): String? = fields.getOrNull(0)

    private fun sanitize(s: String): String =
        s.replace(SEP, ' ').replace(DELIM, ' ').replace('\r', ' ')
}
