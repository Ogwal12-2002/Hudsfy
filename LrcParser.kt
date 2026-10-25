package com.hudsify.app.lyrics

import com.hudsify.app.model.LrcLine
import java.util.regex.Pattern

object LrcParser {
    private val tag = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)")
    fun parse(text: String): List<LrcLine> = buildList {
        text.lineSequence().forEach { line ->
            val m = tag.matcher(line)
            if (m.matches()) {
                val min = m.group(1)?.toLongOrNull() ?: return@forEach
                val sec = m.group(2)?.toLongOrNull() ?: return@forEach
                val fraction = (m.group(3) ?: "0").padEnd(3, '0').take(3).toLongOrNull() ?: 0
                add(LrcLine(min * 60_000 + sec * 1_000 + fraction, m.group(4)?.trim().orEmpty()))
            }
        }
    }.sortedBy { it.timeMs }
}
