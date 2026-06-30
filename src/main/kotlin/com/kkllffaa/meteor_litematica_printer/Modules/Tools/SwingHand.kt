package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.isPlayerInControl
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.misc.input.Input
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent
import meteordevelopment.meteorclient.utils.misc.input.KeyAction

import meteordevelopment.meteorclient.settings.*
import meteordevelopment.orbit.EventHandler
import net.minecraft.world.InteractionHand
import meteordevelopment.orbit.EventPriority

object SwingHand : Module(Addon.TOOLS, "SwingHand", "Swing your hands with LR Click when module is enabled.") {
    init {
        toggleOnBindRelease = true
    }

    private val sgGeneral = settings.defaultGroup

    private val 持续连续挥手: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("continuously-swing")
            .description("Wave your hand continuously when the key is pressed instead of just once.")
            .defaultValue(true)
            .build()
    )
    private val 持续挥手间隔tick: Setting<Int> = sgGeneral.add(
        IntSetting.Builder()
            .name("swing-interval-tick")
            .description("The interval tick for continuously swinging hand.")
            .defaultValue(4)
            .min(1).sliderMin(4)
            .max(1024).sliderMax(10)
            .visible { 持续连续挥手.get() }
            .build()
    )
    private var wasAttackPressed = false
    private var wasUsePressed = false

    private var tickCounter = 0

    private var nextHandIsMain = true

    override fun onActivate() {
        wasAttackPressed = false
        wasUsePressed = false
        tickCounter = 0
    }

    @EventHandler(priority = EventPriority.HIGHEST + 100)
    private fun onMouse(event: MouseClickEvent) {
        if (event.action == KeyAction.Press && isPlayerInControl
            && (mc.options.keyAttack.matchesMouse(event.click) || mc.options.keyUse.matchesMouse(event.click))
        ) {
            toggleOnBindRelease = true
            event.cancel()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST + 100)
    private fun onKey(event: KeyInputEvent) {
        if (event.action == KeyAction.Press && isPlayerInControl
            && (Input.getKey(mc.options.keyAttack) == event.key() || Input.getKey(mc.options.keyUse) == event.key())
        ) {
            event.cancel()
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return

        mc.options.keyAttack.setDown(false)
        mc.options.keyUse.setDown(false)
        val attackPressed = Input.isPressed(mc.options.keyAttack)
        val usePressed = Input.isPressed(mc.options.keyUse)

        if (持续连续挥手.get()) {
            tickCounter++
            val interval = 持续挥手间隔tick.get()

            if (tickCounter % interval == 0) {
                if (attackPressed && usePressed) {
                    player.swing(if (nextHandIsMain) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND)
                    nextHandIsMain = !nextHandIsMain
                } else if (attackPressed) {
                    player.swing(InteractionHand.OFF_HAND)
                } else if (usePressed) {
                    player.swing(InteractionHand.MAIN_HAND)
                }
            }
        } else {
            if (attackPressed && !wasAttackPressed) {
                player.swing(InteractionHand.OFF_HAND)
            } else if (usePressed && !wasUsePressed) {
                player.swing(InteractionHand.MAIN_HAND)
            }
            wasAttackPressed = attackPressed
            wasUsePressed = usePressed
        }


    }

}
