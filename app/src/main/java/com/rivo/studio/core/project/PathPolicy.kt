package com.rivo.studio.core.project

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object PathPolicy {
    fun normalizeRelativePath(input: String, allowEmpty: Boolean = false): String {
        require(input.indexOf('\u0000') < 0) { "A path cannot contain a null character." }
        require('\\' !in input) { "Use forward slashes in project paths." }
        require(!input.startsWith('/')) { "Absolute paths are not allowed in a project." }
        if (input.isEmpty()) {
            require(allowEmpty) { "A file path is required." }
            return ""
        }
        val segments = input.split('/')
        require(segments.none { it.isEmpty() || it == "." || it == ".." }) {
            "The path contains an empty or unsafe segment."
        }
        segments.forEach(::validateSegment)
        return segments.joinToString("/")
    }

    fun validateFileName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean != "." && clean != "..") { "Enter a file or folder name." }
        require(clean.length <= 180) { "Names must be 180 characters or fewer." }
        validateSegment(clean)
        return clean
    }

    fun safeArchivePath(raw: String): String {
        val normalizedSeparators = raw.replace('\\', '/')
        val withoutTrailingSlash = normalizedSeparators.trimEnd('/')
        return normalizeRelativePath(withoutTrailingSlash)
    }

    private fun validateSegment(segment: String) {
        require(segment.isNotBlank()) { "A path segment cannot be blank." }
        require(segment.length <= 180) { "Path segments must be 180 characters or fewer." }
        require(segment != "." && segment != "..") { "Parent path segments are not allowed." }
        require(segment.none { it.code < 0x20 || it in "<>:\"|?*" }) {
            "The name contains a reserved character."
        }
        require(!segment.endsWith('.') && !segment.endsWith(' ')) {
            "Names cannot end with a dot or space."
        }
    }
}

enum class TextEncoding(val charset: Charset, val bom: ByteArray) {
    UTF8(StandardCharsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
    UTF16_LE(StandardCharsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
    UTF16_BE(StandardCharsets.UTF_16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
    WINDOWS_1252(Charset.forName("windows-1252"), byteArrayOf());

    companion object {
        fun fromName(name: String): TextEncoding = entries.firstOrNull { it.charset.name().equals(name, true) }
            ?: UTF8

        fun decode(bytes: ByteArray): Pair<String, Pair<TextEncoding, Boolean>> {
            val encoding: TextEncoding
            val offset: Int
            val hasBom: Boolean
            when {
                bytes.startsWith(UTF8.bom) -> { encoding = UTF8; offset = UTF8.bom.size; hasBom = true }
                bytes.startsWith(UTF16_LE.bom) -> { encoding = UTF16_LE; offset = UTF16_LE.bom.size; hasBom = true }
                bytes.startsWith(UTF16_BE.bom) -> { encoding = UTF16_BE; offset = UTF16_BE.bom.size; hasBom = true }
                else -> { encoding = UTF8; offset = 0; hasBom = false }
            }
            val payload = bytes.copyOfRange(offset, bytes.size)
            if (!hasBom && payload.any { it == 0.toByte() }) {
                throw IllegalArgumentException("This appears to be a binary file, not editable text.")
            }
            val decoded = try {
                encoding.charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload)).toString() to (encoding to hasBom)
            } catch (_: CharacterCodingException) {
                require(!hasBom) { "The file has a byte-order mark but contains invalid ${encoding.charset.name()} text." }
                val fallback = WINDOWS_1252
                fallback.charset.decode(ByteBuffer.wrap(payload)).toString() to (fallback to false)
            }
            return decoded
        }

        fun encode(text: String, encoding: TextEncoding, hasBom: Boolean): ByteArray {
            val encoded = try {
                encoding.charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(text))
            } catch (_: CharacterCodingException) {
                throw IllegalArgumentException(
                    "This text cannot be represented as ${encoding.charset.name()}. Convert the file to UTF-8 before saving.",
                )
            }
            val body = ByteArray(encoded.remaining()).also(encoded::get)
            val prefix = if (hasBom && encoding.bom.isNotEmpty()) encoding.bom else byteArrayOf()
            return prefix + body
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
