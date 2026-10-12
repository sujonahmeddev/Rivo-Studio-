package com.rivo.studio.core.agent

/** Bounded, local line diff for the agent's review panel. It never applies edits. */
object DiffPreview {
    fun render(original: String?, proposed: String, maxLines: Int = 800): String {
        if (original == null) {
            val lines = proposed.split('\n')
            return buildString {
                append("--- /dev/null\n+++ proposed file\n")
                lines.take(maxLines).forEach { append("+ ").append(it).append('\n') }
                if (lines.size > maxLines) append("… diff preview capped …\n")
            }
        }
        if (original == proposed) return "No textual difference."
        val before = original.split('\n')
        val after = proposed.split('\n')
        if (before.size.toLong() * after.size.toLong() > MAX_COMPARISON_CELLS || before.size + after.size > maxLines) {
            return "--- current file (${before.size} lines)\n+++ proposed file (${after.size} lines)\nDiff preview simplified because this file is large. Review the complete proposed source before applying."
        }
        val lcs = Array(before.size + 1) { IntArray(after.size + 1) }
        for (i in before.lastIndex downTo 0) {
            for (j in after.lastIndex downTo 0) {
                lcs[i][j] = if (before[i] == after[j]) 1 + lcs[i + 1][j + 1]
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        return buildString {
            append("--- current file\n+++ proposed file\n")
            var i = 0
            var j = 0
            var emitted = 0
            while ((i < before.size || j < after.size) && emitted < maxLines) {
                when {
                    i < before.size && j < after.size && before[i] == after[j] -> {
                        append("  ").append(before[i]).append('\n')
                        i++
                        j++
                    }
                    i < before.size && (j == after.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> {
                        append("- ").append(before[i]).append('\n')
                        i++
                    }
                    j < after.size -> {
                        append("+ ").append(after[j]).append('\n')
                        j++
                    }
                }
                emitted++
            }
            if (i < before.size || j < after.size) append("… diff preview capped …\n")
        }
    }

    private const val MAX_COMPARISON_CELLS = 500_000L
}
