package com.kkllffaa.meteor_litematica_printer.Functions

import meteordevelopment.meteorclient.utils.render.color.SettingColor
import kotlin.random.Random.Default

enum class SignColorMode {
    None,
    `§`,
    `&`
}

enum class SafetyFaceMode {
    PlayerRotation,
    PlayerPosition,  // 射线方向
    None,
}

enum class ColorScheme(val sideColor: SettingColor, val lineColor: SettingColor) {
    红(SettingColor(204, 0, 0, 10), SettingColor(204, 0, 0, 255)),
    绿(SettingColor(0, 204, 0, 10), SettingColor(0, 204, 0, 255)),
    蓝(SettingColor(0, 0, 204, 10), SettingColor(0, 0, 204, 255)),
    黄(SettingColor(204, 204, 0, 10), SettingColor(204, 204, 0, 255)),
    紫(SettingColor(204, 0, 204, 10), SettingColor(204, 0, 204, 255)),
    青(SettingColor(0, 204, 204, 10), SettingColor(0, 204, 204, 255)),
    白(SettingColor(255, 255, 255, 10), SettingColor(255, 255, 255, 255))
}

enum class RandomDelayMode(private val delays: IntArray?) {
    None(null),
    Fast(intArrayOf(0, 0, 1)),
    Balanced(intArrayOf(0, 0, 0, 0, 1, 1, 1, 2, 2, 3)),
    Slow(intArrayOf(0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 5, 6)),
    Variable(intArrayOf(0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));


    val theDelay get() = delays?.get(Default.nextInt(delays.size)) ?: 0
}

enum class DistanceMode {
    Auto,
    Max,
}

enum class ProtectMode {
    Off,
    ReferencePlayerY,
    ReferenceWorldY
}

enum class ListMode {
    Whitelist,
    Blacklist,
    None
}


enum class ActionMode {
    None,
    SendPacket,
    Normal
}

enum class SafetyFace {
    PlayerRotation,
    PlayerPosition,
}

enum class PreferPerspective {
    NONE,
    FIRST_PERSON,
    THIRD_PERSON_BACK,
}

enum class 触发模式 {
    自动半径全部,
    手动相连同类,
}

enum class OreMode {
    遵循黑白名单,
    强制挖掘,
    强制不挖掘,
}

enum class MeshMineMode {
    Cache,
    CacheAndAir,
    CacheAndAirAndFluid,
}

// 网格挖掘策略：探查点2=看邻面暴露(原设计)，探查点6/7=按稀疏探查点阵(IsProb6/IsProb7)判定
enum class 网格挖掘模式 {
    探查点2,
    探查点6,
    探查点7,
}

// 网格挖掘的挡路豁免范围：广范围=玩家包围盒按速度外扩，窄通道=前进方向高2宽1单柱
enum class 挡路模式 {
    广范围,
    窄通道,
}