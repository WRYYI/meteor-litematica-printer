package com.kkllffaa.meteor_litematica_printer

import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.MeteorClient.mc
import meteordevelopment.meteorclient.events.game.GameJoinedEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen

/**
 * 统计“连接到当前服务器后发生的地图加载次数”。
 */
object WorldLoadTracker {

    @Volatile
    var loadCount = 0
        private set

    /** 订阅事件总线开始计数（在 Addon.onInitialize 调用一次）。 */
    fun install() {
        MeteorClient.EVENT_BUS.subscribe(this)
    }

    @EventHandler
    private fun onGameJoined(event: GameJoinedEvent) {
        loadCount++
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (loadCount != 0 && atMainMenu()) loadCount = 0
    }

    /** 是否真正回到了主菜单（标题 / 多人服务器列表 / 断开界面），而非转移途中的忙碌界面。 */
    private fun atMainMenu(): Boolean {
        if (mc.level != null) return false
        val screen = mc.screen ?: return false
        return screen is TitleScreen || screen is JoinMultiplayerScreen || screen is DisconnectedScreen
    }
}
