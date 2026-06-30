package com.kkllffaa.meteor_litematica_printer.crud.atomic

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.物品及非耐久属性全部相同于
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.player.InvUtils
import meteordevelopment.meteorclient.utils.player.SlotUtils
import meteordevelopment.orbit.EventHandler
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 换物的唯一入口——所有来源（放置 / 破坏 / 交互）想拿某物或某槽，都走这里。
 *
 * 它只做三件互相独立、各由一个倒计时驱动的事：
 *  1. **切到目标**：物品在主背包就先借一个快捷栏槽 [borrowItem] 再选中 [selectHotbar]；已在快捷栏直接选中。
 *  2. **回切选框** [restoreSelectedSlot]：选框停留 [selectBackDelay] tick 后切回玩家原来的选框（玩家手动换过则放手）。
 *  3. **归还借物** [restoreAllBorrowed]：借入的物品停留 [freeItemsDelay] tick 后送回原位（玩家手动换过则放手）。
 *
 * 换物产生的容器点击包要被服务器认可，需要玩家静止——这一层与本模块解耦，统一交给
 * `crud.net.SlotClickGuard`（它在所有容器点击处兜底，无须本模块过问移动与输入包时序）。
 *
 * 槽位的取用顺序（[claimHotbarSlot]）：空快捷栏槽 → 从未借用过的槽 → 最久未用的槽（[HotbarMru]）。
 */
object SwapSettings : Module(Addon.SettingsForCRUD, "Swap", "Module to configure AtomicSettings.") {
    override fun toggle() { if (!isActive) super.toggle() }
    init { toggle() }

    // region Settings
    private val sgGeneral = settings.defaultGroup

    private val useSlots = sgGeneral.add(IntSetting.Builder()
        .name("use-slots")
        .description("How many hotbar slots auto-swap may borrow, counted down from slot 8.")
        .defaultValue(8).range(1, 9).sliderRange(1, 9).build())

    private val selectBackDelay = sgGeneral.add(IntSetting.Builder()
        .name("select-back-delay")
        .description("Ticks before the selected slot is restored.")
        .defaultValue(20).range(1, 100).build())

    private val freeItemsDelay = sgGeneral.add(IntSetting.Builder()
        .name("free-items-delay")
        .description("Ticks before borrowed items are returned to their original slot.")
        .defaultValue(20).range(1, 100).build())
    // endregion

    // region Runtime state（两个倒计时各司其职，互不耦合；借物状态合并到单一数组）
    private val slotRestore = Countdown()
    private val itemRestore = Countdown()

    private val mru = HotbarMru()
    private val borrowed = arrayOfNulls<Borrow>(9)

    /** 自动换物可借用的快捷栏槽位（含两端）：从 8 往下数 [useSlots] 个。 */
    private val allocatedHotbar: IntRange get() = (9 - useSlots.get())..8
    // endregion

    // region 回切 / 归还（窗口到点触发）
    @EventHandler
    private fun onPostTick(event: TickEvent.Post) {
        if (slotRestore.tick() == Phase.Expired) restoreSelectedSlot()
        if (itemRestore.tick() == Phase.Expired) restoreAllBorrowed()
    }

    /** 切回玩家原选框；若玩家在此期间手动改过选框，则放手不动。 */
    private fun restoreSelectedSlot() {
        if (InvUtils.previousSlot == -1) return
        val current = mc.player?.inventory?.selectedSlot ?: return
        if (current == mru.mostRecent && current != InvUtils.previousSlot) InvUtils.swapBack()
        else InvUtils.previousSlot = -1
    }

    private fun restoreAllBorrowed() {
        for (slot in allocatedHotbar) returnBorrowed(slot)
    }

    /** 把某快捷栏槽借入的物品送回背包；玩家手动换过该槽则放手。 */
    private fun returnBorrowed(hotbar: Int) {
        val borrow = borrowed[hotbar] ?: return
        val player = mc.player ?: return
        val current = player.inventory.getItem(hotbar)

        val stillOurItem = current.物品及非耐久属性全部相同于(borrow.lent)
        val notRestoredYet = !current.物品及非耐久属性全部相同于(borrow.original)
        if (stillOurItem && notRestoredYet) {
            val home = InvUtils.find({ borrow.original.物品及非耐久属性全部相同于(it) }, 9, 35)
                .takeIf { it.found() }
                ?: InvUtils.find({ borrow.original.物品及非耐久属性全部相同于(it) }, 0, 8)
            if (home.found()) InvUtils.quickSwap().fromId(hotbar).to(home.slot)
        }
        borrowed[hotbar] = null
    }
    // endregion

