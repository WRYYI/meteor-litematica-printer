package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.Utils
import meteordevelopment.meteorclient.utils.misc.input.Input
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.gui.screens.inventory.InventoryScreen

object HangUp : Module(Addon.TOOLS, "HangUp", "Hang up the player.") {
    init {
        if (isActive) toggle()
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        if (!isActive || mc.player == null) return
        mc.options.keyShift.setDown(true)
        if (Input.isPressed(mc.options.keyShift)) toggle()
    }

    /**
     * @see {@link net.minecraft.client.MinecraftClient.handleInputEvents
     */
    override fun onActivate() {
        mc.player?.let { Utils.screenToOpen = InventoryScreen(it) }
    }

    override fun onDeactivate() {
        mc.options.keyShift.setDown(Input.isPressed(mc.options.keyShift))
    }
}
