package com.kkllffaa.meteor_litematica_printer.mixins

import com.kkllffaa.meteor_litematica_printer.Functions.*
import com.kkllffaa.meteor_litematica_printer.Modules.Tools.PickUpEverything
import com.mojang.authlib.GameProfile
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LocalPlayer::class, priority = 1000 - 1)
open class LocalPlayerMixin(world: ClientLevel, profile: GameProfile) :
    AbstractClientPlayer(world, profile) {

    @Shadow
    @JvmField
    val connection: ClientPacketListener? = null

    @Inject(method = ["openTextEdit"], at = [At("HEAD")], cancellable = true)
    private fun openTextEdit(sign: SignBlockEntity, front: Boolean, ci: CallbackInfo) {
        getTargetSignEntity(sign)?.let { signBlockEntity ->
            val targetText = signBlockEntity.getText(front)
            val lines = (0..3).map { getFormattedLine(targetText.getMessage(it, false)) }

            val packet = ServerboundSignUpdatePacket(
                sign.blockPos,
                front,
                lines[0],
                lines[1],
                lines[2],
                lines[3]
            )
            connection?.send(packet)
            ci.cancel()
        }
    }

    /**
     * 拦截 Q 丢弃：若选中的是 [PickUpEverything] 的假物品，则在客户端丢回地上并取消原版逻辑，
     * 绝不向服务端发 DROP 包(否则会触发背包重新同步把假物品抹掉)。
     */
    @Inject(method = ["drop(Z)Z"], at = [At("HEAD")], cancellable = true)
    private fun onDrop(all: Boolean, cir: CallbackInfoReturnable<Boolean>) {
        if (PickUpEverything.onClientDrop(this, all)) cir.returnValue = true
    }

    @Unique
    private fun getTargetSignEntity(sign: SignBlockEntity): SignBlockEntity? {
        val worldSchematic = SchematicWorldHandler.getSchematicWorld() ?: return null
        return worldSchematic.getBlockEntity(sign.blockPos) as? SignBlockEntity
    }
}
