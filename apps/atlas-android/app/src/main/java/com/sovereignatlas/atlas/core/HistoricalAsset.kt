// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

/**
 * Provenance for a historical source. RULES 2.3 requires that licensing travel
 * with the data, and unknown beats invented: a source whose rights cannot be
 * established is [Unverified], never assumed to be open.
 */
sealed interface HistoricalLicense {
    /** No rights claim known or asserted. */
    data object PublicDomain : HistoricalLicense

    /** Deterministically released into the public domain, e.g. CC0. */
    data class PublicDomainDedicated(val dedication: String) : HistoricalLicense

    /**
     * Public domain in the United States only. Karst / historical US federal
     * survey material is frequently in this state, and calling it worldwide
     * Public Domain would be a fabricated claim.
     */
    data class PublicDomainUsOnly(val jurisdiction: String = "United States") : HistoricalLicense

    /** Terms are known but not yet classified. */
    data class Unverified(val note: String) : HistoricalLicense

    /**
     * True only when the asset is safe to redistribute worldwide. Callers must
     * not treat [PublicDomainUsOnly] or [Unverified] as redistributable.
     */
    val isRedistributable: Boolean
        get() = this is PublicDomain || this is PublicDomainDedicated
}

/**
 * A source of historical cartography or survey data.
 *
 * Pure domain model. No Android, MapLibre, or `java.net` types: the adapter
 * layer resolves an [asset] to a concrete URL and the tile pack that serves it.
 */
sealed interface HistoricalAsset {
    val id: String
    val title: String

    /**
     * Year of the source material, negative for BCE. A single Int because the
     * sources in scope (land patents, Sanborn volumes) are single-date sheets;
     * ranges belong on a separate type rather than as a sentinel value here.
     */
    val year: Int

    /** Extent of the material this source covers. */
    val boundingBox: AtlasBoundingBox

    /** Rights for this asset. Provenance travels with the data (RULES 2.3). */
    val license: HistoricalLicense

    /** Stable citation string for the source, or null when none is recorded. */
    val attribution: String?
}

/**
 * Vector GLO boundary data derived from a land patent survey.
 *
 * Geometry is vector, so an adapter may serve it from a pbf pack rather than an
 * MBTiles raster pack. The pattern is carried explicitly instead of inferred so
 * the pack-type decision stays in the adapter layer.
 */
data class LandPatent(
    override val id: String,
    override val title: String,
    override val year: Int,
    override val boundingBox: AtlasBoundingBox,
    override val license: HistoricalLicense,
    val patentNumber: String,
    val township: String? = null,
    val patenteeName: String? = null,
    val issueDate: String? = null,
    val acreage: Double? = null,
    val legalDescription: String? = null,
    val state: String? = null,
    val county: String? = null,
    val geometry: GeoJsonGeometry,
    override val attribution: String? = null,
) : HistoricalAsset {
    init {
        require(id.isNotBlank()) { "HistoricalAsset id must not be blank." }
        require(title.isNotBlank()) { "HistoricalAsset title must not be blank." }
        require(patentNumber.isNotBlank()) { "LandPatent patentNumber must not be blank." }
    }
}

/**
 * Raster building footprints from a Sanborn fire-insurance sheet.
 *
 * Sanborn sheets are raster and hand-drafted, so an adapter serves this from an
 * MBTiles pack. [edition] and [sheet] locate the leaf on the volume because a
 * city is typically split across many sheets.
 */
data class SanbornBlueprint(
    override val id: String,
    override val title: String,
    override val year: Int,
    override val boundingBox: AtlasBoundingBox,
    override val license: HistoricalLicense,
    override val attribution: String? = null,
    /** Volume/edition number within the Sanborn collection. */
    val edition: Int,
    /** Sheet identifier as printed on the leaf. */
    val sheet: String,
) : HistoricalAsset {
    init {
        require(id.isNotBlank()) { "HistoricalAsset id must not be blank." }
        require(title.isNotBlank()) { "HistoricalAsset title must not be blank." }
        require(edition > 0) { "Sanborn edition must be positive, was $edition." }
    }
}
