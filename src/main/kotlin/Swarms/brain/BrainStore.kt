package com.kkllffaa.meteor_litematica_printer.swarms.brain

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * 主脑的“公共信息内存”——需要在蜂群里共享、且必须在主脑客户端退出/崩溃后自动恢复的数据。
 *
 * 目前只有一项：[assignments] 基座分配表（baseName -> 它要驮的玩家 A 的名字）。
 *
 * 数据常驻内存（[ConcurrentHashMap]，多线程安全），并且：
 *  - 周期性 + 退出时落盘（见 [save]）；
 *  - 主脑启动时从盘里读回（见 [load]）——这就是“下次自动恢复”。
 *
 * 落盘格式是简单的 TAB 分隔文本，原子写入（先写 .tmp 再 move），避免崩溃时写坏文件。
 */
class BrainStore(private val file: Path) {

    /** baseName -> targetName */
    val assignments = ConcurrentHashMap<String, String>()

    fun setAssignment(base: String, target: String) {
        if (base.isBlank()) return
        if (target.isBlank()) assignments.remove(base) else assignments[base] = target
    }

    fun removeAssignment(base: String) {
        assignments.remove(base)
    }

    /** 从磁盘恢复。文件不存在视为空状态（首次运行）。 */
    fun load() {
        try {
            if (!file.exists()) return
            assignments.clear()
            for (raw in file.readLines()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val p = line.split('\t')
                if (p.getOrNull(0) == TAG && p.size >= 3 && p[1].isNotBlank() && p[2].isNotBlank()) {
                    assignments[p[1]] = p[2]
                }
            }
        } catch (e: Exception) {
            System.err.println("[Swarm] BrainStore load failed: ${e.message}")
        }
    }

    /** 原子落盘。任意线程可调用（包括 JVM 关闭钩子）。 */
    @Synchronized
    fun save() {
        try {
            file.createParentDirectories()
            val text = buildString {
                appendLine("# swarm brain state v1")
                for ((base, target) in assignments) appendLine("$TAG\t$base\t$target")
            }

            val tmp = file.resolveSibling("${file.fileName}.tmp")
            tmp.writeText(text)
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                // 某些文件系统不支持原子 move，退化为普通 move
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            System.err.println("[Swarm] BrainStore save failed: ${e.message}")
        }
    }

    private companion object {
        /** 落盘文件里每条分配记录的行首标记。 */
        const val TAG = "ASSIGN"
    }
}
