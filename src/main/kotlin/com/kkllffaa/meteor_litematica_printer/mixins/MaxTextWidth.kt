package com.kkllffaa.meteor_litematica_printer.mixins

import com.kkllffaa.meteor_litematica_printer.crud.atomic.CommonSettings
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import meteordevelopment.meteorclient.MeteorClient.mc
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.HangingSignBlockEntity
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.client.gui.components.EditBox
import net.minecraft.world.inventory.AnvilMenu

@Mixin(SignBlockEntity::class, HangingSignBlockEntity::class)
class SignBlockEntityMixin {
    @Inject(method = ["getMaxTextLineWidth"], at = [At("RETURN")], cancellable = true)
    private fun getMaxTextLineWidth(ci: CallbackInfoReturnable<Int>) {
        val customWidth = CommonSettings.MaxTextWidth.get()
        if (customWidth > 0 && mc.screen is AbstractSignEditScreen) {
            ci.returnValue = customWidth
        }
    }
}


@Mixin(AnvilMenu::class)
class AnvilScreenHandlerMixin {

    private companion object {
        @Inject(method = ["validateName"], at = [At("HEAD")], cancellable = true)
        @JvmStatic
        private fun validateName(name: String, ci: CallbackInfoReturnable<String?>) {
            ci.returnValue = name
        }
    }

}


@Mixin(EditBox::class)
class TextFieldWidgetMixin {
    @Shadow
    private var maxLength: Int = 0

    @Inject(method = ["getMaxLength"], at = [At("HEAD")], cancellable = true)
    private fun getMaxLength(ci: CallbackInfoReturnable<Int>) {
        val len = CommonSettings.MaxTextWidth.get()
        if (len > 0) {
            this.maxLength = len
            ci.returnValue = len
        }
    }

    @Inject(method = ["setMaxLength"], at = [At("HEAD")], cancellable = true)
    private fun setMaxLength(maxLength: Int, ci: CallbackInfo) {
        val len = CommonSettings.MaxTextWidth.get()
        if (len > 0) {
            this.maxLength = len
            ci.cancel()
        }
    }
}


