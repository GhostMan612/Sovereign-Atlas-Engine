// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core.knowledge

import com.sovereignatlas.atlas.core.AtlasBoundingBox

enum class EntityType {
    PLACE,
    STRUCTURE,
    EVENT,
    BOUNDARY,
    PARCEL,
    PERSON,
}

enum class ConfidenceScore {
    CONFIRMED,
    PROBABLE,
    UNCERTAIN,
    DISPUTED,
}

data class TemporalInterval(
    val validFromYear: Int?,
    val validToYear: Int?,
    val exactDateIso: String? = null,
) {
    fun containsYear(year: Int): Boolean {
        val afterFrom = validFromYear == null || year >= validFromYear
        val beforeTo = validToYear == null || year <= validToYear
        return afterFrom && beforeTo
    }
}

data class SourceCitation(
    val citationId: String,
    val title: String,
    val authorOrArchive: String,
    val publicationYear: Int?,
    val urlOrShelfmark: String,
    val license: String,
)

data class ConflictingClaim(
    val conflictingSource: SourceCitation,
    val claimDescription: String,
)

data class HistoricalDossier(
    val id: String,
    val name: String,
    val entityType: EntityType,
    val temporal: TemporalInterval,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val confidence: ConfidenceScore,
    val primaryCitation: SourceCitation,
    val alternativeClaims: List<ConflictingClaim> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    fun isInBounds(box: AtlasBoundingBox): Boolean {
        return latitude in box.south..box.north && longitude in box.west..box.east
    }
}
