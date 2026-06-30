package com.kkllffaa.meteor_litematica_printer.mixins

import com.kkllffaa.meteor_litematica_printer.Modules.Tools.EveryCharIsValid
import net.minecraft.ChatFormatting
import net.minecraft.util.StringUtil
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * [EveryCharIsValid] glue for [StringUtil].
 *
 * Every handler short-circuits a validation routine while the module is active and is a no-op
 * otherwise, leaving vanilla behaviour intact. The gate is a single [EveryCharIsValid.isActive] read —
 * cheap enough for the per-character [isAllowedChatCharacter] path.
 */
@Mixin(StringUtil::class)
class StringUtilMixin {
    private companion object {
        /** Count whitespace-only text as non-blank so it passes "is this empty?" checks (e.g. blank names). */
        @JvmStatic
        @Inject(method = ["isBlank"], at = [At("HEAD")], cancellable = true)
        private fun allowWhitespaceText(text: String?, cir: CallbackInfoReturnable<Boolean>) {
            cir.returnValue = text.isNullOrEmpty()
        }

        /** Treat every code point as chat-legal. */
        @JvmStatic
        @Inject(method = ["isAllowedChatCharacter"], at = [At("HEAD")], cancellable = true)
        private fun allowEveryChatCharacter(codePoint: Int, cir: CallbackInfoReturnable<Boolean>) {
            if (EveryCharIsValid.isActive) cir.returnValue = true
        }

        /** Keep `§` color codes instead of stripping them. */
        @JvmStatic
        @Inject(method = ["stripColor"], at = [At("HEAD")], cancellable = true)
        private fun keepColorCodes(text: String?, cir: CallbackInfoReturnable<String?>) {
            if (EveryCharIsValid.isActive) cir.returnValue = text
        }
    }
}

/**
 * [EveryCharIsValid] glue for [ChatFormatting]: keep formatting codes instead of stripping them.
 */
@Mixin(ChatFormatting::class)
class ChatFormattingMixin {
    private companion object {
        @JvmStatic
        @Inject(method = ["stripFormatting"], at = [At("HEAD")], cancellable = true)
        private fun keepFormattingCodes(text: String?, cir: CallbackInfoReturnable<String?>) {
            if (EveryCharIsValid.isActive) cir.returnValue = text
        }
    }
}
