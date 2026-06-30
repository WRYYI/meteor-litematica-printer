package com.kkllffaa.meteor_litematica_printer.Functions

import com.kkllffaa.meteor_litematica_printer.crud.atomic.*
import meteordevelopment.meteorclient.MeteorClient.mc
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.utils.world.BlockUtils
import net.minecraft.world.phys.HitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Vec3i
import net.minecraft.world.level.LightLayer

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Block
import kotlin.math.*


val BlockPos.canTouch get() = CommonSettings.playerCanTouchBlockPos(this)
fun BlockPos.canBreakIt(blockState: BlockState? = null): Boolean {
    val state = blockState ?: mc.level?.getBlockState(this) ?: return false
    return canTouch && BlockUtils.canBreak(this, state) && !BreakSettings.被相邻方块保护(this)
}


fun BlockPos.BreakIt() {
    BreakSettings.breakBlockWithRotationCfg(this)
}

fun BlockPos.TryBreakIt(blockState: BlockState? = null): Boolean {
    if (canBreakIt(blockState)) {
        BreakIt()
        return true
    }
    return false
}

fun BlockState.needInteractionCountsTo(targetState: BlockState): Int =
    InteractSettings.calculateRequiredInteractions(targetState, this)

fun BlockPos.TryInteractIt(count: Int = 1): Int = InteractSettings.TryInteractBlock(this, count)

fun BlockState.TryPlaceIt(pos: BlockPos, worldPosState: BlockState? = null): Boolean =
    PlaceSettings.TryPlaceBlock(this, pos, worldPosState)


