package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.systems.modules.Module

/**
 * While active, drops Minecraft's text validation so any character survives everywhere it is
 * normally filtered: chat input, sign and anvil fields, `§` color/format codes, and whitespace-only
 * names.
 *
 * The module's own on/off state is the switch; the text mixins gate directly on [isActive].
 */
object IgnoreBorders : Module(
    Addon.TOOLS,
    "Ignore-Borders",
    "isWithinBounds=true",
)