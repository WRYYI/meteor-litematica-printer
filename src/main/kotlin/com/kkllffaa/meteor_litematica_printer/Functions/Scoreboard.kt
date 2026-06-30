package com.kkllffaa.meteor_litematica_printer.Functions

import meteordevelopment.meteorclient.MeteorClient.mc
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard

/*
 * 计分板(Scoreboard)是怎么组成的：
 *
 *   Scoreboard            一块世界共享的记分板，客户端从 mc.level.scoreboard 取得。
 *   ├─ Objective          一个"目标"，有内部名(name)和显示名(displayName)。
 *   ├─ DisplaySlot        槽位，决定哪个目标显示在哪里；右侧边栏是 SIDEBAR。
 *   ├─ PlayerScoreEntry   边栏里的一行：owner(行的标识) + value(右侧分数)。
 *   └─ PlayerTeam         队伍；给 owner 套上 前缀+后缀(和颜色) 才是真正显示的文字。
 *
 * 右侧边栏怎么画(见 Gui#displayScoreboardSidebar)：
 *   · 标题 = SIDEBAR 目标的 displayName，居中显示在最顶部。            ← 这里是"标题"
 *   · 每行 = 一个 PlayerScoreEntry：左边文字 + 右边分数。              ← 这里是"内容"
 *           行按分数从高到低排序，最多 15 行，owner 以 '#' 开头的行隐藏。
 *
 * 什么时候有队伍：仅当某行的 owner 被加进了某个队伍时(getPlayersTeam 非空)。
 * 此时左边文字 = 队伍前缀 + owner + 队伍后缀；否则就是 owner 本身。
 *
 * 本服务器把"当前位置"放在标题里，所以判断大区只要读 sidebarTitle 即可。
 */

/** 客户端当前世界的计分板；未进入世界时为 null。 */
val scoreboard: Scoreboard?
    get() = mc.level?.scoreboard

/** 右侧边栏(SIDEBAR 槽位)正在显示的目标；边栏未启用时为 null。 */
val sidebarObjective: Objective?
    get() = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)

/** 右侧边栏的标题文字，即目标的显示名。 */
val sidebarTitle: String?
    get() = sidebarObjective?.displayName?.string

/**
 * 右侧边栏从上到下的每行文字，已套用各行所属队伍的前/后缀。
 *
 * 顺序与游戏内一致（分数从高到低，最多 15 行），不含右侧的分数数字。
 */
val sidebarLines: List<String>
    get() {
        val board = scoreboard ?: return emptyList()
        val objective = sidebarObjective ?: return emptyList()
        return board.listPlayerScores(objective)
            .asSequence()
            .filterNot { it.isHidden }
            .sortedWith(SIDEBAR_ORDER)
            .take(15)
            .map { board.lineOf(it) }
            .toList()
    }

/** 与游戏内边栏一致的排序：分数降序，分数相同按 owner 不区分大小写。 */
private val SIDEBAR_ORDER: Comparator<PlayerScoreEntry> =
    compareByDescending<PlayerScoreEntry> { it.value() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }

/** 把一行分数条目还原成屏幕上真正显示的文字（队伍前缀 + 名字 + 队伍后缀）。 */
private fun Scoreboard.lineOf(entry: PlayerScoreEntry): String {
    val team: PlayerTeam? = getPlayersTeam(entry.owner())
    return PlayerTeam.formatNameForTeam(team, entry.ownerName()).string
}