val BlockState.isBlockCollisionFullCube: Boolean
    get() {
        try {
            return Block.isShapeFullBlock(getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
        } catch (ignored: Exception) {
            // if we can't get the collision shape, assume it's bad...
        }
        return false
    }

val BlockState.isAirOrFluid get() = this.isAir || this.block is net.minecraft.world.level.block.LiquidBlock
private const val 速度阈值 = 0.009
val Vec3i.会阻挡玩家移动
    get() : Boolean {
        val player = mc.player ?: return false
        val playerPos = player.position()
        val playerX = playerPos.x
        val playerY = playerPos.y
        val playerZ = playerPos.z

        val velocity = player.deltaMovement

        var xMin = floor(playerX - 0.5).toInt()
        var xMax = floor(playerX + 0.5).toInt()
        var zMin = floor(playerZ - 0.5).toInt()
        var zMax = floor(playerZ + 0.5).toInt()
        var yMin = floor(playerY + 0.001).toInt()
        var yMax = floor(playerY + 1.001).toInt()

        if (velocity.x > 速度阈值) xMax += 1
        else if (velocity.x < -速度阈值) xMin -= 1

        if (velocity.z > 速度阈值) zMax += 1
        else if (velocity.z < -速度阈值) zMin -= 1

        if (velocity.y > 速度阈值) yMax += 1
        else if (velocity.y < -速度阈值) yMin -= 1

        return this.y in yMin..yMax && this.x in xMin..xMax && this.z in zMin..zMax
    }

// 窄通道挡路：玩家所在的高2宽1立柱，外加 XZ 速度最大方向(正东/南/西/北其一)前方一柱
val Vec3i.会阻挡玩家移动窄通道
    get() : Boolean {
        val player = mc.player ?: return false
        val pos = player.position()
        val footX = floor(pos.x).toInt()
        val footZ = floor(pos.z).toInt()
        val yFoot = floor(pos.y + 0.001).toInt()

        if (this.y != yFoot && this.y != yFoot + 1) return false      // 高2
        if (this.x == footX && this.z == footZ) return true           // 玩家立柱(宽1)

        val velocity = player.deltaMovement
        if (abs(velocity.x) < 速度阈值 && abs(velocity.z) < 速度阈值) return false
        val (dx, dz) = if (abs(velocity.x) >= abs(velocity.z))
            (if (velocity.x > 0) 1 else -1) to 0
        else
            0 to (if (velocity.z > 0) 1 else -1)
        return this.x == footX + dx && this.z == footZ + dz          // 前方一柱(宽1)
    }

fun Vec3i.在矿物通道范围内(OreBlocks: Set<Vec3i>, PlayerPos: Vec3i): Boolean {
    val playerY = PlayerPos.y
    val posY = this.y
    val posX = this.x
    val posZ = this.z
    for (Ore in OreBlocks) {
        val OreY = Ore.y
        val OreX = Ore.x
        val OreZ = Ore.z
        val minY = min(playerY, OreY)
        val maxY = max(playerY, OreY)

        var XZ半径 = abs(posY - OreY)+1
        if (OreY > playerY) {
            XZ半径 = when (XZ半径) {
                0 -> 1
                else -> 2
            }
        }

        if (posY in minY..maxY && abs(posX - OreX) <= XZ半径 && abs(posZ - OreZ) <= XZ半径) return true
    }
    return false
}

fun BlockPos.Render(event: Render3DEvent, colorScheme: ColorScheme, shapeMode: ShapeMode = ShapeMode.Lines) {
    val shape = mc.level?.let { it.getBlockState(this).getShape(it, this) } ?: return
    val x1: Double
    val y1: Double
    val z1: Double
    val x2: Double
    val y2: Double
    val z2: Double
    if (!shape.isEmpty) {
        x1 = x + shape.min(Direction.Axis.X)
        y1 = y + shape.min(Direction.Axis.Y)
        z1 = z + shape.min(Direction.Axis.Z)
        x2 = x + shape.max(Direction.Axis.X)
        y2 = y + shape.max(Direction.Axis.Y)
        z2 = z + shape.max(Direction.Axis.Z)
    } else {
        x1 = x.toDouble()
        y1 = y.toDouble()
        z1 = z.toDouble()
        x2 = (x + 1).toDouble()
        y2 = (y + 1).toDouble()
        z2 = (z + 1).toDouble()
    }
    event.renderer.box(x1, y1, z1, x2, y2, z2, colorScheme.sideColor, colorScheme.lineColor, shapeMode, 0)
}

val BlockPos.LightLevel get() = mc.level?.getBrightness(LightLayer.BLOCK, this) ?: 15

infix fun Vec3i.ManhattanDistanceTo(pos: Vec3i): Int = abs(x - pos.x) + abs(y - pos.y) + abs(z - pos.z)

val Vec3i.Center: Vec3 get() = Vec3.atCenterOf(this)

// 半径1闭邻域(自身+上下左右前后共7格)的完美Lee码探查点排布，密度1/7，每格恰好被一个探查点覆盖
val Vec3i.IsProb7: Boolean get() = (x + 2 * y + 3 * z).mod(7) == 0

// 半径1开邻域(仅上下左右前后6格、不含自身)的全完美码探查点排布，密度1/6，每格恰好被一个探查点覆盖
val Vec3i.IsProb6: Boolean get() = (x + 3 * y + 5 * z).mod(12) in 0..1

// 返回覆盖本格的IsProb7探查点(可能是自身)，每格必有且仅有一个
// 余数w=(x+2y+3z)%7，需让 w+d≡0，故 d=(-w)%7，对应唯一偏移
val Vec3i.belongProb7: Vec3i
    get() = when ((7 - (x + 2 * y + 3 * z).mod(7)) % 7) {
        0 -> this
        1 -> Vec3i(x + 1, y, z)
        2 -> Vec3i(x, y + 1, z)
        3 -> Vec3i(x, y, z + 1)
        4 -> Vec3i(x, y, z - 1)
        5 -> Vec3i(x, y - 1, z)
        else -> Vec3i(x - 1, y, z) // 6
    }

// 返回覆盖本格的IsProb6探查点(必为相邻格、非自身)，每格必有且仅有一个
// 余数w=(x+3y+5z)%12，邻居全奇(w偶)取目标1、全偶(w奇)取目标0，d=(目标-w)%12
val Vec3i.belongProb6: Vec3i
    get() {
        val w = (x + 3 * y + 5 * z).mod(12)
        val target = if (w % 2 == 0) 1 else 0
        return when ((target - w).mod(12)) {
            1 -> Vec3i(x + 1, y, z)
            3 -> Vec3i(x, y + 1, z)
            5 -> Vec3i(x, y, z + 1)
            7 -> Vec3i(x, y, z - 1)
            9 -> Vec3i(x, y - 1, z)
            else -> Vec3i(x - 1, y, z) // 11
        }
    }

val Vec3.isVisible
    get() = mc.player?.let {
        mc.level?.clip(
            ClipContext(
                it.EyeCenterPos, this, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, it
            )
        )?.type == HitResult.Type.MISS
    } ?: false

val Pair<Vec3i, Direction>.isVisible: Boolean
    get() = when (second) {
        Direction.UP -> FACE_OFFSETS_UP_OUT
        Direction.DOWN -> FACE_OFFSETS_DOWN_OUT
        Direction.NORTH -> FACE_OFFSETS_NORTH_OUT
        Direction.SOUTH -> FACE_OFFSETS_SOUTH_OUT
        Direction.EAST -> FACE_OFFSETS_EAST_OUT
        Direction.WEST -> FACE_OFFSETS_WEST_OUT
    }.any { (first.vec3d + it).isVisible }


fun Vec3i.PickAFaceFromPlayerPosition(player: LocalPlayer): Direction {
    val direction = this.Center.subtract(player.EyeCenterPos)

    val absX = abs(direction.x)
    val absY = abs(direction.y)
    val absZ = abs(direction.z)

    // Find which component is largest - this determines which face the ray hits
    if (absX > absY && absX > absZ) {
        // Ray hits either EAST or WEST face
        return if (direction.x > 0) Direction.WEST else Direction.EAST
    } else if (absY > absX && absY > absZ) {
        // Ray hits either UP or DOWN face
        return if (direction.y > 0) Direction.DOWN else Direction.UP
    }
    // Ray hits either SOUTH or NORTH face
    return if (direction.z > 0) Direction.NORTH else Direction.SOUTH
}

//region 方块锚点到方块面四顶点的偏移常量Vec3d[4]，稍微外扩0.05,用于方块的面穿墙可见性判断
private val FACE_OFFSETS_UP_OUT = arrayOf(
    Vec3(0.05, 1.05, 0.05),
    Vec3(0.95, 1.05, 0.05),
    Vec3(0.95, 1.05, 0.95),
    Vec3(0.05, 1.05, 0.95)
)

private val FACE_OFFSETS_DOWN_OUT = arrayOf(
    Vec3(0.05, -0.05, 0.05),
    Vec3(0.95, -0.05, 0.05),
    Vec3(0.95, -0.05, 0.95),
    Vec3(0.05, -0.05, 0.95)
)

private val FACE_OFFSETS_NORTH_OUT = arrayOf(
    Vec3(0.05, 0.05, -0.05),
    Vec3(0.95, 0.05, -0.05),
    Vec3(0.95, 0.95, -0.05),
    Vec3(0.05, 0.95, -0.05)
)

private val FACE_OFFSETS_SOUTH_OUT = arrayOf(
    Vec3(0.05, 0.05, 1.05),
    Vec3(0.95, 0.05, 1.05),
    Vec3(0.95, 0.95, 1.05),
    Vec3(0.05, 0.95, 1.05)
)

private val FACE_OFFSETS_EAST_OUT = arrayOf(
    Vec3(1.05, 0.05, 0.05),
    Vec3(1.05, 0.05, 0.95),
    Vec3(1.05, 0.95, 0.95),
    Vec3(1.05, 0.95, 0.05)
)

private val FACE_OFFSETS_WEST_OUT = arrayOf(
    Vec3(-0.05, 0.05, 0.05),
    Vec3(-0.05, 0.05, 0.95),
    Vec3(-0.05, 0.95, 0.95),
    Vec3(-0.05, 0.95, 0.05)
)

//endregion
