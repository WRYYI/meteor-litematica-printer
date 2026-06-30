package com.kkllffaa.meteor_litematica_printer.swarms.net

/** 尽力关闭并吞掉异常——清理 socket / server-socket 时只想关掉它，不关心是否失败。 */
internal fun AutoCloseable?.closeQuietly() {
    try {
        this?.close()
    } catch (_: Exception) {
    }
}
