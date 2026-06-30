package com.kkllffaa.meteor_litematica_printer


import kotlin.math.*

data class Rotation(val yaw: Float, val pitch: Float) {
    init {
        check(!(yaw.isInfinite() || yaw.isNaN() || pitch.isInfinite() || pitch.isNaN()))
        { "$yaw $pitch" }
    }

    fun add(other: Rotation): Rotation {
        return Rotation(
            this.yaw + other.yaw,
            this.pitch + other.pitch
        )
    }

    fun subtract(other: Rotation): Rotation {
        return Rotation(
            this.yaw - other.yaw,
            this.pitch - other.pitch
        )
    }

    fun clamp(): Rotation {
        return Rotation(
            this.yaw,
            clampPitch(this.pitch)
        )
    }

    fun normalize(): Rotation {
        return Rotation(
            normalizeYaw(this.yaw),
            this.pitch
        )
    }

    fun normalizeAndClamp(): Rotation {
        return Rotation(
            normalizeYaw(this.yaw),
            clampPitch(this.pitch)
        )
    }

    fun withPitch(pitch: Float): Rotation {
        return Rotation(this.yaw, pitch)
    }

    fun isReallyCloseTo(other: Rotation): Boolean {
        return yawIsReallyClose(other) && abs(this.pitch - other.pitch) < 0.01
    }

    fun yawIsReallyClose(other: Rotation): Boolean {
        val yawDiff = abs(normalizeYaw(yaw) - normalizeYaw(other.yaw)) // you cant fool me
        return (yawDiff !in 0.01..359.99)
    }

    override fun toString(): String {
        return "Yaw: $yaw, Pitch: $pitch"
    }

    companion object {
        fun clampPitch(pitch: Float): Float {
            return max(-90f, min(90f, pitch))
        }

        fun normalizeYaw(yaw: Float): Float {
            var newYaw = yaw % 360f
            if (newYaw < -180f) {
                newYaw += 360f
            }
            if (newYaw > 180f) {
                newYaw -= 360f
            }
            return newYaw
        }
    }
}
