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
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.*
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

object PlaceSettings : Module(Addon.SettingsForCRUD, "Place", "Module to configure AtomicSettings.") {
    override fun toggle() {
        if (isActive) return
        super.toggle()
    }

    init {
        this.toggle()
    }

    private val sgGeneral = settings.defaultGroup
    private val sgDirectional = settings.createGroup("Directional Protection")
    private val sgClickFace = settings.createGroup("Click Face")
    private val sgNeighbor = settings.createGroup("Place On Neighbor Blocks")

    private val OnlySiting: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("Only-Siting")
            .description("玩家处于坐下状态时允许放置")
            .defaultValue(false)
            .build()
    )
    private val BlacklistforFullCube: Setting<Boolean> = sgNeighbor.add(
        BoolSetting.Builder()
            .name("Black-list-for-FullCube")
            .description("Enable blacklist for neighbor blocks.")
            .defaultValue(false)
            .build()
    )

    private val blacklist: Setting<MutableList<Block>> = sgNeighbor.add(
        BlockListSetting.Builder()
            .name("blacklist")
            .description("Blocks that cannot be placed against.(CollisionFullCube but cannot be clicked)")
            .defaultValue(
                //碰撞箱完整但是不能放的
                // *潜影盒.toTypedArray(),
            )
            .visible { BlacklistforFullCube.get() }
            .build()
    )

    private val enableAddList: Setting<Boolean> = sgNeighbor.add(
        BoolSetting.Builder()
            .name("enable-additional-list")
            .description("Enable additional list for neighbor blocks.")
            .defaultValue(false)
            .build()
    )

    private val addList: Setting<MutableList<Block>> = sgNeighbor.add(
        BlockListSetting.Builder()
            .name("additional-list")
            .description("Additional blocks allowed after collision box filtering.")
            .visible { enableAddList.get() }
            .build()
    )


    private val swingMode: Setting<ActionMode> = sgGeneral.add(
        EnumSetting.Builder<ActionMode>()
            .name("swing-hand")
            .description("swing hand post place.")
            .defaultValue(ActionMode.None)
            .build()
    )

    private val airPlace: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("air-place")
            .description("Allow placing in the air.")
            .defaultValue(true)
            .build()
    )
    private val airplaceBlacklist: Setting<MutableList<Block>> = sgGeneral.add(
        BlockListSetting.Builder()
            .name("airplace-blacklist")
            .description("Blocks that cannot be placed in airplace.")
            .defaultValue(
                Blocks.GRINDSTONE,// 砂轮
                *天花板H告示牌.toTypedArray(),
                *墙上H告示牌.toTypedArray(),

                *地面火把.toTypedArray(),
                *地面告示牌.toTypedArray(),
                *地面旗帜.toTypedArray(),
                *地面头颅.toTypedArray(),
                *墙上头颅.toTypedArray(),

                )
            .visible { airPlace.get() }
            .build()
    )

    private val placeThroughWall: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("Place Through Wall")
            .description("Allow the bot to place through walls.")
            .defaultValue(true)
            .build()
    )

    private val safetyPlaceFaceMode: Setting<SafetyFaceMode> = sgGeneral.add(
        EnumSetting.Builder<SafetyFaceMode>()
            .name("safetyPlace-mode")
            .description("Only place blocks on A safe faces.")
            .defaultValue(SafetyFaceMode.None)
            .build()
    )

    private val onlyPlaceOnLookFace: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("only-place-on-look-face")
            .description("Only place blocks on the face you are looking at direction")
            .defaultValue(false)
            .build()
    )

    private val dirtgrass: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("dirt-as-grass")
            .description("Use dirt instead of grass.")
            .defaultValue(false)
            .build()
    )

    val SignTextWithColor: Setting<SignColorMode> = sgGeneral.add(
        EnumSetting.Builder<SignColorMode>()
            .name("sign-text-with-color")
            .description("Use colored text for signs.")
            .defaultValue(SignColorMode.`§`)
            .build()
    )


    private val directionProtection: Setting<Boolean> = sgDirectional.add(
        BoolSetting.Builder()
            .name("direction-protection")
            .description("Only place directional blocks when player is facing the correct direction.")
            .defaultValue(true)
            .build()
    )

    private val YawForward: Setting<MutableList<Block>> = sgDirectional.add(
        BlockListSetting.Builder()
            .name("yaw-forward")
            .description("Blocks that should face the same direction as player.")
            .defaultValue(
                Blocks.LEVER, // 拉杆
                Blocks.CALIBRATED_SCULK_SENSOR, // 校准潜影传感器
                Blocks.OBSERVER,// 侦测器
                Blocks.GRINDSTONE,// 砂轮
                Blocks.CAMPFIRE, // 篝火
                Blocks.SOUL_CAMPFIRE, // 蓝篝火
                Blocks.BELL, // 钟
                Blocks.DECORATED_POT, // 装饰花盆
                *地面头颅.toTypedArray(),
                *按钮.toTypedArray(),
                *铁轨.toTypedArray(),
                *栅栏门.toTypedArray(),
                *楼梯.toTypedArray(),
                *门.toTypedArray(),
                *床.toTypedArray(),
            )
            .visible { directionProtection.get() }
            .build()
    )

    private val YawBackward: Setting<MutableList<Block>> = sgDirectional.add(
        BlockListSetting.Builder()
            .name("yaw-backward")
            .description("Blocks that should face to player.")
            .defaultValue(
                Blocks.REPEATER,// 中继器
                Blocks.COMPARATOR,// 比较器
                Blocks.TRIPWIRE_HOOK, // 绊线钩
                Blocks.LECTERN, // 讲台
                Blocks.PISTON, // 活塞
                Blocks.STICKY_PISTON, // 粘性活塞
                Blocks.DISPENSER,// 发射器
                Blocks.DROPPER,//投掷器
                Blocks.CRAFTER, //合成器
                Blocks.BARREL,// 木桶
                Blocks.CHISELED_BOOKSHELF, // 雕文书架
                Blocks.STONECUTTER,// 切石机
                Blocks.LOOM, //织布机
                Blocks.FURNACE, // 炉子
                Blocks.SMOKER, // 食物炉子
                Blocks.BLAST_FURNACE, // 矿炉子
                Blocks.SOUL_SOIL, // 失水恶魂
                Blocks.CARVED_PUMPKIN,   // 雕刻南瓜
                Blocks.JACK_O_LANTERN,   // 发光南瓜
                Blocks.BEE_NEST,// 蜂巢
                Blocks.BEEHIVE,// 蜂箱
                Blocks.BIG_DRIPLEAF,// 大滴水叶
                Blocks.END_PORTAL_FRAME, // 末地传送门框架
                Blocks.VAULT, // 宝库
                *地面旗帜.toTypedArray(),
                *墙上旗帜.toTypedArray(),
                *墙上头颅.toTypedArray(),
                *墙上火把.toTypedArray(),
                *墙上告示牌.toTypedArray(),
                *地面告示牌.toTypedArray(),
                *天花板H告示牌.toTypedArray(),
                *墙上H告示牌.toTypedArray(),
                *开合箱子.toTypedArray(),
                *三框展示架.toTypedArray(),
                *活板门.toTypedArray(),
                *铁轨.toTypedArray(),
                *栅栏门.toTypedArray(),
                *铜雕像.toTypedArray(),
            )
            .visible { directionProtection.get() }
            .build()
    )

    private val YawLeft: Setting<MutableList<Block>> = sgDirectional.add(
        BlockListSetting.Builder()
            .name("yaw-left")
            .description("Blocks that should face to the left of player.")
            .visible { directionProtection.get() }
            .build()
    )

    private val YawRight: Setting<MutableList<Block>> = sgDirectional.add(
        BlockListSetting.Builder()
            .name("yaw-right")
            .description("Blocks that should face to the right of player.")
            .defaultValue(*铁砧.toTypedArray())
            .visible { directionProtection.get() }
            .build()
    )

    private val PitchForward: Setting<MutableList<Block>> = sgDirectional.add(
        BlockListSetting.Builder()
            .name("pitch-forward")
            .description("Blocks that should face the same direction as player when UpDown")
            .defaultValue(
                Blocks.OBSERVER,// 侦测器
            )
            .visible { directionProtection.get() }
            .build()
    )

    private val PitchBackward: Setting<MutableList<Block>> = sgDirectional.add(
        BlockListSetting.Builder()
            .name("pitch-backward")
            .description("Blocks that should face to player when UpDown")
            .defaultValue(
                Blocks.PISTON, // 活塞
                Blocks.STICKY_PISTON, // 粘性活塞
                Blocks.DISPENSER,// 发射器
                Blocks.DROPPER,//投掷器
                Blocks.CRAFTER, //合成器
                Blocks.BARREL,// 木桶
            )
            .visible { directionProtection.get() }
            .build()
    )


    private val clickProtection: Setting<Boolean> = sgClickFace.add(
        BoolSetting.Builder()
            .name("click-protection")
            .description("Only place directional blocks when click-face is correct direction.")
            .defaultValue(true)
            .build()
    )

    private val freeFaceForDefaultTorch: Setting<Boolean> = sgClickFace.add(
        BoolSetting.Builder()
            .name("free-face-of-default-torch")
            .description("Allow placing default torches without precise placement.")
            .defaultValue(false)
            .visible { clickProtection.get() }
            .build()
    )

    private val clickForward: Setting<MutableList<Block>> = sgClickFace.add(
        BlockListSetting.Builder()
            .name("click-forward")
            .description("Blocks that should face the same direction as click face.")
            .defaultValue(
                Blocks.LADDER,   // 梯子
                Blocks.LEVER,   // 拉杆
                Blocks.GRINDSTONE,  // 砂轮
                Blocks.END_ROD, //末地烛 TODO:末地烛放在末地烛上反向特例
                Blocks.TRIPWIRE_HOOK, // 绊线钩
                *按钮.toTypedArray(),
                *墙上旗帜.toTypedArray(),
                *墙上头颅.toTypedArray(),
                *墙上火把.toTypedArray(),
                *墙上告示牌.toTypedArray(),
                *潜影盒.toTypedArray(),
                *活板门.toTypedArray(),
                *避雷针.toTypedArray(),

                *链.toTypedArray(),
                *原木log.toTypedArray(),
                *去皮原木log.toTypedArray(),
                *木块wood.toTypedArray(),
                *去皮木块wood.toTypedArray(),
                *个别AxisBlocks.toTypedArray(),
            )
            .visible { clickProtection.get() }
            .build()
    )

    private val clickBackward: Setting<MutableList<Block>> = sgClickFace.add(
        BlockListSetting.Builder()
            .name("click-backward")
            .description("Blocks that should face to click face(back).")
            .defaultValue(
                Blocks.BELL,  // 钟
                Blocks.HOPPER,    // 漏斗
                *链.toTypedArray(),
                *原木log.toTypedArray(),
                *去皮原木log.toTypedArray(),
                *木块wood.toTypedArray(),
                *去皮木块wood.toTypedArray(),
                *个别AxisBlocks.toTypedArray(),
            )
            .visible { clickProtection.get() }
            .build()
    )

    private val clickLeft: Setting<MutableList<Block>> = sgClickFace.add(
        BlockListSetting.Builder()
            .name("click-left")
            .description("Blocks for precise placement facing left.")
            .defaultValue(*墙上H告示牌.toTypedArray())
            .visible { clickProtection.get() }
            .build()
    )

    private val clickRight: Setting<MutableList<Block>> = sgClickFace.add(
        BlockListSetting.Builder()
            .name("click-right")
            .description("Blocks for precise placement facing right.")
            .defaultValue(*墙上H告示牌.toTypedArray())
            .visible { clickProtection.get() }
            .build()
    )


    private fun BlockState.isPlaceAllowedFromClickFace(clickFace: Direction): Boolean {
        //方块能判断方向
        val requiredDirection = this.ATagFaceOf6 ?: return true

        val block = this.block
        val inListForward = block in clickForward.get()
        val inListBackward = block in clickBackward.get()
        val inListLeft = block in clickLeft.get()
        val inListRight = block in clickRight.get()
        return !(inListForward || inListBackward || inListLeft || inListRight) || when (requiredDirection) {
            clickFace -> inListForward
            clickFace.opposite -> inListBackward
            clickFace.Left -> inListLeft
            clickFace.Right -> inListRight
            else -> false
        }
    }

    private val BlockState.isPlaceAllowedFromPlayerRotation: Boolean
        get() {
            val block = this.block
            val inListUp = block in PitchForward.get()
            val inListDown = block in PitchBackward.get()
            val inListForward = block in YawForward.get()
            val inListBackward = block in YawBackward.get()
            val inListLeft = block in YawLeft.get()
            val inListRight = block in YawRight.get()
            if (!(inListUp || inListDown || inListForward || inListBackward || inListLeft || inListRight)) return true

            if (hasProperty(BlockStateProperties.ROTATION_16)) {
                val YawInt16 = PlayerYawInt16 ?: return false
                val BlockInt16 = this.getValue(BlockStateProperties.ROTATION_16)
                return when (BlockInt16) {
                    YawInt16 -> inListForward
                    YawInt16.opposite -> inListBackward
                    YawInt16.Left -> inListLeft
                    YawInt16.Right -> inListRight
                    else -> false
                }
            }

            val requiredDirection = this.ATagFaceOf6 ?: return true
            val 六向砖 = inListUp || inListDown
            val playerPitchDirection = PlayerPitchDirection
            val playerYawDirection = PlayerYawDirection

            if (六向砖 && (playerPitchDirection == Direction.UP || playerPitchDirection == Direction.DOWN)) {
                if (!hasProperty(BlockStateProperties.ORIENTATION) || playerYawDirection?.let {
                        this.getValue(BlockStateProperties.ORIENTATION).name.endsWith(it.name)
                    } == true
                ) {
                    return when (requiredDirection) {
                        playerPitchDirection -> inListUp
                        playerPitchDirection.opposite -> inListDown
                        else -> false
                    }
                }
            } else if (playerYawDirection != null && !(六向砖 && playerPitchDirection == null)) {
                return when (requiredDirection) {
                    playerYawDirection -> inListForward
                    playerYawDirection.opposite -> inListBackward
                    playerYawDirection.Left -> inListLeft
                    playerYawDirection.Right -> inListRight
                    else -> false
                }
            }
            return false
        }


    private fun BlockState.canPlaceAgainst(neighbourPos: BlockPos, neighbourFace: Direction): Boolean {
        val world = mc.level ?: return false
        val player = mc.player ?: return false
        val neighbour = world.getBlockState(neighbourPos)
        val neighbourBlock = neighbour.block
        val neighbourisBlockCollisionFullCube by lazy { neighbour.isBlockCollisionFullCube }
        return !neighbour.isAir && neighbour.fluidState.isEmpty//有砖
            // 不出GUI
            && (!BlockUtils.isClickable(neighbourBlock) || player.isShiftKeyDown)
            // 特例
            && !(this.block is WallHangingSignBlock && !neighbourisBlockCollisionFullCube)
            // 不在额外黑名单
            && !(BlacklistforFullCube.get() && neighbourBlock in blacklist.get())
            // 在白名单组合
            && (neighbourisBlockCollisionFullCube
            || neighbourBlock === Blocks.GLASS
            || neighbourBlock is StainedGlassBlock
            || neighbourBlock is StairBlock
            || (enableAddList.get() && neighbourBlock in addList.get())
            //不会重叠的半砖
            || (neighbourBlock is SlabBlock
            && (this.block !== neighbour.block //类型不同不会融合
            || neighbour.getValue<SlabType>(SlabBlock.TYPE) == SlabType.DOUBLE //邻居双层不会融合
            || !(//同类型,邻居单层半砖 附带不会融合约束
            //从上向下放到半砖底 会融合
            (neighbour.getValue<SlabType>(SlabBlock.TYPE) == SlabType.BOTTOM && neighbourFace == Direction.UP)
                //从下向上放到半砖顶 会融合
                || (neighbour.getValue<SlabType>(SlabBlock.TYPE) == SlabType.TOP && neighbourFace == Direction.DOWN)
                //两半砖顶底不同时 从侧面放置 会融合
                || (neighbourFace != Direction.UP && neighbourFace != Direction.DOWN //侧面放置
                && neighbour.getValue<SlabType>(SlabBlock.TYPE) != this.getValue<SlabType>(SlabBlock.TYPE)) //顶底不同
            ))))
    }

    fun TryPlaceBlock(required: BlockState, pos: BlockPos, worldPoState: BlockState?): Boolean {
        val player = mc.player ?: return false
        if (OnlySiting.get() && !player.isPassenger) return false
        val world = mc.level ?: return false
        val block = required.block
        // 检查点
        if (required.isAir || block is LiquidBlock || !required.isMultiStructurePlacementAllowed) return false//无法放置的东西

        if (!Level.isInSpawnableBounds(pos) || !required.canSurvive(world, pos)) return false
        val worldPosState = worldPoState ?: world.getBlockState(pos)

        val 已经放好了: Boolean =
            worldPosState.block === block && when (block) {
                is SlabBlock if required.getValue(SlabBlock.TYPE) == SlabType.DOUBLE -> {
                    worldPosState.getValue<SlabType>(SlabBlock.TYPE) == SlabType.DOUBLE
                }

                is CandleBlock -> {
                    worldPosState.getValue<Int>(CandleBlock.CANDLES) >= required.getValue<Int>(CandleBlock.CANDLES)
                }

                is SeaPickleBlock -> {
                    worldPosState.getValue<Int>(SeaPickleBlock.PICKLES) >= required.getValue<Int>(SeaPickleBlock.PICKLES)
                }

                is TurtleEggBlock -> {
                    worldPosState.getValue<Int>(TurtleEggBlock.EGGS) >= required.getValue<Int>(TurtleEggBlock.EGGS)
                }

                else -> true
            }

        if (已经放好了) return false
        val 可替换 = worldPosState.canBeReplaced()
            || (block === worldPosState.block && (block is SlabBlock || block is CandleBlock || block is SeaPickleBlock || block is TurtleEggBlock))

        if (!可替换) return false

        // Check if intersects entities
        // if (player.boundingBox.intersects(Vec3d.of(pos), Vec3d.of(pos).add(1.0, 1.0, 1.0))) return false
        if (!world.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, CollisionContext.empty())) return false

        // 检查面
        val isPlaceAllowedFromPlayerRotation by lazy { required.isPlaceAllowedFromPlayerRotation }
        val posCenterVisible by lazy { pos.Center.isVisible }
        val enableAirPlace by lazy { airPlace.get() && block !in airplaceBlacklist.get() }
        val 铰链方向 by lazy {
            val facing = required.getValue<Direction>(DoorBlock.FACING)
            val 铰链位置 = required.getValue<DoorHingeSide>(DoorBlock.HINGE)
            when (铰链位置) {
                DoorHingeSide.LEFT -> facing.Left!!
                DoorHingeSide.RIGHT -> facing.Right!!
            }
        }

        val 铰链比对立多 by lazy {
            // 门高两格，计算铰链侧和对立侧各有多少个固体方块 (0-2)
            fun countSolidBlocks(dir: Direction): Int {
                var count = 0
                for (dy in 0..1) {
                    val checkPos = pos.relative(dir).above(dy)
                    val state = world.getBlockState(checkPos)
                    if (state.isRedstoneConductor(world, checkPos)) count++
                }
                return count
            }

            val 铰链侧方块数 = countSolidBlocks(铰链方向)
            val 对立侧方块数 = countSolidBlocks(铰链方向.opposite)
            铰链侧方块数 - 对立侧方块数
        }


        for (face in Direction.entries) {
            var tempHitPos = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            // 特殊砖:根据状态属性/点击面有 不同的 点击面保护和视角保护的变化/点击点偏移/面能不能点
            var disableDirectionProtection = false
            var disableFaceProtection = false
            var disableAirPlace = false
            if (block is WallTorchBlock || block is RedstoneWallTorchBlock) {//墙火把 面不同禁用不同保护/面不能点
                when (face) {
                    Direction.UP -> continue
                    Direction.DOWN -> disableFaceProtection = true
                    else -> disableDirectionProtection = true
                }
            } else if (block is TorchBlock || block is RedstoneTorchBlock) {// 直立式火把  面不能点
                if (!freeFaceForDefaultTorch.get() && face != Direction.UP) continue
            } else if (block is TrapDoorBlock) { //活板门 面不同禁用不同保护/点击点偏移/面不能点
                val blockHalf = required.getValue<Half>(BlockStateProperties.HALF)
                when {
                    (blockHalf == Half.TOP && face == Direction.UP)
                        || (blockHalf == Half.BOTTOM && face == Direction.DOWN) -> continue// 半砖类型会错误
                    face == Direction.UP || face == Direction.DOWN -> {
                        disableFaceProtection = true//方向依据玩家
                    }

                    else -> {
                        if (blockHalf == Half.TOP) {
                            tempHitPos = tempHitPos.add(0.0, 0.25, 0.0)
                        } else {
                            disableAirPlace = true
                            tempHitPos = tempHitPos.add(0.0, -0.25, 0.0)
                        }
                        disableDirectionProtection = true//侧面方向取决于点击面
                    }
                }
            } else if (block is StairBlock) { //楼梯 点击点偏移/面不能点
                val blockHalf = required.getValue<Half>(BlockStateProperties.HALF)
                when {
                    (blockHalf == Half.TOP && face == Direction.UP)
                        || (blockHalf == Half.BOTTOM && face == Direction.DOWN) -> continue// 半砖类型会错误
                    face == Direction.UP || face == Direction.DOWN -> {
                    }

                    else -> {//侧面设置半砖偏移
                        if (blockHalf == Half.TOP) {
                            tempHitPos = tempHitPos.add(0.0, 0.25, 0.0)
                        } else {
                            disableAirPlace = true
                            tempHitPos = tempHitPos.add(0.0, -0.25, 0.0)
                        }
                    }
                }
            } else if (block is SlabBlock) { //半砖 点击点偏移/面不能点
                val slabTpye = required.getValue<SlabType>(BlockStateProperties.SLAB_TYPE)
                when {
                    (slabTpye == SlabType.TOP && face == Direction.UP)
                        || (slabTpye == SlabType.BOTTOM && face == Direction.DOWN) -> continue // 半砖类型会错误
                    face == Direction.UP || face == Direction.DOWN -> {
                    }

                    else -> {//侧面设置半砖偏移
                        when (slabTpye) {
                            SlabType.TOP -> tempHitPos = tempHitPos.add(0.0, 0.25, 0.0)
                            SlabType.BOTTOM -> {
                                disableAirPlace = true
                                tempHitPos = tempHitPos.add(0.0, -0.25, 0.0)
                            }

                            SlabType.DOUBLE -> {}
                        }
                    }
                }
            } else if (block is WallHangingSignBlock) {//墙面悬挂告示牌 面不能点
                if (face == Direction.DOWN || face == Direction.UP) continue
            } else if (block is CeilingHangingSignBlock) {//天花板悬挂告示牌 面不能点
                if (face != Direction.DOWN) continue
                val attached = required.getValue<Boolean>(BlockStateProperties.ATTACHED)
                if (attached != player.isShiftKeyDown) continue
            } else if (block is WallSignBlock || block is WallSkullBlock || block is WallBannerBlock) {
                when (face) {
                    Direction.UP -> continue
                    Direction.DOWN -> {
                        if (block is WallSkullBlock) continue
                        disableFaceProtection = true // 方向取决于玩家
                    }

                    else -> {
                        disableDirectionProtection = true //方向取决于点击面
                    }
                }
            } else if (block is StandingSignBlock || block is SkullBlock || block is BannerBlock) {
                if (face != Direction.UP) continue
            } else if (required.hasProperty(BlockStateProperties.BELL_ATTACHMENT)) { //钟既 属性不同禁用不同保护/面不能点
                when (required.getValue<BellAttachType>(BlockStateProperties.BELL_ATTACHMENT)) {
                    BellAttachType.FLOOR -> {
                        if (face != Direction.UP) continue  // 只能放在邻居上方
                        disableFaceProtection = true  // 方向取决于玩家
                    }

                    BellAttachType.CEILING -> {
                        if (face != Direction.DOWN) continue  // 只能放在邻居下方
                        disableFaceProtection = true  // 方向取决于玩家
                    }

                    else -> {
                        if (face == Direction.UP || face == Direction.DOWN) continue  // 只能放在邻居四周
                        disableDirectionProtection = true//方向取决于点击面
                    }
                }
            } else if (required.hasProperty(BlockStateProperties.HANGING)) { //灯笼 面不能点
                if (required.getValue<Boolean>(BlockStateProperties.HANGING)) { // 吊着的
                    if (face != Direction.DOWN) continue  // 只能放在邻居下方
                    disableAirPlace = true
                } else {   // 不吊着的
                    if (face == Direction.DOWN) continue  // 不能放在邻居下方
                }
            } else if (required.hasProperty(BlockStateProperties.ATTACH_FACE)) { //拉杆 按钮 磨石 block is WallMountedBlock 属性不同禁用不同保护/面不能点
                when (required.getValue<AttachFace>(BlockStateProperties.ATTACH_FACE)) {
                    AttachFace.FLOOR -> {
                        if (face != Direction.UP) continue  // 只能放在邻居上方

                        // 方向取决于玩家，禁用点击面保护
                        disableFaceProtection = true
                        disableAirPlace = true
                    }

                    AttachFace.CEILING -> {
                        if (face != Direction.DOWN) continue  // 只能放在邻居下方

                        // 方向取决于玩家，禁用点击面保护
                        disableFaceProtection = true
                        disableAirPlace = true
                    }

                    AttachFace.WALL -> {
                        if (face == Direction.UP || face == Direction.DOWN) continue  // 只能放在邻居四周

                        //方向取决于点击面，禁用方向保护
                        disableDirectionProtection = true
                    }
                }
            } else if (block is TripWireHookBlock) {//绊线钩
                when (face) {
                    Direction.UP, Direction.DOWN -> disableFaceProtection = true
                    else -> disableDirectionProtection = true
                }
            } else if (block is DoorBlock) {//门 点击点偏移
                if (铰链比对立多 == 0) {
                    if (face == 铰链方向) continue
                    if (face != 铰链方向.opposite) {
                        tempHitPos = tempHitPos.add(
                            铰链方向.stepX * 0.25,
                            0.0,
                            铰链方向.stepZ * 0.25
                        )
                    }
                } else if (铰链比对立多 < 0) {
                    return false
                }
            }
            val airPlaceAllowed = !disableAirPlace && enableAirPlace
            val isPlaceAllowedFromClickFace by lazy { required.isPlaceAllowedFromClickFace(face) }
            val isFaceSafe by lazy {
                when (safetyPlaceFaceMode.get()) {
                    SafetyFaceMode.PlayerRotation -> BlockUtils.getDirection(pos)
                    SafetyFaceMode.PlayerPosition -> pos.PickAFaceFromPlayerPosition(player)
                    SafetyFaceMode.None -> null
                }?.let {
                    face == it
                } ?: true
            }

            for (i in 0..1) {
                val useAirPlace = i == 0
                if (useAirPlace && !airPlaceAllowed) continue


                val disableDP = if (useAirPlace) false else disableDirectionProtection
                val disableFP = disableFaceProtection


                if (directionProtection.get() && !disableDP && !isPlaceAllowedFromPlayerRotation) continue
                if (clickProtection.get() && !disableFP && !isPlaceAllowedFromClickFace) continue

                val neighbour = if (useAirPlace) pos else {
                    val oppositeFace = face.opposite
                    val neighborPos = pos.relative(oppositeFace)
                    if (!required.canPlaceAgainst(neighborPos, face)) continue
                    neighborPos
                }
                val hitPos = if (useAirPlace) tempHitPos else {
                    val oppositeFace = face.opposite
                    tempHitPos.add(
                        oppositeFace.stepX * 0.5,
                        oppositeFace.stepY * 0.5,
                        oppositeFace.stepZ * 0.5
                    )
                }

                // 已经确定了face hitPos neighbour

                if (hitPos.distanceTo(player.eyePosition) > PlayerHandDistance) continue


                if (!isFaceSafe) break

                if (!placeThroughWall.get()) {
                    val isVisible =
                        if (useAirPlace) posCenterVisible else (neighbour to face).isVisible
                    if (!isVisible) continue
                }
                if (onlyPlaceOnLookFace.get() && !player.RotationInTheFaceOfBlock(neighbour, face)) continue


                var item = required.block.asItem()
                if (dirtgrass.get() && item === Items.GRASS_BLOCK) item = Items.DIRT
                if (!player.switchTo(item)) {
                    // info("没有物品${item}，无法放置${block}在$pos")
                    return false
                }
                return if (place(BlockHitResult(hitPos, face, neighbour, false))) {
                    // info("$block 成功放在 $pos,  \n点了$neighbour 的$face 面 于$hitPos")
                    true
                } else {
                    info("${block}失败放在$pos,\n点了${neighbour}的${face}面 于$hitPos")
                    false
                }
            }
        }
        return false
    }

    private fun place(blockHitResult: BlockHitResult): Boolean {
        val result = mc.player?.let { mc.gameMode?.useItemOn(it, InteractionHand.MAIN_HAND, blockHitResult) }
        if (result == InteractionResult.SUCCESS) {
            mc.player?.swing(InteractionHand.MAIN_HAND, swingMode.get())
            return true
        }
        return false
    }


    private val BlockState.isMultiStructurePlacementAllowed: Boolean
        get() {
            if (hasProperty(BlockStateProperties.BED_PART)) {
                val bedPart = this.getValue<BedPart>(BlockStateProperties.BED_PART)
                if (bedPart == BedPart.HEAD) {
                    return false
                }
            }

            if (hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                val doubleBlockHalf = this.getValue<DoubleBlockHalf>(BlockStateProperties.DOUBLE_BLOCK_HALF)
                if (doubleBlockHalf == DoubleBlockHalf.UPPER) {
                    return false
                }
            }
            return true
        }

}
