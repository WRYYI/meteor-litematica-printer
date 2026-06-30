package com.kkllffaa.meteor_litematica_printer.crud.atomic


class CommandQueue(private val emit: (String) -> Unit) {

   
     private class Pending(val line: String, val gap: Int, val onSent: (() -> Unit)?)

    private val pending = ArrayDeque<Pending>()

    /** 距离下一条可放行还需空转的 tick 数。 */
    private var idleTicks = 0

    /** 当前排队中（尚未发出）的命令条数。 */
    val size: Int get() = pending.size

    /** 队列已排空且不处在间隔中——可据此驱动“发完这批再继续”的时序。 */
    val isIdle: Boolean get() = pending.isEmpty() && idleTicks == 0


    fun enqueue(line: String, gap: Int, onSent: (() -> Unit)? = null) {
        pending.addLast(Pending(line, gap, onSent))
    }

    fun clear() {
        pending.clear()
        idleTicks = 0
    }

    fun tick() {
        if (idleTicks > 0) {
            idleTicks--
            return
        }
        val next = pending.removeFirstOrNull() ?: return
        emit(next.line)
        idleTicks = next.gap
        next.onSent?.invoke()
    }
}
