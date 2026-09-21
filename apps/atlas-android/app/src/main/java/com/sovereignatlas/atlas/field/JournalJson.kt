// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

object JournalJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWs()
        if (!reader.atEnd()) {
            throw IllegalArgumentException("journal JSON has trailing content")
        }
        return value
    }

    fun render(value: Any?): String {
        val out = StringBuilder()
        writeValue(out, value)
        return out.toString()
    }

    private fun writeValue(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Long, is Int -> out.append(value.toString())
            is Double -> out.append(value.toString())
            is Float -> out.append(value.toDouble().toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, entry) in value.entries) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    writeValue(out, entry)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                var first = true
                for (entry in value) {
                    if (!first) out.append(',')
                    first = false
                    writeValue(out, entry)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException(
                "journal JSON cannot render ${value::class.java.name}",
            )
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (c < ' ') {
                        out.append("\\u" + c.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(c)
                    }
                }
            }
        }
        out.append('"')
    }

    private class Reader(private val text: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWs() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun fail(message: String): Nothing {
            throw IllegalArgumentException("journal JSON malformed at $pos: $message")
        }

        private fun peek(): Char {
            if (pos >= text.length) fail("unexpected end")
            return text[pos]
        }

        fun readValue(): Any? {
            skipWs()
            return when (peek()) {
                '{' -> readObj()
                '[' -> readArr()
                '"' -> readStr()
                't' -> {
                    expect("true")
                    true
                }
                'f' -> {
                    expect("false")
                    false
                }
                'n' -> {
                    expect("null")
                    null
                }
                else -> readNum()
            }
        }

        private fun expect(word: String) {
            if (!text.startsWith(word, pos)) fail("expected $word")
            pos += word.length
        }

        private fun readObj(): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                if (peek() != '"') fail("expected key")
                val key = readStr()
                skipWs()
                if (peek() != ':') fail("expected colon")
                pos++
                map[key] = readValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> fail("expected comma or brace")
                }
            }
        }

        private fun readArr(): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(readValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> fail("expected comma or bracket")
                }
            }
        }

        private fun readStr(): String {
            pos++
            val out = StringBuilder()
            while (true) {
                if (pos >= text.length) fail("unterminated string")
                val c = text[pos++]
                if (c == '"') return out.toString()
                if (c != '\\') {
                    out.append(c)
                    continue
                }
                if (pos >= text.length) fail("unterminated escape")
                when (val e = text[pos++]) {
                    '"', '\\', '/' -> out.append(e)
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (pos + 4 > text.length) fail("bad unicode escape")
                        val code = text.substring(pos, pos + 4).toIntOrNull(16)
                            ?: fail("bad unicode escape")
                        pos += 4
                        out.append(code.toChar())
                    }
                    else -> fail("bad escape")
                }
            }
        }

        private fun readNum(): Number {
            val start = pos
            if (peek() == '-') pos++
            var floating = false
            while (pos < text.length) {
                val c = text[pos]
                if (c.isDigit()) {
                    pos++
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = true
                    pos++
                } else {
                    break
                }
            }
            val raw = text.substring(start, pos)
            if (!floating) {
                try {
                    return raw.toLong()
                } catch (error: NumberFormatException) {
                }
            }
            return raw.toDoubleOrNull() ?: fail("bad number $raw")
        }
    }
}
