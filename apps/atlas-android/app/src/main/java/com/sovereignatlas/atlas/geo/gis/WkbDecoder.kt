// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WkbDecoder {

    const val GPKG_MAGIC_0 = 0x47.toByte()
    const val GPKG_MAGIC_1 = 0x50.toByte()

    fun isGeoPackageBinary(bytes: ByteArray): Boolean {
        return bytes.size >= 8 && bytes[0] == GPKG_MAGIC_0 && bytes[1] == GPKG_MAGIC_1
    }

    fun extractGpkgSrsId(bytes: ByteArray): Int {
        if (!isGeoPackageBinary(bytes)) return 4326
        val flags = bytes[3].toInt()
        val isLittleEndian = (flags and 0x01) == 1
        val buffer = ByteBuffer.wrap(bytes, 4, 4)
        buffer.order(if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        return buffer.int
    }

    fun decode(bytes: ByteArray): RenderGeometry? {
        if (bytes.isEmpty()) return null
        val (offset, _) = if (isGeoPackageBinary(bytes)) {
            val flags = bytes[3].toInt()
            val envelopeIndicator = (flags shr 1) and 0x07
            val envelopeSize = when (envelopeIndicator) {
                0 -> 0
                1 -> 32
                2 -> 48
                3 -> 48
                4 -> 64
                else -> 0
            }
            val headerLen = 8 + envelopeSize
            val isLittleEndian = (flags and 0x01) == 1
            val buffer = ByteBuffer.wrap(bytes, 4, 4)
            buffer.order(if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            val srs = buffer.int
            headerLen to srs
        } else {
            0 to 4326
        }

        if (offset >= bytes.size) return null
        val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
        return parseGeometry(buffer)
    }

    private fun parseGeometry(buffer: ByteBuffer): RenderGeometry? {
        if (buffer.remaining() < 5) return null
        val byteOrderByte = buffer.get()
        buffer.order(if (byteOrderByte.toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        val rawType = buffer.int
        val hasEwkbSrid = (rawType and 0x20000000) != 0
        val isEwkbZ = (rawType and 0x80000000.toInt()) != 0
        val isEwkbM = (rawType and 0x40000000) != 0

        val maskedType = rawType and 0x1FFFFFFF
        val baseType = maskedType % 1000
        val isIsoZ = maskedType in 1000..1999 || maskedType in 3000..3999
        val isIsoM = maskedType in 2000..2999 || maskedType in 3000..3999

        val hasZ = isEwkbZ || isIsoZ
        val hasM = isEwkbM || isIsoM

        if (hasEwkbSrid) {
            if (buffer.remaining() < 4) return null
            buffer.int
        }

        return when (baseType) {
            1 -> parsePoint(buffer, hasZ, hasM)
            2 -> parseLineString(buffer, hasZ, hasM)
            3 -> parsePolygon(buffer, hasZ, hasM)
            4 -> parseMultiPoint(buffer)
            5 -> parseMultiLineString(buffer)
            6 -> parseMultiPolygon(buffer)
            7 -> parseGeometryCollection(buffer)
            else -> null
        }
    }

    private fun readPointCoords(buffer: ByteBuffer, hasZ: Boolean, hasM: Boolean): LngLat {
        val x = buffer.double
        val y = buffer.double
        if (hasZ) buffer.double
        if (hasM) buffer.double
        return LngLat(x, y)
    }

    private fun parsePoint(buffer: ByteBuffer, hasZ: Boolean, hasM: Boolean): RenderGeometry.Point? {
        val coordSize = 16 + (if (hasZ) 8 else 0) + (if (hasM) 8 else 0)
        if (buffer.remaining() < coordSize) return null
        val pt = readPointCoords(buffer, hasZ, hasM)
        return RenderGeometry.Point(pt)
    }

    private fun parseLineString(buffer: ByteBuffer, hasZ: Boolean, hasM: Boolean): RenderGeometry? {
        if (buffer.remaining() < 4) return null
        val numPoints = buffer.int
        if (numPoints <= 0) return null
        val coordSize = 16 + (if (hasZ) 8 else 0) + (if (hasM) 8 else 0)
        if (buffer.remaining() < numPoints * coordSize) return null

        val points = ArrayList<LngLat>(numPoints)
        for (i in 0 until numPoints) {
            points.add(readPointCoords(buffer, hasZ, hasM))
        }

        return if (points.size >= 2) {
            RenderGeometry.LineString(points)
        } else {
            RenderGeometry.Point(points.first())
        }
    }

    private fun parsePolygon(buffer: ByteBuffer, hasZ: Boolean, hasM: Boolean): RenderGeometry.Polygon? {
        if (buffer.remaining() < 4) return null
        val numRings = buffer.int
        if (numRings <= 0) return null

        val rings = ArrayList<List<LngLat>>(numRings)
        val coordSize = 16 + (if (hasZ) 8 else 0) + (if (hasM) 8 else 0)

        for (r in 0 until numRings) {
            if (buffer.remaining() < 4) return null
            val numPoints = buffer.int
            if (numPoints <= 0 || buffer.remaining() < numPoints * coordSize) return null
            val ringPoints = ArrayList<LngLat>(numPoints)
            for (p in 0 until numPoints) {
                ringPoints.add(readPointCoords(buffer, hasZ, hasM))
            }
            if (ringPoints.isNotEmpty()) {
                val first = ringPoints.first()
                val last = ringPoints.last()
                if (first != last) {
                    ringPoints.add(first)
                }
                rings.add(ringPoints)
            }
        }

        if (rings.isEmpty()) return null
        return RenderGeometry.Polygon(rings)
    }

    private fun parseMultiPoint(buffer: ByteBuffer): RenderGeometry? {
        if (buffer.remaining() < 4) return null
        val numGeoms = buffer.int
        if (numGeoms <= 0) return null
        val points = ArrayList<LngLat>(numGeoms)
        for (i in 0 until numGeoms) {
            val geom = parseGeometry(buffer)
            if (geom is RenderGeometry.Point) {
                points.add(geom.coordinates)
            }
        }
        return if (points.isNotEmpty()) {
            RenderGeometry.Point(points.first())
        } else {
            null
        }
    }

    private fun parseMultiLineString(buffer: ByteBuffer): RenderGeometry? {
        if (buffer.remaining() < 4) return null
        val numGeoms = buffer.int
        if (numGeoms <= 0) return null
        val lines = ArrayList<RenderGeometry.LineString>(numGeoms)
        for (i in 0 until numGeoms) {
            val geom = parseGeometry(buffer)
            if (geom is RenderGeometry.LineString) {
                lines.add(geom)
            }
        }
        return if (lines.isNotEmpty()) {
            lines.first()
        } else {
            null
        }
    }

    private fun parseMultiPolygon(buffer: ByteBuffer): RenderGeometry.MultiPolygon? {
        if (buffer.remaining() < 4) return null
        val numGeoms = buffer.int
        if (numGeoms <= 0) return null
        val polygons = ArrayList<List<List<LngLat>>>(numGeoms)
        for (i in 0 until numGeoms) {
            val geom = parseGeometry(buffer)
            if (geom is RenderGeometry.Polygon) {
                polygons.add(geom.coordinates)
            }
        }
        if (polygons.isEmpty()) return null
        return RenderGeometry.MultiPolygon(polygons)
    }

    private fun parseGeometryCollection(buffer: ByteBuffer): RenderGeometry? {
        if (buffer.remaining() < 4) return null
        val numGeoms = buffer.int
        if (numGeoms <= 0) return null
        for (i in 0 until numGeoms) {
            val geom = parseGeometry(buffer)
            if (geom != null) return geom
        }
        return null
    }
}
