package com.kkllffaa.meteor_litematica_printer.mixins

import com.kkllffaa.meteor_litematica_printer.Modules.Tools.SlotClickGuard
import com.kkllffaa.meteor_litematica_printer.crud.atomic.BreakSettings
import com.kkllffaa.meteor_litematica_printer.crud.atomic.BreakSettings.destroyingInVanilla
import com.kkllffaa.meteor_litematica_printer.crud.atomic.BreakSettings.wasDestroyingInEngine
import com.kkllffaa.meteor_litematica_printer.crud.source.CursorProxy.playerWantDestroy
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Minecraft::class)
class MinecraftStartAttackMixin {
    @WrapWithCondition(
        method = ["startAttack"],
        at = [At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z")]
    )
    private fun cancelStartDestroyBlock(
        self: MultiPlayerGameMode,
        pos: BlockPos,
        direction: Direction
    ): Boolean {
        return false
    }
}

@Mixin(MultiPlayerGameMode::class)
interface MultiPlayerGameModeAccessor {
    @get:Accessor("destroyDelay")
    @set:Accessor("destroyDelay")
    var destroyDelay: Int
}

@Mixin(MultiPlayerGameMode::class)
class MultiPlayerGameModeMixin {
    @Shadow
    private var isDestroying: Boolean = false
    @WrapOperation(
        method = ["continueDestroyBlock"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;sameDestroyTarget(Lnet/minecraft/core/BlockPos;)Z"
        )]
    )
    private fun wrapSameDestroyTarget(
        self: MultiPlayerGameMode,
        pos: BlockPos,
        original: Operation<Boolean>
    ): Boolean {
        return isDestroying && original.call(self, pos)
    }


    @Inject(method = ["stopDestroyBlock"], at = [At("HEAD")], cancellable = true)
    private fun onStopDestroyBlock(ci: CallbackInfo) {
        if (BreakSettings.destroyingInEngine) ci.cancel()
    }

    @Inject(method = ["continueDestroyBlock"], at = [At("HEAD")], cancellable = true)
    private fun onContinueDestroyBlock(pos: BlockPos, direction: Direction, cir: CallbackInfoReturnable<Boolean>) {
        if (!BreakSettings.destroyingFromEngine) {
            playerWantDestroy(pos)
            if(BreakSettings.destroyingInEngine) {
                cir.cancel()
                return
            }
            destroyingInVanilla=true
        }
    }

    /** 每个容器点击出网前，先让 [SlotClickGuard] 静止，保证服务器认可这次点击。 */
    @Inject(method = ["handleContainerInput"], at = [At("HEAD")])
    private fun onContainerClick(
        containerId: Int,
        slotNum: Int,
        buttonNum: Int,
        containerInput: ContainerInput,
        player: Player,
        ci: CallbackInfo,
    ) {
        SlotClickGuard.freezeBeforeClick()
    }
}
