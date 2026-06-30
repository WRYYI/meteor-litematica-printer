package com.kkllffaa.meteor_litematica_printer.swarms

/**
 * 周期闸：每隔给定间隔放行一次，把「记一个时间戳 + 每 tick 比较」的重复写法收成一个名字。
 *
 * 间隔在每次询问时传入，因此既能用于固定周期，也能用于运行时可调的周期（如可配置的落盘间隔）。
 * 非线程安全：只在单一线程（游戏 tick）里使用。
 */
class Periodic {
    private var lastAt = 0L

    /** 距上次放行已达 [intervalMs] 则放行、记下当前时刻并返回 true；否则返回 false。 */
    fun due(intervalMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (now - lastAt < intervalMs) return false
        lastAt = now
        return true
    }

    /** (重新)启用时调用：[fireImmediately] 为 true 则下一次 [due] 立即放行，否则先等满一个间隔。 */
    fun reset(fireImmediately: Boolean, now: Long = System.currentTimeMillis()) {
        lastAt = if (fireImmediately) 0L else now
    }
}
