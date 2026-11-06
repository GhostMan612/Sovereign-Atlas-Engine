// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

object KmlStreamingParser {

    const val MAX_VERTICES = 100_000

    fun parse(
        inputStream: InputStream,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "KML Layer",
        sourcePathOrUri: String? = null,
        isKmz: Boolean = false,
    ): GisDataset {
        val content = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return parseString(content, datasetId, datasetName, sourcePathOrUri, isKmz)
    }

    fun parseString(
        kmlText: String,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "KML Layer",
        sourcePathOrUri: String? = null,
        isKmz: Boolean = false,
    ): GisDataset {
        val features = ArrayList<GisFeature>()
        var vertexCount = 0

        var pos = 0
        val len = kmlText.length

        fun findTag(tag: String, startPos: Int): Pair<Int, Int>? {
            val open = "<$tag"
            val idx = kmlText.indexOf(open, startPos)
            if (idx == -1) return null
            val closeIdx = kmlText.indexOf('>', idx)
            if (closeIdx == -1) return null
            return idx to (closeIdx + 1)
        }

        fun extractTagContent(openTag: String, closeTag: String, text: String): String? {
            val start = text.indexOf("<$openTag")
            if (start == -1) return null
            val startClose = text.indexOf('>', start)
            if (startClose == -1) return null
            val end = text.indexOf("</$closeTag>", startClose)
            if (end == -1) return null
            return text.substring(startClose + 1, end).trim()
        }

        while (pos < len && vertexCount < MAX_VERTICES) {
            val placemarkOpen = findTag("Placemark", pos) ?: break
            val placemarkClose = kmlText.indexOf("</Placemark>", placemarkOpen.second)
            if (placemarkClose == -1) break

            val placemarkBlock = kmlText.substring(placemarkOpen.first, placemarkClose + 12)
            pos = placemarkClose + 12

            val name = extractTagContent("name", "name", placemarkBlock) ?: "Feature ${features.size + 1}"
            val description = extractTagContent("description", "description", placemarkBlock)

            val properties = HashMap<String, RenderProperty>()
            properties["name"] = RenderProperty.Text(name)
            if (description != null) {
                properties["description"] = RenderProperty.Text(description)
            }

            var extPos = 0
            while (extPos < placemarkBlock.length) {
                val dataOpen = placemarkBlock.indexOf("<Data name=\"", extPos)
                if (dataOpen == -1) break
                val nameEnd = placemarkBlock.indexOf('"', dataOpen + 12)
                if (nameEnd == -1) break
                val attrName = placemarkBlock.substring(dataOpen + 12, nameEnd)
                val valueOpen = placemarkBlock.indexOf("<value>", nameEnd)
                val valueClose = if (valueOpen != -1) placemarkBlock.indexOf("</value>", valueOpen) else -1
                if (valueOpen != -1 && valueClose != -1) {
                    val attrValue = placemarkBlock.substring(valueOpen + 7, valueClose).trim()
                    properties[attrName] = RenderProperty.Text(attrValue)
                    extPos = valueClose + 8
                } else {
                    extPos = nameEnd + 1
                }
            }

            val geometry = parsePlacemarkGeometry(placemarkBlock)
            if (geometry != null) {
                val geomVertices = geometry.allCoordinates().size
                if (vertexCount + geomVertices <= MAX_VERTICES) {
                    features.add(
                        GisFeature(
                            id = "kml-feature-${features.size}",
                            geometry = geometry,
                            properties = properties,
                        ),
                    )
                    vertexCount += geomVertices
                }
            }
        }

        return GisDataset(
            id = datasetId,
            name = datasetName,
            format = if (isKmz) GisFormat.KMZ else GisFormat.KML,
            srid = GisSrid.WGS84,
            featureCount = features.size,
            features = features,
            sourcePathOrUri = sourcePathOrUri,
        )
    }

    private fun parsePlacemarkGeometry(block: String): RenderGeometry? {
        val polyStart = block.indexOf("<Polygon")
        if (polyStart != -1) {
            val polyEnd = block.indexOf("</Polygon>", polyStart)
            if (polyEnd != -1) {
                val polyBlock = block.substring(polyStart, polyEnd + 10)
                val outerStart = polyBlock.indexOf("<outerBoundaryIs")
                if (outerStart != -1) {
                    val outerCoords = extractCoordinates(polyBlock, outerStart)
                    if (outerCoords.isNotEmpty()) {
                        val rings = ArrayList<List<LngLat>>()
                        val outerRing = ensureClosed(outerCoords)
                        rings.add(outerRing)

                        var innerPos = outerStart + 16
                        while (innerPos < polyBlock.length) {
                            val innerStart = polyBlock.indexOf("<innerBoundaryIs", innerPos)
                            if (innerStart == -1) break
                            val innerEnd = polyBlock.indexOf("</innerBoundaryIs>", innerStart)
                            if (innerEnd == -1) break
                            val innerCoords = extractCoordinates(polyBlock.substring(innerStart, innerEnd + 18), 0)
                            if (innerCoords.isNotEmpty()) {
                                rings.add(ensureClosed(innerCoords))
                            }
                            innerPos = innerEnd + 18
                        }

                        return RenderGeometry.Polygon(rings)
                    }
                }
            }
        }

        val lineStart = block.indexOf("<LineString")
        if (lineStart != -1) {
            val coords = extractCoordinates(block, lineStart)
            if (coords.size >= 2) {
                return RenderGeometry.LineString(coords)
            } else if (coords.size == 1) {
                return RenderGeometry.Point(coords.first())
            }
        }

        val pointStart = block.indexOf("<Point")
        if (pointStart != -1) {
            val coords = extractCoordinates(block, pointStart)
            if (coords.isNotEmpty()) {
                return RenderGeometry.Point(coords.first())
            }
        }

        return null
    }

    private fun extractCoordinates(text: String, fromIndex: Int): List<LngLat> {
        val coordStart = text.indexOf("<coordinates>", fromIndex)
        if (coordStart == -1) return emptyList()
        val coordEnd = text.indexOf("</coordinates>", coordStart)
        if (coordEnd == -1) return emptyList()

        val rawCoords = text.substring(coordStart + 13, coordEnd).trim()
        val tokens = rawCoords.split("\\s+".toRegex())
        val points = ArrayList<LngLat>(tokens.size)

        for (token in tokens) {
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].trim().toDoubleOrNull()
                val lat = parts[1].trim().toDoubleOrNull()
                if (lon != null && lat != null && lon.isFinite() && lat.isFinite()) {
                    if (lon in -180.0..180.0 && lat in -90.0..90.0) {
                        points.add(LngLat(lon, lat))
                    }
                }
            }
        }
        return points
    }

    private fun ensureClosed(ring: List<LngLat>): List<LngLat> {
        if (ring.isEmpty()) return ring
        val first = ring.first()
        val last = ring.last()
        return if (first.latitude != last.latitude || first.longitude != last.longitude) {
            ring + first
        } else {
            ring
        }
    }
}
