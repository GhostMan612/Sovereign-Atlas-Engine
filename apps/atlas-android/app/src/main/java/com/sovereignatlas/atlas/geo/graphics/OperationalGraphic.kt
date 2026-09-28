// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.graphics

import com.sovereignatlas.atlas.geo.AtlasCoordinate

enum class ZoneType(val hexColor: String, val alpha: Float) {
    MEDEVAC("#00FF00", 0.3f),
    RESTRICTED("#FF0000", 0.3f),
    OBJECTIVE("#FFA500", 0.3f)
}

sealed class OperationalGraphic {
    abstract val id: String

    data class TacticalLine(
        override val id: String,
        val points: List<AtlasCoordinate>,
        val colorHex: String = "#FFFF00",
        val width: Float = 3.0f
    ) : OperationalGraphic()

    data class TacticalZone(
        override val id: String,
        val points: List<AtlasCoordinate>,
        val type: ZoneType
    ) : OperationalGraphic()
}
