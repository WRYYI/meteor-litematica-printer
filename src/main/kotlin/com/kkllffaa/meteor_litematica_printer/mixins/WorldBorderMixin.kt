package com.kkllffaa.meteor_litematica_printer.mixins


import com.kkllffaa.meteor_litematica_printer.Modules.Tools.IgnoreBorders
import com.kkllffaa.meteor_litematica_printer.crud.atomic.CommonSettings
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.world.level.border.WorldBorder
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable


@Mixin(WorldBorder::class)
class WorldBorderMixin {

    @Inject(method = ["isWithinBounds(DDD)Z"], at = [At("HEAD")], cancellable = true)
    private fun isWithinBounds(
        x: Double,
        z: Double,
        margin: Double,
        ci: CallbackInfoReturnable<Boolean>
    ) {
        if (IgnoreBorders.isActive) {
            ci.returnValue = true
        }
    }

}