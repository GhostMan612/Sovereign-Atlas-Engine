// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

/**
 * A geographic point with optional live telemetry.
 *
 * [altitude] is metres, nullable: null means "not known", never zero. A zero
 * altitude at Null Island is a claim about the sea surface, and treating an absent
 * reading as zero is how a location fix gets reported as precise when it is not.
 *
 * Carries no coordinate system. Every pure-logic type here is WGS84 decimal
 * degrees, enforced by `AtlasCoordinates.validate`; a second `crs` field would
 * imply a flexibility the engine does not have.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val bearing: Float?,
    val speed: Float?,
    val timestamp: Long,
)

/**
 * A request to cast a line of sight between two points.
 *
 * Lives here rather than in `geo/los/` because the map's two-tap tool and the
 * long-press analysis sheet both construct one, and moving it would mean editing
 * call sites to relocate a four-field struct.
 */
data class LoSRequest(
    val observer: GeoPoint,
    val target: GeoPoint,
    val observerHeightMeters: Double = 2.0,
    val targetHeightMeters: Double = 2.0,
)