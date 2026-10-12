package com.rivo.studio.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER, CONSTANT, MARKUP, BRACKET }
private data class Token(val start: Int, val end: Int, val kind: TokenKind)

object LanguageDetector {
    fun fromPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "py", "pyw" -> "python"
        "js", "jsx", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "html", "htm", "xml", "svg" -> "markup"
        "css", "scss" -> "css"
        "json" -> "json"
        "java" -> "java"
        "kt", "kts" -> "kotlin"
        "c", "h" -> "c"
        "cc", "cpp", "cxx", "hpp" -> "cpp"
        "php" -> "php"
        "sql" -> "sql"
        "md", "markdown" -> "markdown"
        "sh", "bash", "zsh" -> "shell"
        else -> "plain"
    }
}

class SyntaxHighlightingTransformation(
    private val language: String,
    private val darkTheme: Boolean,
    private val caret: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Skip repeated full scans on unusually large buffers; editing remains available.
        if (text.length > MAX_HIGHLIGHT_CHARS || language == "plain") {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val tokens = tokenize(text.text, language)
        val matchingPair = findMatchingBracket(text.text, caret)
        val styled = AnnotatedString.Builder().apply { append(text) }
        tokens.forEach { token ->
            val color = colorFor(token.kind, darkTheme)
            val background = if (token.kind == TokenKind.BRACKET &&
                matchingPair?.let { token.start == it.first || token.start == it.second } == true
            ) if (darkTheme) Color(0xFF35544F) else Color(0xFFDDF1EC) else Color.Transparent
            styled.addStyle(SpanStyle(color = color, background = background), token.start, token.end)
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun colorFor(kind: TokenKind, dark: Boolean): Color = when (kind) {
        TokenKind.KEYWORD -> if (dark) Color(0xFFCCA6F7) else Color(0xFF7047A3)
        TokenKind.STRING -> if (dark) Color(0xFFB9D98C) else Color(0xFF587C2E)
        TokenKind.COMMENT -> if (dark) Color(0xFF7E928D) else Color(0xFF7C8984)
        TokenKind.NUMBER -> if (dark) Color(0xFFE2B477) else Color(0xFFAD641F)
        TokenKind.CONSTANT -> if (dark) Color(0xFF81C9DD) else Color(0xFF217B90)
        TokenKind.MARKUP -> if (dark) Color(0xFFF0A889) else Color(0xFFB64E3A)
        TokenKind.BRACKET -> if (dark) Color(0xFFB5C8C3) else Color(0xFF49605A)
    }

    private fun tokenize(source: String, language: String): List<Token> {
        val result = ArrayList<Token>()
        val keywords = keywordsFor(language)
        val constants = setOf("true", "false", "null", "none", "nil", "undefined", "self", "this", "super")
        var index = 0
        while (index < source.length) {
            val start = index
            val ch = source[index]

            if (language == "markup" && source.startsWith("<!--", index)) {
                val end = source.indexOf("-->", index + 4).let { if (it < 0) source.length else it + 3 }
                result += Token(index, end, TokenKind.COMMENT)
                index = end
                continue
            }

            val commentEnd = when {
                source.startsWith("//", index) && language in setOf("javascript", "typescript", "java", "kotlin", "c", "cpp", "css", "php") -> lineEnd(source, index)
                source.startsWith("/*", index) && language in setOf("javascript", "typescript", "java", "kotlin", "c", "cpp", "css", "php") -> {
                    source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                }
                ch == '#' && language in setOf("python", "shell", "css") -> lineEnd(source, index)
                source.startsWith("--", index) && language == "sql" -> lineEnd(source, index)
                else -> -1
            }
            if (commentEnd >= 0) {
                result += Token(index, commentEnd, TokenKind.COMMENT)
                index = commentEnd
                continue
            }

            if (ch == '\'' || ch == '"' || (ch == '`' && language in setOf("javascript", "typescript"))) {
                val triple = language == "python" && index + 2 < source.length &&
                    source[index + 1] == ch && source[index + 2] == ch
                val quoteWidth = if (triple) 3 else 1
                index += quoteWidth
                var closed = false
                while (index < source.length) {
                    if (source[index] == '\\') {
                        index = (index + 2).coerceAtMost(source.length)
                        continue
                    }
                    if (triple && source.startsWith("$ch$ch$ch", index)) {
                        index += 3
                        closed = true
                        break
                    }
                    if (!triple && source[index] == ch) {
                        index++
                        closed = true
                        break
                    }
                    index++
                }
                if (!closed) index = source.length
                result += Token(start, index, TokenKind.STRING)
                continue
            }

            if (language == "markup" && ch == '<' && index + 1 < source.length &&
                (source[index + 1].isLetter() || source[index + 1] in "/!?")) {
                var inQuote: Char? = null
                index++
                while (index < source.length) {
                    val current = source[index]
                    if (inQuote != null) {
                        if (current == inQuote) inQuote = null
                    } else if (current == '\'' || current == '"') {
                        inQuote = current
                    } else if (current == '>') {
                        index++
                        break
                    }
                    index++
                }
                result += Token(start, index, TokenKind.MARKUP)
                continue
            }

            if (ch.isDigit()) {
                index++
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "._")) index++
                result += Token(start, index, TokenKind.NUMBER)
                continue
            }

            if (ch == '_' || ch == '$' || ch.isLetter()) {
                index++
                while (index < source.length && (source[index] == '_' || source[index] == '$' || source[index].isLetterOrDigit())) index++
                val word = source.substring(start, index)
                val kind = when {
                    word in keywords -> TokenKind.KEYWORD
                    word.lowercase() in constants -> TokenKind.CONSTANT
                    else -> null
                }
                if (kind != null) result += Token(start, index, kind)
                continue
            }

            if (ch in "()[]{}") result += Token(index, index + 1, TokenKind.BRACKET)
            index++
        }
        return result
    }

    private fun lineEnd(source: String, from: Int): Int = source.indexOf('\n', from).let { if (it < 0) source.length else it }

    private fun keywordsFor(language: String): Set<String> = when (language) {
        "python" -> setOf("and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield")
        "javascript", "typescript" -> setOf("async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends", "finally", "for", "from", "function", "if", "import", "in", "instanceof", "let", "new", "of", "return", "static", "super", "switch", "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield", "interface", "type", "enum", "implements", "public", "private", "readonly", "namespace")
        "java", "kotlin", "c", "cpp", "php" -> setOf("abstract", "as", "assert", "auto", "break", "case", "catch", "class", "const", "continue", "default", "delete", "do", "else", "enum", "extends", "extern", "final", "finally", "for", "foreach", "from", "fun", "function", "if", "implements", "import", "in", "interface", "let", "namespace", "native", "new", "null", "override", "package", "private", "protected", "public", "return", "static", "struct", "switch", "template", "throw", "try", "typedef", "typename", "using", "var", "void", "volatile", "while", "val", "when", "object", "companion", "suspend", "operator")
        "sql" -> setOf("add", "alter", "and", "as", "asc", "between", "by", "case", "create", "delete", "desc", "distinct", "drop", "else", "end", "from", "group", "having", "insert", "into", "is", "join", "limit", "not", "null", "on", "or", "order", "select", "set", "table", "then", "update", "values", "when", "where")
        "css" -> setOf("important", "media", "import", "supports", "keyframes", "from", "to")
        "shell" -> setOf("case", "do", "done", "elif", "else", "esac", "fi", "for", "function", "if", "in", "select", "then", "until", "while")
        "json" -> setOf("true", "false", "null")
        else -> emptySet()
    }.map { it.lowercase() }.toSet()

    private fun findMatchingBracket(source: String, caret: Int): Pair<Int, Int>? {
        if (source.isEmpty()) return null
        val candidate = listOf(caret - 1, caret).firstOrNull { it in source.indices && source[it] in "()[]{}" } ?: return null
        val ch = source[candidate]
        val close = when (ch) { '(' -> ')'; '[' -> ']'; '{' -> '}'; else -> null }
        val open = when (ch) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> null }
        if (close != null) {
            var depth = 0
            for (index in candidate until source.length) {
                when (source[index]) {
                    ch -> depth++
                    close -> if (--depth == 0) return candidate to index
                }
            }
        } else if (open != null) {
            var depth = 0
            for (index in candidate downTo 0) {
                when (source[index]) {
                    ch -> depth++
                    open -> if (--depth == 0) return index to candidate
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_HIGHLIGHT_CHARS = 512 * 1024
    }
}
