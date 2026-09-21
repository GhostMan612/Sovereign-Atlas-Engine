// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.model

data class BlueprintFeature(
    val blueprintId: String,
    val kind: String,
    val level: Int,
    val label: String,
    val geometryType: String,
    val coordsLngLat: List<List<Double>>,
)

data class ParcelGeometry(
    val parcelId: String,
    val label: String,
    val ringLngLat: List<List<Double>>,
    val geometryBasis: String,
    val tier: String,
    val sources: List<String>,
    val datum: String = "NAD83",
)
