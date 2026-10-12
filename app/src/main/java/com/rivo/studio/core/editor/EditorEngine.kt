package com.rivo.studio.core.editor

import java.util.ArrayDeque

object AutoIndent {
    data class Result(val text: String, val insertedIndent: Int)

    fun apply(previous: String, proposed: String): Result {
        if (proposed.length != previous.length + 1) return Result(proposed, 0)
        val prefix = commonPrefixLength(previous, proposed)
        if (prefix >= proposed.length || proposed[prefix] != '\n') return Result(proposed, 0)
        if (proposed.substring(prefix + 1) != previous.substring(prefix)) return Result(proposed, 0)

        val lineBefore = previous.substring(0, prefix).substringAfterLast('\n')
        val leadingWhitespace = lineBefore.takeWhile { it == ' ' || it == '\t' }
        val trimmed = lineBefore.trimEnd()
        val extraIndent = when {
            trimmed.endsWith(":") -> "    "
            trimmed.endsWith("{") || trimmed.endsWith("[") || trimmed.endsWith("(") -> "    "
            else -> ""
        }
        val indent = leadingWhitespace + extraIndent
        if (indent.isEmpty()) return Result(proposed, 0)
        return Result(proposed.substring(0, prefix + 1) + indent + proposed.substring(prefix + 1), indent.length)
    }

    private fun commonPrefixLength(left: String, right: String): Int {
        var index = 0
        val limit = minOf(left.length, right.length)
        while (index < limit && left[index] == right[index]) index++
        return index
    }
}

/** Coalesces keystrokes and bounds retained text snapshots to a small local budget. */
class EditorHistory(
    private val maxEntries: Int = 40,
    private val maxSnapshotChars: Int = 2 * 1024 * 1024,
    private val coalesceWindowMs: Long = 650,
) {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var retainedChars = 0L
    private var lastChangeAt = 0L

    fun record(before: String, now: Long = System.currentTimeMillis()) {
        if (lastChangeAt == 0L || now - lastChangeAt > coalesceWindowMs) {
            undo.addLast(before)
            retainedChars += before.length
            trim()
        }
        lastChangeAt = now
        clear(redo)
    }

    fun undo(current: String): String? {
        val previous = undo.pollLast() ?: return null
        retainedChars -= previous.length
        redo.addLast(current)
        retainedChars += current.length
        lastChangeAt = 0L
        trim()
        return previous
    }

    fun redo(current: String): String? {
        val next = redo.pollLast() ?: return null
        retainedChars -= next.length
        undo.addLast(current)
        retainedChars += current.length
        lastChangeAt = 0L
        trim()
        return next
    }

    fun reset() {
        clear(undo)
        clear(redo)
        retainedChars = 0L
        lastChangeAt = 0L
    }

    fun canUndo(): Boolean = undo.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()

    private fun trim() {
        while (undo.size + redo.size > maxEntries || retainedChars > maxSnapshotChars) {
            val oldest = when {
                undo.isNotEmpty() -> undo.removeFirst()
                redo.isNotEmpty() -> redo.removeFirst()
                else -> break
            }
            retainedChars -= oldest.length
        }
    }

    private fun clear(stack: ArrayDeque<String>) {
        while (stack.isNotEmpty()) retainedChars -= stack.removeFirst().length
    }
}

object SearchReplace {
    fun findNext(text: String, query: String, from: Int): Int {
        if (query.isEmpty() || text.isEmpty()) return -1
        val start = from.coerceIn(0, text.length)
        return text.indexOf(query, start, ignoreCase = false).takeIf { it >= 0 }
            ?: text.indexOf(query, 0, ignoreCase = false)
    }

    fun replaceAll(text: String, search: String, replacement: String): Pair<String, Int> {
        if (search.isEmpty()) return text to 0
        var count = 0
        var offset = 0
        val output = StringBuilder(text.length)
        while (true) {
            val found = text.indexOf(search, offset)
            if (found < 0) {
                output.append(text, offset, text.length)
                break
            }
            output.append(text, offset, found).append(replacement)
            offset = found + search.length
            count++
        }
        return output.toString() to count
    }

    fun lineStart(text: String, oneBasedLine: Int): Int? {
        if (oneBasedLine < 1) return null
        if (oneBasedLine == 1) return 0
        var line = 1
        text.forEachIndexed { index, char ->
            if (char == '\n') {
                line++
                if (line == oneBasedLine) return index + 1
            }
        }
        return null
    }
}
