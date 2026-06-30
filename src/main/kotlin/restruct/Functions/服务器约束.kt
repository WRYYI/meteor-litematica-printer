package com.kkllffaa.meteor_litematica_printer.Functions

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import net.minecraft.util.Mth.frac
import java.util.function.BiFunction
import java.util.function.Function


// 适配器：从运作依赖 LocalPlayer 中抽出逻辑需要的 5 个标量
//fun 放置交互约束A点击坐标(player: LocalPlayer): Set<BlockPos> =
//    放置交互约束A点击坐标(
//        player.x, player.y, player.z,
//        player.yRot, player.xRot
//    )

// 纯函数：只接受不可变标量，无副作用，可单元测试
//fun 放置交互约束A点击坐标(
//    feetX: Double, feetY: Double, feetZ: Double,
//    yaw: Float, pitch: Float,
//    reach: Double = 5.0
//): Set<BlockPos> {
//    val originY = feetY + 0.4
//    val dir = 计算视角向量(yaw, pitch)
//
//    val from = Vec3(feetX, originY, feetZ)
//    val to = Vec3(feetX + dir.x * reach, originY + dir.y * reach, feetZ + dir.z * reach)
//
//    // Y+2 条件基于原始脚 Y 的小数部分（不是 +0.4 后的）
//    val yFrac = frac(feetY)
//    val includeY2 = yFrac > 0.38 && yFrac < 0.6
//
//    // 预估容量：~6 格穿透 × 3 层 ≈ 18，HashSet 0.75 负载下需 ≥24，给 32 留余量避免扩容
//    val out = HashSet<BlockPos>(32)
//
//    // traverseBlocks 内部用 null 表示"继续遍历"，但 Mojang Java 签名 T : Any。
//    // 用 BiFunction<...,Any?> 写 lambda 让 null 通过，再 unchecked-cast 给 Java 调用——类型擦除下运行期安全。
//    BlockGetter.traverseBlocks(
//        from, to, out,
//        BiFunction<MutableSet<BlockPos>, BlockPos, Any?> { set, pos ->
//            set.add(pos.immutable())
//            set.add(pos.above())
//            if (includeY2) set.add(pos.above(2))
//            null
//        } as BiFunction<MutableSet<BlockPos>, BlockPos, Any>,
//        Function<MutableSet<BlockPos>, Any?> { null }
//            as Function<MutableSet<BlockPos>, Any>
//    )
//    return out
//}
