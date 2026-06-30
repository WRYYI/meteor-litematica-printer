package com.kkllffaa.meteor_litematica_printer.crud.engine

import com.kkllffaa.meteor_litematica_printer.Functions.isAirOrFluid
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property

/**
 * “符合投影”的唯一判定。
 *
 * 判定调整：流体视为空气（[isAirOrFluid]）。
 *
 * 符合投影 = 世界方块与投影方块**一致**（同方块），且下列方向属性**值相同**。
 * 某属性只要有一方没有，便无法比较——按定义视为该条相同。
 */
object Projection {

    private fun orientationMatches(world: BlockState, schematic: BlockState): Boolean =
        same(world, schematic, BlockStateProperties.FACING) &&
            same(world, schematic, BlockStateProperties.FACING_HOPPER) &&
            same(world, schematic, BlockStateProperties.HORIZONTAL_FACING) &&
            same(world, schematic, BlockStateProperties.AXIS) &&
            same(world, schematic, BlockStateProperties.HORIZONTAL_AXIS) &&
            same(world, schematic, BlockStateProperties.RAIL_SHAPE_STRAIGHT) &&
            same(world, schematic, BlockStateProperties.VERTICAL_DIRECTION) &&
            same(world, schematic, BlockStateProperties.ROTATION_16) &&
            same(world, schematic, BlockStateProperties.ORIENTATION)

    /** 缺失跳过比较(视为相同) */
    private fun <T : Comparable<T>> same(world: BlockState, schematic: BlockState, p: Property<T>): Boolean =
        !world.hasProperty(p) || !schematic.hasProperty(p) || world.getValue(p) == schematic.getValue(p)

    /** 世界方块是否符合投影方块。 */
    fun matches(world: BlockState, schematic: BlockState): Boolean = when {
        world.isAirOrFluid -> schematic.isAirOrFluid
        schematic.isAirOrFluid -> false // 世界有砖、投影要空
        else -> world.block === schematic.block && orientationMatches(world, schematic)
    }
}
