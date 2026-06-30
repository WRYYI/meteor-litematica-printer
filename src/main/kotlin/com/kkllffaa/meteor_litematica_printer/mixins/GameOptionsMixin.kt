package com.kkllffaa.meteor_litematica_printer.mixins

import com.kkllffaa.meteor_litematica_printer.Modules.Tools.BetterThirdPerson
import net.minecraft.client.Options
import net.minecraft.client.CameraType
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Options::class)
class GameOptionsMixin {

    @Shadow
    private var cameraType: CameraType = CameraType.FIRST_PERSON

    @Inject(method = ["setCameraType"], at = [At("HEAD")], cancellable = true)
    private fun setCameraType(perspectiveIn: CameraType, ci: CallbackInfo) {
        if (!BetterThirdPerson.isActive) return

        val current = this.cameraType
        val newPerspective =
            if (BetterThirdPerson.禁用第三人称Front.get() && perspectiveIn == CameraType.THIRD_PERSON_FRONT) {
                when (current) {
                    CameraType.FIRST_PERSON -> CameraType.THIRD_PERSON_BACK
                    else -> CameraType.FIRST_PERSON
                }
            } else {
                perspectiveIn
            }
        if (current != newPerspective) {
            this.cameraType = newPerspective
            BetterThirdPerson.onPerspectiveChanged(newPerspective)
        }
        ci.cancel()
    }
}
