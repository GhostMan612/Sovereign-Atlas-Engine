// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import java.util.Locale

object MapNameFormatter {
    /**
     * Converts "baghdad_sector_4_1991.mbtiles" to "Baghdad Sector 4 1991"
     */
    fun format(filename: String): String {
        return filename
            .replace(Regex("(?i)\\.mbtiles$"), "")
            .replace(Regex("[-_]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase(Locale.ROOT) }
            }
            .trim()
    }
}
