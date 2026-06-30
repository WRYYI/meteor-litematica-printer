package com.kkllffaa.meteor_litematica_printer.Modules

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.mixins.MultiPlayerGameModeAccessor
import com.kkllffaa.meteor_litematica_printer.servers.当前大区
import meteordevelopment.meteorclient.MeteorClient.mc
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler

object Debug : Module(Addon.SettingsForCRUD, "Debug", "AllowEnhancedClassRedefinition") {
    private val sgGeneral = settings.defaultGroup

    /**
     * 监控点：每 tick 读一次 [取值]，和上次比较，只在变化时返回要打印的文本，避免刷屏。
     * 用 `==` 比较、`toString` 展示，所以支持任意类型 T（含可空、null）。
     */
    private class Watch<T>(private val 名称: String, private val 取值: () -> T) {
        private var 有值 = false
        private var 上次: T? = null

        /** 有变化（或首次取值）时返回要打印的文本，否则返回 null。 */
        fun 轮询(): String? {
            val 当前 = 取值()
            if (有值 && 当前 == 上次) return null
            val 文本 = if (有值) "$名称: ${显示(上次)} -> ${显示(当前)}" else "$名称 = ${显示(当前)}"
            有值 = true
            上次 = 当前
            return 文本
        }

        private fun 显示(值: T?): String = 值?.toString() ?: "null"
    }

    /** 所有监控点，onTick 里逐个轮询；要加新的值往这里加一行即可。 */
    private val 监控列表 = listOf(
        Watch("大区") { 当前大区?.name ?: "None" },
        Watch("destroyDelay") { (mc.gameMode as? MultiPlayerGameModeAccessor)?.destroyDelay },
    )

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        监控列表.forEach { watch -> watch.轮询()?.let { info(it) } }
    }
}
