package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.mixin.AbstractContainerScreenAccessor
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.systems.modules.Module
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import java.util.Optional

object CopyItemName : Module(
    Addon.TOOLS,
    "copy-item-name",
    "Copy the fully styled name (color, italic, rarity, etc.) of an item to the clipboard. Hover a slot or use held item, then press the bind."
) {
    init {
        toggleOnBindRelease = true
        runInMainMenu = true
    }

    private val sgGeneral = settings.defaultGroup

    private val source = sgGeneral.add(
        EnumSetting.Builder<Source>()
            .name("source")
            .description("Where to read the item from.")
            .defaultValue(Source.HoveredOrMainHand)
            .build()
    )

    private val useStyledName = sgGeneral.add(
        BoolSetting.Builder()
            .name("styled-name")
            .description("Use the displayed styled name (adds rarity color and italic for renamed items). Disable to copy the raw hover name only.")
            .defaultValue(true)
            .build()
    )

    private val includeBrackets = sgGeneral.add(
        BoolSetting.Builder()
            .name("with-brackets")
            .description("Wrap the name in square brackets like a chat item link.")
            .defaultValue(false)
            .build()
    )

    private val controlChar = sgGeneral.add(
        EnumSetting.Builder<ControlChar>()
            .name("format-char")
            .description("Which control character to use for formatting codes.")
            .defaultValue(ControlChar.SECTION)
            .build()
    )

    private val notify = sgGeneral.add(
        BoolSetting.Builder()
            .name("notify")
            .description("Send a chat message with the result.")
            .defaultValue(true)
            .build()
    )

    override fun onActivate() {
        try {
            val stack = pickStack()
            if (stack == null || stack.isEmpty) {
                if (notify.get()) warning("No item to copy.")
                return
            }

            val component: Component = when {
                includeBrackets.get() -> bracketWrap(if (useStyledName.get()) stack.styledHoverName else stack.hoverName)
                useStyledName.get() -> stack.styledHoverName
                else -> stack.hoverName
            }

            val ch = controlChar.get().char
            val formatted = componentToFormattedString(component, ch)

            mc.keyboardHandler.clipboard = formatted
            if (notify.get()) info("Copied: %s", formatted)
        } finally {
            toggle()
        }
    }

    private fun pickStack(): ItemStack? {
        val mode = source.get()
        if (mode == Source.MainHand) return mc.player?.mainHandItem
        if (mode == Source.OffHand) return mc.player?.offhandItem

        // HoveredOrMainHand
        val screen = mc.screen
        if (screen is AbstractContainerScreen<*>) {
            val held = mc.player?.containerMenu?.carried
            if (held != null && !held.isEmpty) return held
            val slot = (screen as AbstractContainerScreenAccessor).`meteor$getHoveredSlot`()
            if (slot != null && slot.hasItem()) return slot.item
        }
        return mc.player?.mainHandItem ?: mc.player?.offhandItem
    }

    private fun bracketWrap(name: Component): Component =
        Component.literal("[").append(name.copy()).append("]")

    private fun componentToFormattedString(text: Component, ch: Char): String {
        val out = StringBuilder()
        var last: Style? = null
        text.visit({ style, content ->
            if (content.isNotEmpty()) {
                if (style != last) {
                    out.append(styleToCodes(style, ch))
                    last = style
                }
                out.append(content)
            }
            Optional.empty<Unit>()
        }, Style.EMPTY)
        return out.toString()
    }

    private fun styleToCodes(style: Style, ch: Char): String {
        if (style.isEmpty) return "$ch${ChatFormatting.RESET.char}"
        val sb = StringBuilder()
        sb.append(ch).append(ChatFormatting.RESET.char)
        style.color?.let { c ->
            formattingFor(c)?.let { sb.append(ch).append(it.char) }
        }
        if (style.isBold) sb.append(ch).append(ChatFormatting.BOLD.char)
        if (style.isItalic) sb.append(ch).append(ChatFormatting.ITALIC.char)
        if (style.isUnderlined) sb.append(ch).append(ChatFormatting.UNDERLINE.char)
        if (style.isStrikethrough) sb.append(ch).append(ChatFormatting.STRIKETHROUGH.char)
        if (style.isObfuscated) sb.append(ch).append(ChatFormatting.OBFUSCATED.char)
        return sb.toString()
    }

    private fun formattingFor(color: TextColor): ChatFormatting? {
        for (f in ChatFormatting.entries) {
            if (f.isColor && f.color != null && f.color == color.value) return f
        }
        return null
    }

    enum class Source { HoveredOrMainHand, MainHand, OffHand }

    enum class ControlChar(val char: Char) {
        SECTION('§'),
        AMPERSAND('&')
    }
}
