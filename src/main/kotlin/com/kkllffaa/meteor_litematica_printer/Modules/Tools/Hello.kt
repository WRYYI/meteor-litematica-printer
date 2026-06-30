package com.kkllffaa.meteor_litematica_printer.Modules.Tools


import com.kkllffaa.meteor_litematica_printer.crud.atomic.CommonSettings
import com.kkllffaa.meteor_litematica_printer.Functions.*
import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.utils.misc.input.Input
import meteordevelopment.orbit.EventHandler
import net.minecraft.world.InteractionHand
import kotlin.random.Random
import kotlin.math.abs
import net.minecraft.client.CameraType
import net.minecraft.util.Mth

object Hello : Module(Addon.TOOLS, "Hello", "Say hello via showing your friends you're crazy :D") {
    init {
        toggleOnBindRelease = true
    }

    private val sgGeneral = settings.defaultGroup
    private val RotationtickDelay = sgGeneral.add(
        IntSetting.Builder()
            .name("Rotation-tick-delay")
            .description("Server animation updates don't work well if you rotation every tick.")
            .defaultValue(3)
            .min(1).sliderMax(10)
            .build()
    )
    private val preferPerspectiveSetting = sgGeneral.add(
        EnumSetting.Builder<PreferPerspective>()
            .name("prefer-perspective")
            .description("Preferred perspective mode when activating the module.")
            .defaultValue(PreferPerspective.THIRD_PERSON_BACK)
            .build()
    )

    private var tickCounter = 0
    private var 视野模式OnActivate: CameraType? = null
    private var wasOnlyRotateCam = false
    private var wasRotation = Rotation(0F, 0F)
    override fun onActivate() {
        val player = mc.player ?: run {
            this.toggle()
            return
        }
        wasRotation = Rotation(player.yRot, player.xRot)
        wasOnlyRotateCam = CommonSettings.OnlyRotateCam.get()
        if (!wasOnlyRotateCam) CommonSettings.OnlyRotateCam.set(true)


        tickCounter = 0
        视野模式OnActivate = mc.options.cameraType
        when (preferPerspectiveSetting.get()) {
            PreferPerspective.NONE -> {}
            PreferPerspective.FIRST_PERSON -> mc.options.setCameraType(CameraType.FIRST_PERSON)
            PreferPerspective.THIRD_PERSON_BACK -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK)
        }
    }

    override fun onDeactivate() {
        视野模式OnActivate?.let {
            if (preferPerspectiveSetting.get() != PreferPerspective.NONE) mc.options.setCameraType(it)
        }
        mc.player?.let {
            it.setYRot(wasRotation.yaw)
            it.setXRot(wasRotation.pitch)
        }
        if (!wasOnlyRotateCam) CommonSettings.OnlyRotateCam.set(false)
        恢复按键到物理状态(
            mc.options.keyShift,
            mc.options.keyUp,
            mc.options.keyDown,
            mc.options.keyRight,
            mc.options.keyLeft
        )
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        val player = mc.player ?: return
        if (tickCounter % RotationtickDelay.get() == 0) {
            var yaw = Random.nextFloat() * 360f
            while (abs(Mth.wrapDegrees((player.yRot - yaw))) < 130F) {
                yaw = Random.nextFloat() * 360f
            }
            player.setYRot(yaw)
            var pitch = (Random.nextFloat() - 0.5f) * 179.998f
            while (abs(player.xRot - pitch) < 45F) {
                pitch = (Random.nextFloat() - 0.5f) * 179.998f
            }
            player.setXRot(pitch)
        }
        mc.options.keyShift.setDown(!mc.options.keyShift.isDown)
        if (tickCounter % 4 == 0) {
            player.swing(if (tickCounter % 2 == 0) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND)
        }
        tickCounter++

        val yawDiff = Math.toRadians((CommonSettings.cameraYaw - player.yRot).toDouble())
        val cos = kotlin.math.cos(yawDiff).toFloat()
        val sin = kotlin.math.sin(yawDiff).toFloat()

        val intentForward =
            (if (Input.isPressed(mc.options.keyUp)) 1f else 0f) - (if (Input.isPressed(mc.options.keyDown)) 1f else 0f)
        val intentRight =
            (if (Input.isPressed(mc.options.keyRight)) 1f else 0f) - (if (Input.isPressed(mc.options.keyLeft)) 1f else 0f)

        val actualForward = intentForward * cos - intentRight * sin
        val actualRight = intentForward * sin + intentRight * cos

        mc.options.keyUp.setDown(actualForward > 0.1f)
        mc.options.keyDown.setDown(actualForward < -0.1f)
        mc.options.keyRight.setDown(actualRight > 0.1f)
        mc.options.keyLeft.setDown(actualRight < -0.1f)
    }
}
