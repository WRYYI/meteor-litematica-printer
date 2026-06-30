package com.kkllffaa.meteor_litematica_printer.mixins


import com.kkllffaa.meteor_litematica_printer.crud.atomic.CommonSettings
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo


@Mixin(LavaFogEnvironment::class)
class LavaFogModifierMixin {

    @Inject(method = ["setupFog"], at = [At("RETURN")])
    private fun setupFog(
        fog: FogData,
        camera: Camera,
        level: ClientLevel?,
        renderDistance: Float,
        deltaTracker: DeltaTracker?,
        ci: CallbackInfo
    ) {
        if (CommonSettings.NoFogInLava.get()) {
            fog.environmentalStart = -8.0f
            fog.environmentalEnd = renderDistance * 0.5f
            fog.skyEnd = fog.environmentalEnd
            fog.cloudEnd = fog.environmentalEnd
        }
    }

}
