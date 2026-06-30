package com.kkllffaa.meteor_litematica_printer.crud.atomic

import com.kkllffaa.meteor_litematica_printer.Addon
import com.kkllffaa.meteor_litematica_printer.Functions.*
import meteordevelopment.meteorclient.settings.BlockListSetting
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.settings.Setting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.world.BlockUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.ComparatorMode
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object InteractSettings : Module(Addon.SettingsForCRUD, "Interact", "Module to configure AtomicSettings.") {
    override fun toggle() {
        if (isActive) return
        super.toggle()
    }

    init {
        this.toggle()
    }

    private val sgGeneral = settings.defaultGroup
    private val OnlySiting: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("Only-Siting")
            .description("玩家处于坐下状态时允许放置")
            .defaultValue(false)
            .build()
    )
    private val swingMode: Setting<ActionMode> = sgGeneral.add(
        EnumSetting.Builder<ActionMode>()
            .name("swing-hand")
            .description("swing hand post interact.")
            .defaultValue(ActionMode.None)
            .build()
    )

    private val FaceBy: Setting<SafetyFace> = sgGeneral.add(
        EnumSetting.Builder<SafetyFace>()
            .name("interact-face-by")
            .description("Determines which face of the block to interact with.")
            .defaultValue(SafetyFace.PlayerPosition)
            .build()
    )

    private val onlyInteractOnLook: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("only-interact-on-look-the-face")
            .description("Only interact with blocks when looking at the face to interact with.")
            .defaultValue(false)
            .build()
    )

    private val stateBlocks: Setting<MutableList<Block>> = sgGeneral.add(
        BlockListSetting.Builder()
            .name("state-blocks")
            .description("Blocks that need interaction to adjust their state.")
            .defaultValue(
                Blocks.REPEATER, // 中继器
                Blocks.COMPARATOR, // 比较器
                Blocks.NOTE_BLOCK, // 音符盒
                Blocks.LEVER,   // 拉杆
                Blocks.DAYLIGHT_DETECTOR, // 日光传感器
                *(活板门 - Blocks.IRON_TRAPDOOR).toTypedArray(),
                *(门 - Blocks.IRON_DOOR).toTypedArray(),
                *栅栏门.toTypedArray(),
            )
            .build()
    )

    fun TryInteractBlock(blockPos: BlockPos, count: Int = 1): Int {
        val player = mc.player ?: return 0
        if (OnlySiting.get() && !player.isPassenger) return 0
        if (player.isShiftKeyDown || !blockPos.canTouch) {
            return 0
        }
        val pos = if (blockPos is BlockPos.MutableBlockPos) BlockPos(blockPos) else blockPos
        val direction = when (FaceBy.get()) {
            SafetyFace.PlayerRotation -> BlockUtils.getDirection(pos)
            SafetyFace.PlayerPosition -> pos.PickAFaceFromPlayerPosition(player)
        }
        if (onlyInteractOnLook.get() && !player.RotationInTheFaceOfBlock(pos, direction)) {
            return 0
        }
        val interactionManager = mc.gameMode ?: return 0
        val hitPos = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val blockHitResult = BlockHitResult(hitPos, direction, pos, false)
        for (i in 0..<count) {
            val result = interactionManager.useItemOn(player, InteractionHand.MAIN_HAND, blockHitResult)
            if (!result.consumesAction()) {
                warning("Interaction not accepted at $pos, result: $result")
                return i
            }
            player.swing(InteractionHand.MAIN_HAND,swingMode.get())
        }
        return count
    }


    fun calculateRequiredInteractions(targetState: BlockState, currentState: BlockState): Int {
        val currentblock = currentState.block
        if (currentblock !== targetState.block || currentblock !in stateBlocks.get()) {
            return 0
        }

        // 音符盒
        if (currentblock is NoteBlock) {
            val currentNote = currentState.getValue<Int>(BlockStateProperties.NOTE)
            val targetNote = targetState.getValue<Int>(BlockStateProperties.NOTE)

            // Note blocks cycle through 0-24 (25 states)
            val diff = (targetNote - currentNote + 25) % 25
            return diff
        }

        // 中继器
        if (currentblock is RepeaterBlock) {
            val currentDelay = currentState.getValue<Int>(BlockStateProperties.DELAY)
            val targetDelay = targetState.getValue<Int>(BlockStateProperties.DELAY)

            // Repeaters cycle through 1-4 (4 states)
            val diff = (targetDelay - currentDelay + 4) % 4
            return diff
        }

        // 比较器
        if (currentblock is ComparatorBlock) {
            return if (currentState.getValue<ComparatorMode>(BlockStateProperties.MODE_COMPARATOR)
                == targetState.getValue<ComparatorMode>(BlockStateProperties.MODE_COMPARATOR)
            )
                0
            else
                1
        }

        // 光传感器
        if (currentblock is DaylightDetectorBlock) {
            return if (currentState.getValue<Boolean>(BlockStateProperties.INVERTED) == targetState.getValue<Boolean>(
                    BlockStateProperties.INVERTED
                )
            ) 0 else 1
        }

        // 拉杆
        if (currentblock is LeverBlock) {
            return if (currentState.getValue<Boolean>(BlockStateProperties.POWERED) == targetState.getValue<Boolean>(
                    BlockStateProperties.POWERED
                )
            ) 0 else 1
        }

        // 栅栏门 活板门 门
        if (currentState.hasProperty(BlockStateProperties.OPEN)) {
            return if (currentState.getValue<Boolean>(BlockStateProperties.OPEN) == targetState.getValue<Boolean>(
                    BlockStateProperties.OPEN
                )
            ) 0 else 1
        }

        return 0 // 未知类型或不可交互类型
    }

}
