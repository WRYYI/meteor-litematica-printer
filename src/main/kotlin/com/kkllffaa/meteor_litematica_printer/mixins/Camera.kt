package com.kkllffaa.meteor_litematica_printer.mixins

import com.kkllffaa.meteor_litematica_printer.crud.atomic.CommonSettings
import com.llamalad7.mixinextras.sugar.Local
import meteordevelopment.meteorclient.MeteorClient
import meteordevelopment.meteorclient.systems.modules.Modules
import meteordevelopment.meteorclient.systems.modules.render.Freecam
import net.minecraft.client.Camera
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArgs
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.invoke.arg.Args
import kotlin.math.abs

@Mixin(Entity::class, priority = 1001)
abstract class EntityMixin {

    @Inject(method = ["turn"], at = [At("HEAD")], cancellable = true)
    private fun turn(cursorDeltaX: Double, cursorDeltaY: Double, ci: CallbackInfo) {
        if (this as Any !== MeteorClient.mc.player) return
        if (CommonSettings.OnlyRotateCam.get() && !(Modules.get().get(Freecam::class.java)?.isActive == true)) {
            CommonSettings.cameraYaw += (cursorDeltaX / 8F).toFloat()
            CommonSettings.cameraPitch += (cursorDeltaY / 8F).toFloat()

            if (abs(CommonSettings.cameraPitch) > 90.0f) CommonSettings.cameraPitch =
                if (CommonSettings.cameraPitch > 0.0f) 90.0f else -90.0f
            ci.cancel()
        }
    }
}


@Mixin(Camera::class, priority = 1001)
abstract class CameraMixin {

    @ModifyArgs(
        method = ["alignWithEntity"],
        at = At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V")
    )
    private fun alignWithEntity(args: Args, @Local(argsOnly = true) tickDelta: Float) {
        if (CommonSettings.OnlyRotateCam.get() && !(Modules.get().get(Freecam::class.java)?.isActive == true)) {
            args.set<Float?>(0, CommonSettings.cameraYaw)
            args.set<Float?>(1, CommonSettings.cameraPitch)
        }
    }
}
