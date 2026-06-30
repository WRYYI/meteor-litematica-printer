package com.kkllffaa.meteor_litematica_printer.Modules.Tools

import com.kkllffaa.meteor_litematica_printer.Addon
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.*
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.RenderUtils
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.resources.Identifier
import net.minecraft.world.level.levelgen.XoroshiroRandomSource
import net.minecraft.world.level.levelgen.synth.NormalNoise
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

object CheeseCaveFinder : Module(
    Addon.TOOLS,
    "CheeseCaveFinder",
    "Finds the nearest cheese caves for a known overworld seed by sampling cave_cheese noise locally."
) {
    private val sgGeneral = settings.defaultGroup
    private val sgSearch = settings.createGroup("Search")
    private val sgRender = settings.createGroup("Render")

    private val seedSetting: Setting<String> = sgGeneral.add(
        StringSetting.Builder()
            .name("seed")
            .description("World seed (signed 64-bit decimal). Example: 2099652294194755374")
            .defaultValue("2099652294194755374")
            .onChanged { invalidateNoise() }
            .build()
    )

    private val autoUpdate: Setting<Boolean> = sgGeneral.add(
        BoolSetting.Builder()
            .name("auto-update")
            .description("Re-run the search when the player moves more than 'rerun-distance' blocks from the last search origin.")
            .defaultValue(true)
            .build()
    )

    private val rerunDistance: Setting<Int> = sgGeneral.add(
        IntSetting.Builder()
            .name("rerun-distance")
            .description("Player travel distance that triggers an automatic re-search.")
            .defaultValue(48)
            .min(8).sliderMin(8).max(512).sliderMax(256)
            .visible { autoUpdate.get() }
            .build()
    )

    private val searchRadius: Setting<Int> = sgSearch.add(
        IntSetting.Builder()
            .name("horizontal-radius")
            .description("Horizontal search radius around the player (blocks).")
            .defaultValue(256)
            .min(32).sliderMin(64).max(2048).sliderMax(1024)
            .build()
    )

    private val minY: Setting<Int> = sgSearch.add(
        IntSetting.Builder()
            .name("min-y")
            .description("Lowest Y to sample.")
            .defaultValue(-56)
            .min(-64).sliderMin(-64).max(320).sliderMax(80)
            .build()
    )

    private val maxY: Setting<Int> = sgSearch.add(
        IntSetting.Builder()
            .name("max-y")
            .description("Highest Y to sample.")
            .defaultValue(30)
            .min(-64).sliderMin(-64).max(320).sliderMax(80)
            .build()
    )

    private val step: Setting<Int> = sgSearch.add(
        IntSetting.Builder()
            .name("step")
            .description("Grid spacing in blocks. Smaller = more accurate but slower (cost grows cubically).")
            .defaultValue(8)
            .min(2).sliderMin(4).max(16).sliderMax(12)
            .build()
    )

    private val threshold: Setting<Double> = sgSearch.add(
        DoubleSetting.Builder()
            .name("cave-threshold")
            .description("Cell counts as a cheese cave when cave_cheese noise < -threshold. Higher = only large open caverns.")
            .defaultValue(0.45)
            .min(0.05).sliderMin(0.1).max(1.5).sliderMax(1.0)
            .decimalPlaces(3)
            .build()
    )

    private val minClusterCells: Setting<Int> = sgSearch.add(
        IntSetting.Builder()
            .name("min-cluster-cells")
            .description("Drop clusters with fewer cells than this. Filters out small noise pockets.")
            .defaultValue(6)
            .min(1).sliderMin(1).max(200).sliderMax(50)
            .build()
    )

    private val maxResults: Setting<Int> = sgSearch.add(
        IntSetting.Builder()
            .name("max-results")
            .description("Number of nearest clusters to render.")
            .defaultValue(5)
            .min(1).sliderMin(1).max(50).sliderMax(20)
            .build()
    )

    private val shapeMode: Setting<ShapeMode> = sgRender.add(
        EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Wireframe / filled / both.")
            .defaultValue(ShapeMode.Both)
            .build()
    )

    private val nearestColor: Setting<SettingColor> = sgRender.add(
        ColorSetting.Builder()
            .name("nearest-color-line")
            .description("Line color for the nearest cave cluster.")
            .defaultValue(SettingColor(255, 220, 60, 255))
            .build()
    )

    private val nearestSideColor: Setting<SettingColor> = sgRender.add(
        ColorSetting.Builder()
            .name("nearest-color-side")
            .description("Fill color for the nearest cave cluster.")
            .defaultValue(SettingColor(255, 220, 60, 50))
            .build()
    )

    private val otherColor: Setting<SettingColor> = sgRender.add(
        ColorSetting.Builder()
            .name("other-color-line")
            .description("Line color for other detected clusters.")
            .defaultValue(SettingColor(160, 160, 200, 180))
            .build()
    )

    private val otherSideColor: Setting<SettingColor> = sgRender.add(
        ColorSetting.Builder()
            .name("other-color-side")
            .description("Fill color for other detected clusters.")
            .defaultValue(SettingColor(160, 160, 200, 30))
            .build()
    )

    private val showTracer: Setting<Boolean> = sgRender.add(
        BoolSetting.Builder()
            .name("tracer")
            .description("Draw a line from the camera to the nearest cluster center.")
            .defaultValue(true)
            .build()
    )

    private val tracerColor: Setting<SettingColor> = sgRender.add(
        ColorSetting.Builder()
            .name("tracer-color")
            .description("Tracer line color.")
            .defaultValue(SettingColor(255, 220, 60, 200))
            .visible { showTracer.get() }
            .build()
    )

    // --- noise & search state ----------------------------------------------------

    private val CHEESE_PARAMS = NormalNoise.NoiseParameters(
        -8, 0.5, 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 2.0, 0.0
    )

    private const val FLAG_CAVE: Byte = 1
    private const val FLAG_VISITED: Byte = 2

    @Volatile private var cheeseNoise: NormalNoise? = null
    @Volatile private var noiseSeed: Long = 0L
    @Volatile private var noiseSeedString: String = ""

    private val searching = AtomicBoolean(false)
    @Volatile private var results: List<Cluster> = emptyList()
    @Volatile private var lastOrigin: LongArray? = null   // [x, y, z] block
    @Volatile private var statusMessage: String = "idle"

    data class Cluster(
        val cells: Int,
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val centerX: Double, val centerY: Double, val centerZ: Double,
        val distanceToOrigin: Double
    )

    // --- lifecycle ---------------------------------------------------------------

    override fun onActivate() {
        invalidateNoise()
        triggerSearch(force = true)
    }

    override fun onDeactivate() {
        results = emptyList()
        lastOrigin = null
    }

    private fun invalidateNoise() {
        cheeseNoise = null
        results = emptyList()
        lastOrigin = null
    }

    private fun ensureNoise(): NormalNoise? {
        val current = cheeseNoise
        val rawSeed = seedSetting.get().trim()
        if (current != null && rawSeed == noiseSeedString) return current
        val seed = parseSeed(rawSeed) ?: run {
            statusMessage = "invalid seed"
            return null
        }
        val base = XoroshiroRandomSource(seed)
        val positional = base.forkPositional()
        val noiseRandom = positional.fromHashOf(Identifier.withDefaultNamespace("cave_cheese"))
        val noise = NormalNoise.create(noiseRandom, CHEESE_PARAMS)
        cheeseNoise = noise
        noiseSeed = seed
        noiseSeedString = rawSeed
        return noise
    }

    private fun parseSeed(raw: String): Long? {
        if (raw.isEmpty()) return null
        // Numeric seed first; fall back to vanilla's textual-seed convention (String.hashCode()).
        return raw.toLongOrNull() ?: raw.hashCode().toLong()
    }

    // --- search trigger ----------------------------------------------------------

    @EventHandler
    private fun onTick(event: TickEvent.Post) {
        if (!autoUpdate.get()) return
        val player = mc.player ?: return
        val origin = lastOrigin
        val px = player.x.toLong()
        val py = player.y.toLong()
        val pz = player.z.toLong()
        if (origin == null) {
            triggerSearch(force = false)
            return
        }
        val dx = px - origin[0]
        val dy = py - origin[1]
        val dz = pz - origin[2]
        val distSq = dx * dx + dy * dy + dz * dz
        val r = rerunDistance.get().toLong()
        if (distSq > r * r) triggerSearch(force = false)
    }

    private fun triggerSearch(force: Boolean) {
        if (!force && searching.get()) return
        if (!searching.compareAndSet(false, true)) return
        val player = mc.player
        if (player == null) {
            searching.set(false)
            return
        }
        val noise = ensureNoise()
        if (noise == null) {
            searching.set(false)
            return
        }
        val originX = player.x.toInt()
        val originY = player.y.toInt()
        val originZ = player.z.toInt()
        val radius = searchRadius.get()
        val yMin = minY.get()
        val yMax = maxY.get().coerceAtLeast(yMin)
        val stepSize = step.get().coerceAtLeast(1)
        val cutoff = -threshold.get()
        val minCells = minClusterCells.get()
        val keepTop = maxResults.get()

        statusMessage = "searching..."
        Thread({
            try {
                val found = runSearch(
                    noise, originX, originY, originZ,
                    radius, yMin, yMax, stepSize, cutoff, minCells, keepTop
                )
                results = found
                lastOrigin = longArrayOf(originX.toLong(), originY.toLong(), originZ.toLong())
                statusMessage = if (found.isEmpty()) "no caves" else "${found.size} cave(s)"
            } catch (t: Throwable) {
                statusMessage = "error: ${t.message}"
            } finally {
                searching.set(false)
            }
        }, "CheeseCaveFinder-search").also {
            it.isDaemon = true
            it.priority = Thread.MIN_PRIORITY
        }.start()
    }

    // --- core search -------------------------------------------------------------

    private fun runSearch(
        noise: NormalNoise,
        originX: Int, originY: Int, originZ: Int,
        horizontalRadius: Int,
        yMin: Int, yMax: Int,
        stepSize: Int,
        cutoff: Double,
        minCells: Int,
        keepTop: Int
    ): List<Cluster> {
        // Build a grid of samples. Each grid index (gx, gy, gz) maps to world coords:
        //   wx = originX + (gx - halfX) * step
        // Bit packed flags: 1 = cave cell.
        val xCount = (2 * horizontalRadius / stepSize) + 1
        val zCount = xCount
        val yCount = ((yMax - yMin) / stepSize) + 1
        if (xCount <= 0 || yCount <= 0 || zCount <= 0) return emptyList()

        val total = xCount * yCount * zCount
        // Safety cap so misconfigured settings don't lock the game.
        if (total > 12_000_000) {
            statusMessage = "search volume too large ($total cells); reduce radius or increase step"
            return emptyList()
        }

        val flags = ByteArray(total)
        val baseX = originX - (xCount / 2) * stepSize
        val baseZ = originZ - (zCount / 2) * stepSize

        // Sample noise: cheese density function = noise(x * 1.0, y * 2/3, z * 1.0).
        var idx = 0
        for (gy in 0 until yCount) {
            val wy = (yMin + gy * stepSize).toDouble() * (2.0 / 3.0)
            for (gz in 0 until zCount) {
                val wz = (baseZ + gz * stepSize).toDouble()
                for (gx in 0 until xCount) {
                    val wx = (baseX + gx * stepSize).toDouble()
                    val v = noise.getValue(wx, wy, wz)
                    if (v < cutoff) flags[idx] = FLAG_CAVE
                    idx++
                }
            }
        }

        // 3D flood fill (6-neighbour) to extract clusters.
        val xStride = 1
        val zStride = xCount
        val yStride = xCount * zCount
        val stack = IntArray(total)
        val clusters = ArrayList<Cluster>()

        for (start in 0 until total) {
            if (flags[start] != FLAG_CAVE) continue
            flags[start] = FLAG_VISITED
            stack[0] = start
            var stackPtr = 1

            var cells = 0
            var sumX = 0L; var sumY = 0L; var sumZ = 0L
            var miX = Int.MAX_VALUE; var miY = Int.MAX_VALUE; var miZ = Int.MAX_VALUE
            var maX = Int.MIN_VALUE; var maY = Int.MIN_VALUE; var maZ = Int.MIN_VALUE

            while (stackPtr > 0) {
                val cur = stack[--stackPtr]
                val gy = cur / yStride
                val rem = cur - gy * yStride
                val gz = rem / zStride
                val gx = rem - gz * zStride

                cells++
                val wx = baseX + gx * stepSize
                val wy = yMin + gy * stepSize
                val wz = baseZ + gz * stepSize
                sumX += wx; sumY += wy; sumZ += wz
                if (wx < miX) miX = wx
                if (wx > maX) maX = wx
                if (wy < miY) miY = wy
                if (wy > maY) maY = wy
                if (wz < miZ) miZ = wz
                if (wz > maZ) maZ = wz

                if (gx > 0 && flags[cur - xStride] == FLAG_CAVE) {
                    flags[cur - xStride] = FLAG_VISITED; stack[stackPtr++] = cur - xStride
                }
                if (gx < xCount - 1 && flags[cur + xStride] == FLAG_CAVE) {
                    flags[cur + xStride] = FLAG_VISITED; stack[stackPtr++] = cur + xStride
                }
                if (gz > 0 && flags[cur - zStride] == FLAG_CAVE) {
                    flags[cur - zStride] = FLAG_VISITED; stack[stackPtr++] = cur - zStride
                }
                if (gz < zCount - 1 && flags[cur + zStride] == FLAG_CAVE) {
                    flags[cur + zStride] = FLAG_VISITED; stack[stackPtr++] = cur + zStride
                }
                if (gy > 0 && flags[cur - yStride] == FLAG_CAVE) {
                    flags[cur - yStride] = FLAG_VISITED; stack[stackPtr++] = cur - yStride
                }
                if (gy < yCount - 1 && flags[cur + yStride] == FLAG_CAVE) {
                    flags[cur + yStride] = FLAG_VISITED; stack[stackPtr++] = cur + yStride
                }
            }

            if (cells < minCells) continue

            val cx = sumX.toDouble() / cells
            val cy = sumY.toDouble() / cells
            val cz = sumZ.toDouble() / cells
            val dx = cx - originX
            val dy = cy - originY
            val dz = cz - originZ
            clusters.add(
                Cluster(
                    cells = cells,
                    minX = miX, minY = miY, minZ = miZ,
                    maxX = maX + stepSize, maxY = maY + stepSize, maxZ = maZ + stepSize,
                    centerX = cx, centerY = cy, centerZ = cz,
                    distanceToOrigin = sqrt(dx * dx + dy * dy + dz * dz)
                )
            )
        }

        clusters.sortBy { it.distanceToOrigin }
        return if (clusters.size <= keepTop) clusters else clusters.subList(0, keepTop).toList()
    }

    // --- rendering ---------------------------------------------------------------

    @EventHandler
    private fun onRender3D(event: Render3DEvent) {
        val snapshot = results
        if (snapshot.isEmpty()) return
        val mode = shapeMode.get()
        for ((i, c) in snapshot.withIndex()) {
            val isNearest = i == 0
            val line = if (isNearest) nearestColor.get() else otherColor.get()
            val side = if (isNearest) nearestSideColor.get() else otherSideColor.get()
            event.renderer.box(
                c.minX.toDouble(), c.minY.toDouble(), c.minZ.toDouble(),
                c.maxX.toDouble(), c.maxY.toDouble(), c.maxZ.toDouble(),
                side, line, mode, 0
            )
        }
        if (showTracer.get()) {
            val nearest = snapshot[0]
            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                nearest.centerX, nearest.centerY, nearest.centerZ,
                tracerColor.get()
            )
        }
    }

    override fun getInfoString(): String {
        val r = results
        if (r.isEmpty()) return statusMessage
        val n = r[0]
        val player = mc.player
        val dx: Double; val dy: Double; val dz: Double
        if (player != null) {
            dx = n.centerX - player.x; dy = n.centerY - player.y; dz = n.centerZ - player.z
        } else {
            dx = 0.0; dy = 0.0; dz = 0.0
        }
        val live = sqrt(dx * dx + dy * dy + dz * dz)
        return "${r.size} | ${"%.0f".format(n.centerX)},${"%.0f".format(n.centerY)},${"%.0f".format(n.centerZ)} (${"%.0f".format(live)}m)"
    }
}
