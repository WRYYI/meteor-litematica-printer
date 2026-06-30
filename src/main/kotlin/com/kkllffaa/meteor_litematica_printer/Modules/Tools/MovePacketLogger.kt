package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

object MovePacketLogger : Module(Addon.TOOLS, "move-packet-logger", "Logs PlayerMoveC2SPacket information when sent.") {
    private val sgGeneral = settings.defaultGroup

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet

        if (packet is ServerboundMovePlayerPacket) {
            val info = buildString {
                append("[MovePacket] ")

                when (packet) {
                    is ServerboundMovePlayerPacket.PosRot -> append("Full | ")
                    is ServerboundMovePlayerPacket.Pos -> append("Position | ")
                    is ServerboundMovePlayerPacket.Rot -> append("Look | ")
                    is ServerboundMovePlayerPacket.StatusOnly -> append("OnGround | ")
                    else -> append("Unknown | ")
                }

                if (packet.hasPosition()) {
                    append("Pos(%.2f, %.2f, %.2f) ".format(packet.getX(0.0), packet.getY(0.0), packet.getZ(0.0)))
                }

                if (packet.hasRotation()) {
                    append("Yaw=%.2f Pitch=%.2f ".format(packet.getYRot(0f), packet.getXRot(0f)))
                }

                append("OnGround=${packet.isOnGround}")
            }

            info(info)
        }
    }
}
