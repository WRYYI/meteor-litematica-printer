package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.util.Mth
import kotlin.math.roundToInt

object YawSnap : Module(
    Addon.TOOLS,
    "yaw-snap",
    "Locks player yaw to the nearest cardinal direction (N/E/S/W). Eliminates sub-degree drift so you can fly straight for thousands of blocks without sideways slip."
) {
    private val sgGeneral = settings.defaultGroup

    private val recomputeEachTick: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("recompute-each-tick")
            .description("If true, each tick re-finds the nearest cardinal direction so turning the mouse past 45° switches to the next direction. If false, the direction is locked at activation and stays fixed regardless of mouse movement.")
            .defaultValue(false)
            .build()
    )

    private var lockedYaw: Float = 0f

    override fun onActivate() {
        val player = mc.player ?: run { toggle(); return }
        lockedYaw = nearestCardinalYaw(player.yRot)
        player.yRot = lockedYaw
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        val target = if (recomputeEachTick.get()) nearestCardinalYaw(player.yRot) else lockedYaw
        player.yRot = target
    }

    override fun getInfoString(): String {
        val target = if (recomputeEachTick.get()) nearestCardinalYaw(mc.player?.yRot ?: 0f) else lockedYaw
        return cardinalName(target)
    }

    private fun nearestCardinalYaw(yaw: Float): Float {
        val wrapped = Mth.wrapDegrees(yaw)
        val snapped = (wrapped / 90f).roundToInt() * 90
        return Mth.wrapDegrees(snapped.toFloat())
    }

    private fun cardinalName(yaw: Float): String {
        val w = Mth.wrapDegrees(yaw)
        return when {
            w > -45f && w < 45f -> "S +Z"
            w in 45f..<135f -> "W -X"
            w <= -45f && w > -135f -> "E +X"
            else -> "N -Z"
        }
    }
}
