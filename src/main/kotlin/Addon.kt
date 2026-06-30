package com.kkllffaa.meteor_litematica_printer

import com.kkllffaa.meteor_litematica_printer.Commands.NetherCrackerCommand
import com.kkllffaa.meteor_litematica_printer.crud.atomic.*
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSource
import com.kkllffaa.meteor_litematica_printer.crud.engine.Executor
import com.kkllffaa.meteor_litematica_printer.Modules.Tools.SlotClickGuard
import com.kkllffaa.meteor_litematica_printer.crud.source.*
import com.kkllffaa.meteor_litematica_printer.Modules.Debug
import meteordevelopment.meteorclient.systems.modules.Module
import com.kkllffaa.meteor_litematica_printer.Modules.Tools.*
import com.kkllffaa.meteor_litematica_printer.Modules.Tools.OnlyESP.ItemFinder
import com.kkllffaa.meteor_litematica_printer.swarms.brain.SwarmBrain
import com.kkllffaa.meteor_litematica_printer.swarms.worker.SwarmWorker
import com.kkllffaa.meteor_litematica_printer.WorldLoadTracker
import meteordevelopment.meteorclient.addons.MeteorAddon
import meteordevelopment.meteorclient.commands.Commands
import meteordevelopment.meteorclient.systems.modules.Category
import meteordevelopment.meteorclient.systems.modules.Modules
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class Addon : MeteorAddon() {
    override fun onInitialize() {
        // 常开原子层：客观约束 + 基本动作实现
        Modules.get().add(CommonSettings)
        Modules.get().add(PlaceSettings)
        Modules.get().add(InteractSettings)
        Modules.get().add(ContainerSettings)
        Modules.get().add(BreakSettings)
        Modules.get().add(SwapSettings)
        Modules.get().add(CommandSetting)

        // 常开执行引擎：合并各来源诉求并落地执行
        Modules.get().add(Executor)

        // 主观需求来源：只声明"哪个 pos 应该什么样"，注册即喂给 Executor
        registerSource(ProjectionBuilder)
        registerSource(DeleterPs1())
        registerSource(DeleterPs2())
        registerSource(DeleterPs3())
        registerSource(DeleterPs4())
        registerSource(DeleterPs5())

        Modules.get().add(AutoSwarm)
        Modules.get().add(AutoFix)
        Modules.get().add(AutoRenew)
        Modules.get().add(AutoLogin)
        Modules.get().add(AutoTool)
        Modules.get().add(HangUp)
        Modules.get().add(SwingHand)
        Modules.get().add(YawSnap)
        Modules.get().add(EveryCharIsValid)
        Modules.get().add(PickUpEverything)
        Modules.get().add(Hello)
        Modules.get().add(ItemFinder)
        Modules.get().add(Parkour)
        Modules.get().add(ChatLogger)
        Modules.get().add(ShopLimiter)
        Modules.get().add(MovePacketLogger)
        Modules.get().add(BetterThirdPerson)
        Modules.get().add(CopyItemName)
        Modules.get().add(CheeseCaveFinder)
        Modules.get().add(SwarmBrain)
        Modules.get().add(SwarmWorker)
        Modules.get().add(SlotClickGuard)
        Modules.get().add(IgnoreBorders)
        Modules.get().add(TabListNotifier)

        // 统计地图加载次数
        WorldLoadTracker.install()
        // https://github.com/19MisterX98/Nether_Bedrock_Cracker
        Commands.add(NetherCrackerCommand)

        Modules.get().add(Debug)
    }

    /** 既登记为模块、又把它接到 [Executor] 上的需求来源。 */
    private fun <T> registerSource(source: T) where T : Module, T : DemandSource {
        Modules.get().add(source)
        Executor.register(source)
    }

    override fun getPackage(): String = "com.kkllffaa.meteor_litematica_printer"

    override fun onRegisterCategories() {
        Modules.registerCategory(TOOLS)
        Modules.registerCategory(CRUD)
        Modules.registerCategory(SettingsForCRUD)
    }

    companion object {
        @JvmField
        val CRUD: Category = Category("CRUD" ){ItemStack(Items.PINK_CARPET)}

        @JvmField
        val SettingsForCRUD = Category("SetForCRUD" ){ItemStack(Items.PINK_CARPET)}

        @JvmField
        val TOOLS = Category("EXTools"){ItemStack(Items.PINK_CARPET) }
    }
}
