// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.parity

sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

final class GoldenJson private constructor(private val text: String) {
    private var pos = 0

    private fun fail(message: String): Nothing {
        throw IllegalArgumentException("golden JSON malformed at $pos: $message")
    }

    private fun skipWs() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char {
        if (pos >= text.length) fail("unexpected end")
        return text[pos]
    }

    fun readValue(): JsonValue {
        skipWs()
        return when (peek()) {
            '{' -> readObj()
            '[' -> readArr()
            '"' -> JsonValue.Str(readStr())
            't' -> {
                expect("true")
                JsonValue.Bool(true)
            }
            'f' -> {
                expect("false")
                JsonValue.Bool(false)
            }
            'n' -> {
                expect("null")
                JsonValue.Null
            }
            else -> readNum()
        }
    }

    private fun expect(word: String) {
        if (!text.startsWith(word, pos)) fail("expected $word")
        pos += word.length
    }

    private fun readObj(): JsonValue.Obj {
        pos++
        val map = LinkedHashMap<String, JsonValue>()
        skipWs()
        if (peek() == '}') {
            pos++
            return JsonValue.Obj(map)
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
                    return JsonValue.Obj(map)
                }
                else -> fail("expected comma or brace")
            }
        }
    }

    private fun readArr(): JsonValue.Arr {
        pos++
        val list = ArrayList<JsonValue>()
        skipWs()
        if (peek() == ']') {
            pos++
            return JsonValue.Arr(list)
        }
        while (true) {
            list.add(readValue())
            skipWs()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return JsonValue.Arr(list)
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

    private fun readNum(): JsonValue.Num {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' ||
                    text[pos] == 'e' || text[pos] == 'E' ||
                    text[pos] == '+' || text[pos] == '-')
        ) {
            pos++
        }
        val raw = text.substring(start, pos)
        return JsonValue.Num(raw.toDoubleOrNull() ?: fail("bad number $raw"))
    }

    companion object {
        fun parse(text: String): JsonValue {
            val reader = GoldenJson(text)
            val value = reader.readValue()
            reader.skipWs()
            if (reader.pos != text.length) {
                reader.fail("trailing content")
            }
            return value
        }
    }
}
