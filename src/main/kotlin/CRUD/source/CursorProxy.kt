package com.kkllffaa.meteor_litematica_printer.crud.source

import com.kkllffaa.meteor_litematica_printer.Functions.canBreakIt
import com.kkllffaa.meteor_litematica_printer.Functions.isAirOrFluid
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSink
import com.kkllffaa.meteor_litematica_printer.crud.engine.DemandSource
import com.kkllffaa.meteor_litematica_printer.crud.engine.Executor
import meteordevelopment.meteorclient.MeteorClient.mc
import net.minecraft.core.BlockPos


object CursorProxy : DemandSource {

    private const val LIVE_TICKS = 2

    private var target: BlockPos? = null
    private var live = 0

    override val active: Boolean get() = target != null

    override val mergePriority: Int get() = Int.MAX_VALUE


    fun playerWantDestroy(pos: BlockPos) {
        if (!Executor.isRunning || !pos.canBreakIt()) return
        target = pos.immutable()
        live = LIVE_TICKS
    }

    override fun collect(sink: DemandSink) {
        val pos = target ?: return
        if (live-- <= 0) {
            target = null
            return
        }
        if (!(mc.level ?: return).getBlockState(pos).isAirOrFluid) sink.want(pos, null, Int.MAX_VALUE)
    }
}
