package com.kkllffaa.meteor_litematica_printer

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object RotationStuff {
    const val DEG_TO_RAD: Double = Math.PI / 180.0
    const val DEG_TO_RAD_F: Float = DEG_TO_RAD.toFloat()
    const val RAD_TO_DEG: Double = 180.0 / Math.PI
    const val RAD_TO_DEG_F: Float = RAD_TO_DEG.toFloat()
    fun calcRotationFromVec3d(orig: Vec3, dest: Vec3, current: Rotation): Rotation {
        return wrapAnglesToRelative(current, calcRotationFromVec3d(orig, dest))
    }

    private fun calcRotationFromVec3d(orig: Vec3, dest: Vec3): Rotation {
        val delta = doubleArrayOf(orig.x - dest.x, orig.y - dest.y, orig.z - dest.z)
        val yaw = Mth.atan2(delta[0], -delta[2])
        val dist = sqrt(delta[0] * delta[0] + delta[2] * delta[2])
        val pitch = Mth.atan2(delta[1], dist)
        return Rotation(
            (yaw * RAD_TO_DEG).toFloat(),
            (pitch * RAD_TO_DEG).toFloat()
        )
    }

    fun wrapAnglesToRelative(current: Rotation, target: Rotation): Rotation {
        if (current.yawIsReallyClose(target)) {
            return Rotation(current.yaw, target.pitch)
        }
        return target.subtract(current).normalize().add(current)
    }

    fun calcLookDirectionFromRotation(rotation: Rotation): Vec3 {
        val flatZ = Mth.cos((-rotation.yaw * DEG_TO_RAD) - Math.PI)
        val flatX = Mth.sin((-rotation.yaw * DEG_TO_RAD) - Math.PI)
        val pitchBase = -Mth.cos(-rotation.pitch * DEG_TO_RAD)
        val pitchHeight = Mth.sin(-rotation.pitch * DEG_TO_RAD)
        return Vec3((flatX * pitchBase).toDouble(), pitchHeight.toDouble(), (flatZ * pitchBase).toDouble())
    }


    fun rayTraceTowards(entity: LocalPlayer, rotation: Rotation, blockReachDistance: Double): HitResult {
        val start = entity.getEyePosition(1.0f)


        val direction = calcLookDirectionFromRotation(rotation)
        val end = start.add(
            direction.x * blockReachDistance,
            direction.y * blockReachDistance,
            direction.z * blockReachDistance
        )
        return entity.level().clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity))
    }
}
