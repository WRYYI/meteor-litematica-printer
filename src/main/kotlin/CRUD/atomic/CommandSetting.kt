package com.kkllffaa.meteor_litematica_printer.crud.atomic

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.KeybindSetting
import meteordevelopment.meteorclient.settings.StringListSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.misc.input.KeyAction
import meteordevelopment.meteorclient.utils.player.ChatUtils
import meteordevelopment.orbit.EventHandler

/** 一个按键绑定槽位的全部设置，外加 [CommandTrigger.Toggle] 用的翻转位。 */
internal class Slot(
    val keybind: KeybindSetting,
    val trigger: EnumSetting<CommandTrigger>,
    val interval: IntSetting,
    val commands: StringListSetting,
    val toggleCommands: StringListSetting,
) {
    /** 仅 [CommandTrigger.Toggle] 关心：true 表示下一次按下应发 [toggleCommands]。 */
    var toggled = false
}

/** 按键事件如何映射到要发送的命令列表。判定全部内聚在各枚举项里，调用方不再分支。 */
enum class CommandTrigger {
    /** 按下时发 [Slot.commands]。 */
    Press {
        override fun commandsFor(slot: Slot, pressed: Boolean) = slot.commands.takeIf { pressed }
    },

    /** 松开时发 [Slot.commands]。 */
    Release {
        override fun commandsFor(slot: Slot, pressed: Boolean) = slot.commands.takeUnless { pressed }
    },

    /** 每次按下翻转：奇数次发 [Slot.commands]，偶数次发 [Slot.toggleCommands]。 */
    Toggle {
        override fun commandsFor(slot: Slot, pressed: Boolean): StringListSetting? {
            if (!pressed) return null
            return (if (slot.toggled) slot.toggleCommands else slot.commands)
                .also { slot.toggled = !slot.toggled }
        }
    };

    /** 本次输入应发出的命令来源；null 表示这次输入与该触发方式无关。[Toggle] 会顺带推进自己的翻转位。 */
    internal abstract fun commandsFor(slot: Slot, pressed: Boolean): StringListSetting?
}

/**
 * 把按键/鼠标绑定到“有序、按 tick 限速”的命令序列。
 *
 * 输入事件经 [CommandTrigger] 解析出命令列表后压入 [queue]，由它充当唯一出口逐条以玩家身份发出。
 * 本模块只进不出，始终保持激活。
 */
object CommandSetting : Module(Addon.SettingsForCRUD, "CommandSetting", "Bind keys to ordered, rate-limited command sequences.") {

    private const val BIND_LIMIT = 16

    private val sgGeneral = settings.defaultGroup

    private val bindCount = sgGeneral.add(IntSetting.Builder()
        .name("bind-count")
        .description("How many key binds are active.")
        .defaultValue(1)
        .range(0, BIND_LIMIT)
        .sliderRange(0, BIND_LIMIT)
        .build())

    private val slots: List<Slot> = (0 until BIND_LIMIT).map { i ->
        val g = settings.createGroup("Bind ${i + 1}")
        val shown = { i < bindCount.get() }

        val keybind = g.add(KeybindSetting.Builder()
            .name("key")
            .description("Key or mouse button that triggers this bind.")
            .visible { shown() }
            .build())

        val trigger = g.add(EnumSetting.Builder<CommandTrigger>()
            .name("trigger")
            .description("Press: run on key down. Release: run on key up. Toggle: alternate two command sets on each press.")
            .defaultValue(CommandTrigger.Press)
            .visible { shown() }
            .build())

        val interval = g.add(IntSetting.Builder()
            .name("interval-ticks")
            .description("Idle ticks inserted between consecutive commands (0 = one command per tick).")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 40)
            .visible { shown() }
            .build())

        val commands = g.add(StringListSetting.Builder()
            .name("commands")
            .description("Commands run in order. For Toggle, these run on the odd (1st, 3rd, ...) press.")
            .visible { shown() }
            .build())

        val toggleCommands = g.add(StringListSetting.Builder()
            .name("toggle-commands")
            .description("Commands run on the even (2nd, 4th, ...) press. Toggle only.")
            .visible { shown() && trigger.get() == CommandTrigger.Toggle }
            .build())

        Slot(keybind, trigger, interval, commands, toggleCommands)
    }

    /** 唯一出口：按到达顺序、每 tick 至多一条地把命令当作玩家亲手输入发出去。 */
    private val queue = CommandQueue { line -> ChatUtils.sendPlayerMsg(line, false) }

    override fun toggle() {
        if (!isActive) super.toggle() // 只进不出
    }

    init {
        toggle()
    }

    @EventHandler
    private fun onKey(event: KeyInputEvent) = handleInput(isKey = true, event.key(), event.modifiers(), event.action)

    @EventHandler
    private fun onMouseClick(event: MouseClickEvent) = handleInput(isKey = false, event.button(), modifiers = 0, event.action)

    private fun handleInput(isKey: Boolean, code: Int, modifiers: Int, action: KeyAction) {
        if (action == KeyAction.Repeat) return
        if (mc.player == null || mc.screen != null) return

        val pressed = action == KeyAction.Press
        for (i in 0 until bindCount.get()) {
            val slot = slots[i]
            if (!slot.keybind.get().matches(isKey, code, modifiers)) continue
            slot.trigger.get().commandsFor(slot, pressed)?.let { dispatch(it.get(), slot.interval.get()) }
        }
    }

    /** 把一组命令按序压入队列；空行跳过，每条之间留 [gap] 个空转 tick。 */
    private fun dispatch(commands: List<String>, gap: Int) {
        for (line in commands) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) queue.enqueue(trimmed, gap)
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        if (mc.player == null) queue.clear() else queue.tick()
    }
}
