package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.clearFakePickup
import com.kkllffaa.meteor_litematica_printer.Functions.isFakePickup
import com.kkllffaa.meteor_litematica_printer.Functions.markFakePickup
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.DoubleSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.orbit.EventHandler
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * **PickUpEverything** —— 纯客户端的“假拾取”(for fun)。
 *
 * 开启后把范围内的掉落物深拷贝进背包并播放原版拾取动画，但服务端对此一无所知：物品并不真的属于你。
 * 为此每个假物品只在 custom data 里**追加**一个隐藏布尔标记(见 [isFakePickup])，原有 NBT 一律不动。
 *
 * 由此产生三条约定：
 * - **吸取**：[onTick] 给范围内掉落物打标记、塞进背包，再用 [ClientboundTakeItemEntityPacket]
 *   复用原版收物动画/音效并移除地上的实体。
 * - **丢弃**：Q 被 [com.kkllffaa.meteor_litematica_printer.mixins.LocalPlayerMixin] 转交
 *   [onClientDrop] 拦下 —— 假物品绝不发 serverbound 包(否则服务端会重新同步背包、连标记带物品一并抹掉)，
 *   改为抹掉标记后在客户端还原成与原版一致的干净掉落物丢回地上。可反复丢/吸(模块开启时 Q 会被下一 tick
 *   立刻吸回)。
 * - **关闭后**：背包里残留的假物品依旧拦截 Q(不依赖模块开关)，且永远捡不起来 —— 服务端从不发收物包。
 */
object PickUpEverything : Module(
    Addon.TOOLS,
    "PickUpEverything",
    "把附近全部掉落物吸进背包(纯客户端假拾取)，Q 丢出会原样丢回地上，服务端全程不知情(for fun)。",
) {
    /** 原版丢弃物的拾取冷却(tick)，照搬 LivingEntity#createItemStackToDrop。 */
    private const val DROP_PICKUP_DELAY = 40

    /** 原版丢弃初速度的水平推力。 */
    private const val THROW_POWER = 0.3

    private val range: Setting<Double> = settings.defaultGroup.add(
        DoubleSetting.Builder()
            .name("range")
            .description("以玩家为中心向各方向外扩的扫描半径(格)。")
            .defaultValue(5.0)
            .min(1.0)
            .sliderRange(1.0, 16.0)
            .build()
    )

    /** 每 tick 把范围内还活着、非空的掉落物吸进背包。 */
    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        val connection = mc.connection ?: return
        val inventory = player.inventory

        val scanArea = player.boundingBox.inflate(range.get())
        val drops = level.getEntitiesOfClass(ItemEntity::class.java, scanArea) {
            it.isAlive && !it.item.isEmpty
        }

        for (drop in drops) {
            val count = drop.item.count
            val fake = drop.item.copy().apply { markFakePickup() }
            if (!inventory.add(fake)) continue // 背包满，先留在地上

            // 复用原版“收物”逻辑：拾取音效 + 飞向玩家的动画 + 移除掉落物实体
            connection.handleTakeItemEntity(ClientboundTakeItemEntityPacket(drop.id, player.id, count))
        }
    }

    /**
     * Q 丢弃拦截，由 MixinClientPlayerEntity 在 `LocalPlayer#drop` 的 HEAD 调用。
     *
     * 不依赖模块开关：即便已关闭，背包里残留的假物品也必须走这条路，否则原版会对一个服务端不存在的物品发
     * DROP 包，触发背包重新同步把它抹掉。
     *
     * @return `true` 表示这是假物品、已在客户端处理完毕，调用方应取消原版逻辑(不发包)。
     */
    fun onClientDrop(player: Player, all: Boolean): Boolean {
        val level = mc.level ?: return false
        val inventory = player.inventory
        val held = inventory.selectedItem
        if (!held.isFakePickup) return false // 空物品 / 真物品都交还原版

        val count = DropAmount.of(all).countIn(held)
        val dropped = held.copyWithCount(count).apply { clearFakePickup() } // 抹标记 → 与原版完全一致
        inventory.removeItem(inventory.selectedSlot, count)

        level.addEntity(player.forwardDrop(dropped))
        return true
    }

    /** 仿原版手感造一个朝准星方向丢出的掉落物(不加入世界，交给调用方)。 */
    private fun Player.forwardDrop(stack: ItemStack): ItemEntity =
        ItemEntity(level(), x, eyeY - 0.3, z, stack).apply {
            setPickUpDelay(DROP_PICKUP_DELAY)
            deltaMovement = throwVelocity()
        }

    /**
     * 照搬 `LivingEntity#createItemStackToDrop` 的初速度公式：沿准星方向推出，外加一点随机散布。
     * 客户端版 `drop` 是空实现(只挥手、不造实体)，所以这里只能自己算。
     */
    private fun Player.throwVelocity(): Vec3 {
        val yaw = Math.toRadians(yRot.toDouble())
        val pitch = Math.toRadians(xRot.toDouble())
        val cosPitch = cos(pitch)

        val spreadAngle = random.nextFloat() * (Math.PI * 2)
        val spreadPower = 0.02 * random.nextFloat()
        val verticalJitter = (random.nextFloat() - random.nextFloat()) * 0.1

        return Vec3(
            -sin(yaw) * cosPitch * THROW_POWER + cos(spreadAngle) * spreadPower,
            -sin(pitch) * THROW_POWER + 0.1 + verticalJitter,
            cos(yaw) * cosPitch * THROW_POWER + sin(spreadAngle) * spreadPower,
        )
    }

    /** 丢弃数量。`all` 来自原版 `drop(Z)`：Ctrl+Q 丢整组，Q 丢一个。 */
    private enum class DropAmount(val countIn: (ItemStack) -> Int) {
        Single({ 1 }),
        WholeStack({ it.count });

        companion object {
            fun of(all: Boolean): DropAmount = if (all) WholeStack else Single
        }
    }
}
