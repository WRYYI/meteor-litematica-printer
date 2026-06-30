package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

/**
 * 当 Tab 列表中存在指定的玩家名字时，每隔固定秒数发起一次通知（聊天提示 + 可选音效）。
 * 一旦目标玩家从 Tab 列表中消失，下次重新出现会立即提示一次，随后再按间隔重复。
 */
object TabListNotifier : Module(Addon.TOOLS, "tab-list-notifier", "Tab 列表存在指定玩家时每隔若干秒提醒一次") {
    private val sgGeneral = settings.defaultGroup

    private val targetNames: Setting<MutableList<String>> = sgGeneral.add(
        StringListSetting.Builder()
            .name("player-names")
            .description("需要监控的玩家名字列表，只要其中任意一个出现在 Tab 列表就会提醒。")
            .defaultValue(ArrayList<String>())
            .build()
    )

    private val intervalSeconds: Setting<Int> = sgGeneral.add(
        IntSetting.Builder()
            .name("interval-seconds")
            .description("两次通知之间的间隔（秒）。")
            .defaultValue(10)
            .min(1)
            .sliderRange(1, 60)
            .build()
    )

    private val exactMatch: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("exact-match")
            .description("开启时名字需完全一致；关闭时只要包含即可匹配。")
            .defaultValue(true)
            .build()
    )

    private val ignoreCase: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("ignore-case")
            .description("匹配时忽略大小写。")
            .defaultValue(true)
            .build()
    )

    private val playSound: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("play-sound")
            .description("通知时播放一次提示音。")
            .defaultValue(true)
            .build()
    )

    private var lastNotifyTime = 0L

    override fun onActivate() {
        lastNotifyTime = 0L
    }

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        val connection = mc.connection ?: return
        if (targetNames.get().isEmpty()) return

        val hit = connection.onlinePlayers.firstNotNullOfOrNull { info ->
            val name = info.profile.name
            if (matches(name)) name else null
        }

        if (hit == null) {
            // 目标不在 Tab 列表中：重置计时，下次出现立即提醒
            lastNotifyTime = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastNotifyTime >= intervalSeconds.get() * 1000L) {
            lastNotifyTime = now
            notify(hit)
        }
    }

    private fun matches(onlineName: String): Boolean {
        return targetNames.get().any { target ->
            if (target.isBlank()) return@any false
            if (exactMatch.get()) {
                onlineName.equals(target, ignoreCase.get())
            } else {
                onlineName.contains(target, ignoreCase.get())
            }
        }
    }

    private fun notify(name: String) {
        info("Tab 列表中检测到玩家 §a%s§r", name)
        if (playSound.get()) {
            mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0f))
        }
    }
}
