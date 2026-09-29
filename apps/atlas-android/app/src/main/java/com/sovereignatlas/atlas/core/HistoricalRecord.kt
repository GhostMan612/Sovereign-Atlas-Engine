// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

/**
 * Pure-logic representation of a tapped historical feature (patent, blueprint, or parcel).
 */
data class HistoricalRecord(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val sourceLayer: String
)
