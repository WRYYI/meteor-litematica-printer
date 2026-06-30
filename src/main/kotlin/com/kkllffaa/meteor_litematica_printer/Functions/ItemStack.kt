package com.kkllffaa.meteor_litematica_printer.Functions

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData


fun ItemStack.物品及非耐久属性全部相同于(other: ItemStack?): Boolean {
    if (other == null) return false
    if (this === other) return true
    if (!this.`is`(other.item)) return false
    if (this.isEmpty && other.isEmpty) return true

    val thisComponents = this.components
    val otherComponents = other.components

    val allTypes = buildSet {
        thisComponents.keySet().forEach { add(it) }
        otherComponents.keySet().forEach { add(it) }
    }

    for (type in allTypes) {
        if (type == DataComponents.DAMAGE) continue

        val thisValue = thisComponents.get(type)
        val otherValue = otherComponents.get(type)

        if (thisValue != otherValue) {
            return false
        }
    }

    return true
}


//region 客户端"假物品"标记 —— 供 PickUpEverything 假拾取使用
//
// 假拾取把掉落物深拷贝进背包时，只在 custom data 里**追加**这一个布尔键，其余 NBT 一律不动；
// Q 丢弃前再 clearFakePickup()，物品就和原版完全一致、服务端无从分辨真假。

private const val FAKE_PICKUP_KEY = "PickUpEverythingFake"

/** custom data 是否带有假物品标记。空物品恒为 false。 */
val ItemStack.isFakePickup: Boolean
    get() = get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains(FAKE_PICKUP_KEY) == true

/** 追加假物品标记，不触碰其余 custom data。 */
fun ItemStack.markFakePickup() {
    CustomData.update(DataComponents.CUSTOM_DATA, this) { it.putBoolean(FAKE_PICKUP_KEY, true) }
}

/** 移除假物品标记；custom data 因此变空时整个组件会被自动删除，完全还原。 */
fun ItemStack.clearFakePickup() {
    CustomData.update(DataComponents.CUSTOM_DATA, this) { it.remove(FAKE_PICKUP_KEY) }
}

//endregion
