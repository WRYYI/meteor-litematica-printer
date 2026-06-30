package com.kkllffaa.meteor_litematica_printer.Functions


import com.kkllffaa.meteor_litematica_printer.crud.atomic.*
import meteordevelopment.meteorclient.MeteorClient.mc
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove
import meteordevelopment.meteorclient.systems.modules.render.Freecam
import meteordevelopment.meteorclient.systems.modules.Modules
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.AreaEffectCloud
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Vec3i
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import kotlin.math.floor

val PlayerHandDistance get() = CommonSettings.PlayerHandDistance
val LocalPlayer.EyeCenterPos get() = Vec3(x, y + getEyeHeight(pose), z)

val PlayerYawDirection: Direction? get() = CommonSettings.playerYawDirection
val PlayerPitchDirection: Direction? get() = CommonSettings.playerPitchDirection
val PlayerYawInt16: Int? get() = CommonSettings.playerYawInt16

//region 玩家角度对准方块的面

//region 方块锚点到方块面(四顶点)的偏移常量Vec3i[4]
private val FACE_OFFSETS_UP = arrayOf(
    Vec3i(0, 1, 0),
    Vec3i(1, 1, 0),
    Vec3i(1, 1, 1),
    Vec3i(0, 1, 1)
)

private val FACE_OFFSETS_DOWN = arrayOf(
    Vec3i(0, 0, 0),
    Vec3i(1, 0, 0),
    Vec3i(1, 0, 1),
    Vec3i(0, 0, 1)
)

private val FACE_OFFSETS_NORTH = arrayOf(
    Vec3i(0, 0, 0),
    Vec3i(1, 0, 0),
    Vec3i(1, 1, 0),
    Vec3i(0, 1, 0)
)

private val FACE_OFFSETS_SOUTH = arrayOf(
    Vec3i(0, 0, 1),
    Vec3i(1, 0, 1),
    Vec3i(1, 1, 1),
    Vec3i(0, 1, 1)
)

private val FACE_OFFSETS_EAST = arrayOf(
    Vec3i(1, 0, 0),
    Vec3i(1, 0, 1),
    Vec3i(1, 1, 1),
    Vec3i(1, 1, 0)
)

private val FACE_OFFSETS_WEST = arrayOf(
    Vec3i(0, 0, 0),
    Vec3i(0, 0, 1),
    Vec3i(0, 1, 1),
    Vec3i(0, 1, 0)
)

//endregion
fun LocalPlayer.RotationInTheFaceOfBlock(blockPos: Vec3i, face: Direction): Boolean {
    val eye = EyeCenterPos
    val playerYaw = yRot
    val playerPitch = xRot
    val offsets = when (face) {
        Direction.UP -> FACE_OFFSETS_UP
        Direction.DOWN -> FACE_OFFSETS_DOWN
        Direction.NORTH -> FACE_OFFSETS_NORTH
        Direction.SOUTH -> FACE_OFFSETS_SOUTH
        Direction.EAST -> FACE_OFFSETS_EAST
        Direction.WEST -> FACE_OFFSETS_WEST
    }
    val pointsYawRelative = FloatArray(4)
    val pointsPitchRelative = FloatArray(4)
    for (i in offsets.indices) {
        val offset = offsets[i]
        val point = (blockPos + offset).vec3d
        val pointRot = (eye to point).Rotation
        pointsYawRelative[i] = (pointRot.yaw - playerYaw).normalizeAsYaw
        pointsPitchRelative[i] = pointRot.pitch - playerPitch
    }
    return pointsYawRelative.anyPairSpansZero && pointsPitchRelative.anyPairSpansZero
}


// endregion

fun LocalPlayer.switchTo(item: Item): Boolean = SwapSettings.switchTo(this, item)
fun LocalPlayer.switchTo(slot: Int): Boolean = SwapSettings.switchTo(this, slot)


enum class SwapDoResult {
    Success,
    没有物品,
    执行False,
}

fun LocalPlayer.swing(hand: InteractionHand, swingMode: ActionMode) {
    when (swingMode) {
        ActionMode.None -> {}
        ActionMode.SendPacket -> mc.connection?.send(ServerboundSwingPacket(hand))
        ActionMode.Normal -> swing(hand)
    }
}

val isPlayerInControl: Boolean
    get() = (mc.screen == null
            || Modules.get().get(GUIMove::class.java)?.skip() == false)
            && !Modules.get().isActive(Freecam::class.java)



enum class RideKind { 无, 马, 船, 矿车, 坐在玩家身上, 座位代理, 其他 }

val LocalPlayer.正在骑乘: Boolean get() = isPassenger

val LocalPlayer.rideKind: RideKind
    get() {
        if (!isPassenger) return RideKind.无
        if (rootVehicle is Player) return RideKind.坐在玩家身上
        return when (vehicle) {
            is AbstractHorse    -> RideKind.马
            is AbstractBoat     -> RideKind.船
            is AbstractMinecart -> RideKind.矿车
            is AreaEffectCloud  -> RideKind.座位代理   // 独立座位代理
            else                -> RideKind.其他
        }
    }
