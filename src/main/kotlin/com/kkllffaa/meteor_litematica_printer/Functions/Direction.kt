package com.kkllffaa.meteor_litematica_printer.Functions

import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.RailShape
import net.minecraft.world.level.block.state.properties.RotationSegment

val Float.AsYawToDirectionNSWE: Direction
    get() = when (val yaw = this.normalizeAsYaw) {
        in 45f..<135f -> Direction.WEST
        in -45f..<45f -> Direction.SOUTH
        in -135f..<-45f -> Direction.EAST
        else -> Direction.NORTH
    }

val Float.AsPitchToDirectionUD: Direction?
    get() = when {
        this > 45f -> Direction.UP
        this < -45f -> Direction.DOWN
        else -> null
    }

val Direction.Left: Direction?
    get() = when (this) {
        Direction.NORTH -> Direction.WEST
        Direction.SOUTH -> Direction.EAST
        Direction.WEST -> Direction.SOUTH
        Direction.EAST -> Direction.NORTH
        else -> null
    }
val Direction.Right: Direction? get() = Left?.opposite

val Int.opposite: Int get() = (this + 8) % 16
val Int.Left: Int get() = (this + 12) % 16
val Int.Right: Int get() = (this + 4) % 16

val RailShape.Dir: Direction?
    get() = when (this) {
        RailShape.NORTH_SOUTH -> Direction.SOUTH
        RailShape.EAST_WEST -> Direction.EAST
        else -> null
    }

val BlockState.ATagFaceOf6: Direction?
    get() = when {
        hasProperty(BlockStateProperties.FACING) -> getValue(BlockStateProperties.FACING)
        hasProperty(BlockStateProperties.FACING_HOPPER) -> getValue(BlockStateProperties.FACING_HOPPER)
        hasProperty(BlockStateProperties.HORIZONTAL_FACING) -> getValue(BlockStateProperties.HORIZONTAL_FACING)
        hasProperty(BlockStateProperties.AXIS) -> Direction.fromAxisAndDirection(
            getValue(BlockStateProperties.AXIS),
            Direction.AxisDirection.POSITIVE
        )

        hasProperty(BlockStateProperties.HORIZONTAL_AXIS) -> Direction.fromAxisAndDirection(
            getValue(
                BlockStateProperties.HORIZONTAL_AXIS
            ), Direction.AxisDirection.POSITIVE
        )

        hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT) -> getValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT).Dir
        hasProperty(BlockStateProperties.VERTICAL_DIRECTION) -> getValue(BlockStateProperties.VERTICAL_DIRECTION)
        hasProperty(BlockStateProperties.ROTATION_16) -> RotationSegment.convertToDirection(
            getValue(
                BlockStateProperties.ROTATION_16
            )
        ).orElse(null)

        hasProperty(BlockStateProperties.ORIENTATION) -> getValue(BlockStateProperties.ORIENTATION).front()
        else -> null
    }

