package com.kkllffaa.meteor_litematica_printer.servers

import com.kkllffaa.meteor_litematica_printer.Functions.sidebarTitle

/**
 * 服务器大区。
 *
 * 右侧计分板标题形如 "当前位置:[登录大区]"，方括号内即玩家所在大区，与下列常量同名。
 * 识别大区只需把标题与常量名匹配——一处枚举搞定，无需为每个大区单独写判断方法。
 */
enum class 大区 {
    登录大区,
    生存一区,
    生存二区,
    资源大区,
    主城大区,
}

/** 当前所在大区（读自右侧计分板标题）；未进服 / 无右侧边栏 / 标题无法识别时为 null。 */
val 当前大区: 大区?
    get() {
        val 标题 = sidebarTitle ?: return null
        return 大区.entries.firstOrNull { it.name in 标题 }
    }
