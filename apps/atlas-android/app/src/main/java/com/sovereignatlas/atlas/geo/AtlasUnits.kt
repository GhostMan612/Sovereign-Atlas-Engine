// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

object AtlasLengthUnits {
    const val METERS_PER_KILOMETER = 1000.0
    const val METERS_PER_MILE = 1609.344
    const val METERS_PER_FOOT = 0.3048
    const val METERS_PER_NAUTICAL_MILE = 1852.0

    const val EARTH_MEAN_RADIUS_METERS = 6371008.8

    fun toKilometers(meters: Double): Double = meters / METERS_PER_KILOMETER
    fun toMiles(meters: Double): Double = meters / METERS_PER_MILE
    fun toFeet(meters: Double): Double = meters / METERS_PER_FOOT
    fun toNauticalMiles(meters: Double): Double = meters / METERS_PER_NAUTICAL_MILE

    fun fromKilometers(v: Double): Double = v * METERS_PER_KILOMETER
    fun fromMiles(v: Double): Double = v * METERS_PER_MILE
    fun fromFeet(v: Double): Double = v * METERS_PER_FOOT
    fun fromNauticalMiles(v: Double): Double = v * METERS_PER_NAUTICAL_MILE

    fun degreesPerMeter(): Double =
        360.0 / (2.0 * Math.PI * EARTH_MEAN_RADIUS_METERS)
}
