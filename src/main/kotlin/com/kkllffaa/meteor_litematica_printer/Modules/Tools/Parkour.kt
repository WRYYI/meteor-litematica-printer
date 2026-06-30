package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.google.common.collect.Streams
import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.*
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.DoubleSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove
import meteordevelopment.meteorclient.systems.modules.render.Freecam
import meteordevelopment.meteorclient.utils.misc.input.Input
import meteordevelopment.orbit.EventHandler
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.sqrt

object Parkour : Module(Addon.TOOLS, "parkour", "Automatically jumps at the edges of blocks.") {
    private val sgGeneral = settings.defaultGroup

    private val edgeDistance: Setting<Double> = sgGeneral.add(
        DoubleSetting.Builder()
            .name("edge-distance")
            .description("How far from the edge should you jump.")
            .range(0.001, 0.1)
            .defaultValue(0.004)
            .build()
    )

    private val minSpeed: Setting<Double> = sgGeneral.add(
        DoubleSetting.Builder()
            .name("min-speed")
            .description("Minimum horizontal speed required to trigger automatic jumping.(block/tick)")
            .range(0.0, 0.39285)
            .defaultValue(0.085)
            .build()
    )
    private val 悬空高度: Setting<Double> = sgGeneral.add(
        DoubleSetting.Builder()
            .name("悬空高度")
            .description("脚下高度内没有碰撞才可以跳跃")
            .range(0.0001, 1.5)
            .defaultValue(0.1249)
            .build()
    )
    private val 垫幽灵砖: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("垫幽灵砖")
            .description("Whether to place ghost blocks under the player.")
            .defaultValue(false)
            .build()
    )

    private var needEdgeJumping = false

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        mc.options.keyJump.setDown(needEdgeJumping || (Input.isPressed(mc.options.keyJump) && isPlayerInControl))
    }

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        val player = mc.player ?: run {
            needEdgeJumping = false
            return
        }
        val world = mc.level ?: run {
            needEdgeJumping = false
            return
        }
        if (!player.onGround() || mc.options.keyJump.isDown ||
            player.isShiftKeyDown || mc.options.keyShift.isDown
        ) {
            needEdgeJumping = false
            if (垫幽灵砖.get()) {
                world.setBlockAndUpdate(player.blockPosition().below(), Blocks.STONE.defaultBlockState())
            }
        } else {
            val horizontalSpeed =
                sqrt(player.deltaMovement.x * player.deltaMovement.x + player.deltaMovement.z * player.deltaMovement.z)
            needEdgeJumping = if (horizontalSpeed < minSpeed.get()) {
                false
            } else {
                val adjustedBox = player.boundingBox.move(0.0, -悬空高度.get(), 0.0)
                    .inflate(-edgeDistance.get(), 0.0, -edgeDistance.get())
                val blockCollisions = Streams.stream<VoxelShape>(world.getBlockCollisions(player, adjustedBox))

                !blockCollisions.findAny().isPresent
            }
        }
    }



}