    // region 公开入口
    /** 切到指定槽位（快捷栏直接选中；主背包先借槽再选中）。 */
    fun switchTo(player: LocalPlayer, slot: Int): Boolean = when {
        SlotUtils.isHotbar(slot) -> selectHotbar(slot)
        SlotUtils.isMain(slot) -> bringToHotbar(player, slot)
        else -> false
    }

    /** 切到持有指定物品的槽位。 */
    fun switchTo(player: LocalPlayer, item: Item): Boolean {
        val recent = mru.mostRecent
        return when {
            player.mainHandItem.item === item -> selectHotbar(player.inventory.selectedSlot)
            player.inventory.getItem(recent).item === item -> selectHotbar(recent)
            player.abilities.instabuild -> giveInCreative(player, item)
            else -> {
                val found = InvUtils.find(item)
                when {
                    !found.found() -> false
                    found.isHotbar -> selectHotbar(found.slot)
                    found.isMain -> bringToHotbar(player, found.slot)
                    else -> false
                }
            }
        }
    }
    // endregion

    // region 槽位操作
    private fun bringToHotbar(player: LocalPlayer, mainSlot: Int): Boolean {
        val target = claimHotbarSlot(player)
        borrowItem(player, mainSlot, target)
        return selectHotbar(target)
    }

    /** 取一个可借用的快捷栏槽：空槽 → 从未借用 → 最久未用。 */
    private fun claimHotbarSlot(player: LocalPlayer): Int {
        val slots = allocatedHotbar
        return slots.lastOrNull { player.inventory.getItem(it).isEmpty }   // 空快捷栏槽（最高位优先）
            ?: slots.lastOrNull { it !in mru }                            // 从未借用过的槽
            ?: mru.oldestIn(slots)                                        // 最久未用
    }

    /** 把主背包物品换进某快捷栏槽，记下借据以备归还。首次借入才记原物，连环借入保留最初的原物。 */
    private fun borrowItem(player: LocalPlayer, fromMain: Int, toHotbar: Int) {
        val lent = player.inventory.getItem(fromMain)
        val original = borrowed[toHotbar]?.original ?: player.inventory.getItem(toHotbar)
        borrowed[toHotbar] = Borrow(original, lent)
        InvUtils.quickSwap().fromId(toHotbar).to(fromMain)
        itemRestore.arm(freeItemsDelay.get())
    }

    private fun selectHotbar(slot: Int): Boolean {
        val player = mc.player ?: return false
        if (player.inventory.selectedSlot != slot) InvUtils.swap(slot, true)
        mru.touch(slot)
        slotRestore.arm(selectBackDelay.get())
        itemRestore.arm(freeItemsDelay.get())
        return true
    }

    private fun giveInCreative(player: LocalPlayer, item: Item): Boolean {
        val slot = 8
        val stack = item.defaultInstance
        mc.connection?.send(ServerboundSetCreativeModeSlotPacket(36 + slot, stack))
        player.inventory.setItem(slot, stack)
        return selectHotbar(slot)
    }
    // endregion

    // region 内部类型
    private enum class Phase { Idle, Running, Expired }

    /** tick 倒计时：[arm] 装填，[tick] 每 tick 递减一次，跨过 0 的那一 tick 报 [Phase.Expired]。 */
    private class Countdown {
        private var remaining = 0
        fun arm(ticks: Int) { remaining = ticks }
        fun tick(): Phase {
            if (remaining <= 0) return Phase.Idle
            remaining--
            return if (remaining == 0) Phase.Expired else Phase.Running
        }
    }

    /** 快捷栏使用先后表（MRU）：插入序即使用序，末尾最近、开头最久。 */
    private class HotbarMru {
        private val order = linkedSetOf<Int>()
        val mostRecent: Int get() = order.lastOrNull() ?: 8
        operator fun contains(slot: Int) = slot in order
        fun touch(slot: Int) { order.remove(slot); order.add(slot) }
        fun oldestIn(range: IntRange): Int = order.first { it in range }
    }

    /** 一笔借据：把 [lent] 借进某快捷栏槽，待归还时还原 [original]。 */
    private class Borrow(val original: ItemStack, val lent: ItemStack)
    // endregion
}
