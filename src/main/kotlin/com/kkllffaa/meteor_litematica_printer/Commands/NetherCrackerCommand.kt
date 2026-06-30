package com.kkllffaa.meteor_litematica_printer.Commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import meteordevelopment.meteorclient.commands.Command
import net.minecraft.world.level.block.Blocks
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ClickEvent.CopyToClipboard
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk

private const val SEARCH_RADIUS = 128
private val BEDROCK_Y_LEVELS = intArrayOf(4, 123)

object NetherCrackerCommand : Command(
    "nethercracker",
    "Finds bedrock at Y=4 and Y=123 in the Nether within a specified radius."
) {
    override fun build(builder: LiteralArgumentBuilder<ClientSuggestionProvider?>?) {
        builder?.executes { scan(checkDimension = true) }
        builder?.then(LiteralArgumentBuilder.literal<ClientSuggestionProvider?>("force").executes { scan(checkDimension = false) })
    }

    private fun scan(checkDimension: Boolean): Int {
        val player = mc.player ?: return error("Player not found.").let { SINGLE_SUCCESS }
        val world = mc.level ?: return error("World not found.").let { SINGLE_SUCCESS }

        if (checkDimension && world.dimension() != Level.NETHER) {
            error("You must be in the Nether to use this command. Use '.nethercracker force' to skip this check.")
            return SINGLE_SUCCESS
        }

        val centerChunkPos = world.getChunk(player.blockPosition()).pos
        val chunkRadius = SEARCH_RADIUS / 16

        info("Scanning chunks in a $SEARCH_RADIUS block radius ($chunkRadius chunk radius)...")

        val bedrockCandidates = buildList {
            for (r in 0..chunkRadius) {
                val xRange = centerChunkPos.x - r..centerChunkPos.x + r
                val zRange = centerChunkPos.z - r..centerChunkPos.z + r

                for (chunkX in xRange) {
                    for (chunkZ in zRange) {
                        if (r > 0 && chunkX in (xRange.first + 1)..<xRange.last && chunkZ in (zRange.first + 1)..<zRange.last) {
                            continue
                        }

                        world.chunkSource.getChunk(chunkX, chunkZ, false)?.let { chunk ->
                            addAll(chunk.findBedrockBlocks())
                        }
                    }
                }
            }
        }

        info("Found ${bedrockCandidates.size} bedrock blocks at y=4 and y=123.")

        if (bedrockCandidates.isNotEmpty()) {
            val coords = bedrockCandidates.joinToString("\n") { "${it.x} ${it.y} ${it.z}" }

            val copyText = ComponentUtils.wrapInSquareBrackets(
                Component.literal("Copy Coords")
                    .withStyle(
                        Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withClickEvent(CopyToClipboard(coords))
                            .withHoverEvent(ShowText(Component.literal("Click to copy all coordinates")))
                            .withInsertion(coords)
                    )
            )
            info(copyText)
        }

        return SINGLE_SUCCESS
    }

    private fun LevelChunk.findBedrockBlocks(): List<BlockPos> = buildList {
        val chunkPos = pos
        val mutablePos = BlockPos.MutableBlockPos()

        for (x in 0..15) {
            for (z in 0..15) {
                val worldX = chunkPos.minBlockX + x
                val worldZ = chunkPos.minBlockZ + z

                for (y in BEDROCK_Y_LEVELS) {
                    mutablePos.set(worldX, y, worldZ)
                    if (getBlockState(mutablePos).`is`(Blocks.BEDROCK)) {
                        add(mutablePos.immutable())
                        break
                    }
                }
            }
        }
    }

}
