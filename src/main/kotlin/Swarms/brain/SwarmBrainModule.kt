package com.kkllffaa.meteor_litematica_printer.swarms.brain

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.swarms.Periodic
import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.settings.SettingGroup
import meteordevelopment.meteorclient.settings.StringListSetting
import meteordevelopment.meteorclient.settings.StringSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.fabricmc.loader.api.FabricLoader

/**
 * 蜂群「主脑」模块（Meteor 接入层，仅协调服务器）。
 *
 * 运行协调服务器、持有并周期落盘公共信息（基座分配表），主脑崩溃/退出后下次自动恢复。
 * 整套蜂群应只有一个客户端启用本模块。若主脑机器也要作为工蜂参与协作，请在同一客户端
 * 再启用工蜂模块。
 *
 * 注意：bind 地址在模块激活时读取一次；改动后请重新开关模块以生效。
 */
object SwarmBrain : Module(Addon.TOOLS, "swarm-brain", "蜂群主脑：协调服务器 + 公共信息落盘/恢复。") {

    private val sgConn: SettingGroup = settings.createGroup("connection")

    private val bindAddr: Setting<String> = sgConn.add(
        StringSetting.Builder()
            .name("bind")
            .description("主脑服务器绑定地址，host:port 格式（如 127.0.0.1:47000 / 0.0.0.0:47000）。")
            .defaultValue("127.0.0.1:47000")
            .build()
    )

    private val sgAssign: SettingGroup = settings.createGroup("assignments")

    private val assignments: Setting<MutableList<String>> = sgAssign.add(
        StringListSetting.Builder()
            .name("assignments")
            .description("基座分配表，每条格式 base:target。会持久化并在主脑重启后自动恢复。")
            .defaultValue(ArrayList())
            .build()
    )

    private val saveInterval: Setting<Int> = sgAssign.add(
        IntSetting.Builder()
            .name("save-interval")
            .description("公共信息周期性落盘的间隔（秒）。")
            .defaultValue(30)
            .range(5, 600)
            .noSlider()
            .build()
    )

    /** 周期性把分配表下发给工蜂。 */
    private val pushTimer = Periodic()

    /** 周期性把公共信息落盘。 */
    private val saveTimer = Periodic()

    override fun onActivate() {
        val statePath = FabricLoader.getInstance().gameDir.resolve("swarm").resolve("brain_state.txt")
        BrainService.start(
            bindAddr = bindAddr.get(),
            statePath = statePath,
            log = { msg -> MeteorClient.LOG.info("[Swarm] {}", msg) }
        )
        pushTimer.reset(fireImmediately = true)   // 启动即推一次分配
        saveTimer.reset(fireImmediately = false)  // 落盘先等满一个间隔
        info("Swarm brain started.")
    }

    override fun onDeactivate() {
        BrainService.stop()
        info("Swarm brain stopped.")
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (!BrainService.running) return
        if (pushTimer.due(PUSH_INTERVAL_MS)) BrainService.pushAssignments(parseAssignments())
        if (saveTimer.due(saveInterval.get() * 1000L)) BrainService.saveStore()
    }

    /** 把 "base:target" 文本列表解析成 base -> target 映射，忽略空白与格式错误项（同名后者覆盖前者）。 */
    private fun parseAssignments(): Map<String, String> =
        assignments.get().mapNotNull { entry ->
            val base = entry.substringBefore(':', "").trim()
            val target = entry.substringAfter(':', "").trim()
            if (base.isBlank() || target.isBlank()) null else base to target
        }.toMap()

    private const val PUSH_INTERVAL_MS = 1000L
}
