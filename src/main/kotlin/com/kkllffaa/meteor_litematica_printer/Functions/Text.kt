package com.kkllffaa.meteor_litematica_printer.Functions

import com.kkllffaa.meteor_litematica_printer.crud.atomic.PlaceSettings
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.*
import java.util.*

fun getFormattedLine(text: Component): String {
    val mode = PlaceSettings.SignTextWithColor.get()
    val controlChar = when (mode) {
        SignColorMode.None -> return text.string
        SignColorMode.`§` -> '§'
        SignColorMode.`&` -> '&'
    }

    val result = StringBuilder()
    var lastStyle: Style? = null

    text.visit({ style, content ->
        if (content.isNotEmpty()) {
            if (style != lastStyle) {
                result.append(styleToFormattingCodes(style, controlChar))
                lastStyle = style
            }
            result.append(content)
        }
        Optional.empty<Unit>()
    }, Style.EMPTY)

    return result.toString()
}


private fun styleToFormattingCodes(style: Style, controlChar: Char): String {
    if (style.isEmpty) return ""

    val codes = StringBuilder()

    style.color?.let { textColor ->
        val formatting = getFormattingFromColor(textColor)
        if (formatting != null) {
            codes.append(controlChar).append(formatting.char)
        }
    }

    if (style.isBold) {
        codes.append(controlChar).append(ChatFormatting.BOLD.char)
    }
    if (style.isItalic) {
        codes.append(controlChar).append(ChatFormatting.ITALIC.char)
    }
    if (style.isUnderlined) {
        codes.append(controlChar).append(ChatFormatting.UNDERLINE.char)
    }
    if (style.isStrikethrough) {
        codes.append(controlChar).append(ChatFormatting.STRIKETHROUGH.char)
    }
    if (style.isObfuscated) {
        codes.append(controlChar).append(ChatFormatting.OBFUSCATED.char)
    }

    return codes.toString()
}


private fun getFormattingFromColor(textColor: TextColor): ChatFormatting? {
    for (formatting in ChatFormatting.entries) {
        if (formatting.isColor) {
            val colorValue = formatting.color
            if (colorValue != null && colorValue == textColor.value) {
                return formatting
            }
        }
    }
    return null
}
