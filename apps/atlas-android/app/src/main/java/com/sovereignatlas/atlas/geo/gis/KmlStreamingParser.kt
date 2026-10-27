// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.UUID

object KmlStreamingParser {

    const val MAX_VERTICES = 100_000

    private const val READ_CHUNK_SIZE = 8192
    private const val MAX_TAG_TAIL = 256

    fun parse(
        inputStream: InputStream,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "KML Layer",
        sourcePathOrUri: String? = null,
        isKmz: Boolean = false,
    ): GisDataset {
        val reader = inputStream.bufferedReader(StandardCharsets.UTF_8)
        return parseFromReader(reader, datasetId, datasetName, sourcePathOrUri, isKmz)
    }

    fun parseString(
        kmlText: String,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "KML Layer",
        sourcePathOrUri: String? = null,
        isKmz: Boolean = false,
    ): GisDataset {
        return parseFromReader(StringReader(kmlText), datasetId, datasetName, sourcePathOrUri, isKmz)
    }

    private fun parseFromReader(
        reader: Reader,
        datasetId: String,
        datasetName: String,
        sourcePathOrUri: String?,
        isKmz: Boolean,
    ): GisDataset {
        val features = ArrayList<GisFeature>()
        var vertexCount = 0
        val buffer = StringBuilder()
        val chunk = CharArray(READ_CHUNK_SIZE)
        var endOfInput = false
        while (!endOfInput && vertexCount < MAX_VERTICES) {
            val read = reader.read(chunk)
            if (read == -1) {
                endOfInput = true
            } else if (read > 0) {
                buffer.append(String(chunk, 0, read))
            }
            while (vertexCount < MAX_VERTICES) {
                val block = extractNextPlacemarkBlock(buffer, endOfInput) ?: break
                val placemark = parsePlacemarkBlock(block, features.size)
                for (geometry in placemark.geometries) {
                    val geometryVertices = geometry.allCoordinates().size
                    if (geometryVertices == 0) continue
                    if (vertexCount + geometryVertices > MAX_VERTICES) continue
                    features.add(
                        GisFeature(
                            id = "kml-feature-${features.size}",
                            geometry = geometry,
                            properties = placemark.properties,
                        ),
                    )
                    vertexCount += geometryVertices
                }
            }
            if (!endOfInput) {
                discardBeforePlacemarkStart(buffer)
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

    private fun extractNextPlacemarkBlock(buffer: StringBuilder, endOfInput: Boolean): String? {
        val text = buffer.toString()
        val start = findOpeningTag(text, "Placemark", 0) ?: return null
        val closing = findClosingTag(text, "Placemark", start.second)
        if (closing == null) {
            if (endOfInput) {
                buffer.setLength(0)
            }
            return null
        }
        val block = text.substring(start.first, closing.second)
        buffer.delete(0, closing.second)
        return block
    }

    private fun discardBeforePlacemarkStart(buffer: StringBuilder) {
        val text = buffer.toString()
        val start = findOpeningTag(text, "Placemark", 0)
        if (start == null) {
            if (buffer.length > MAX_TAG_TAIL) {
                buffer.delete(0, buffer.length - MAX_TAG_TAIL)
            }
        } else if (start.first > 0) {
            buffer.delete(0, start.first)
        }
    }

    private class ElementRange(
        val start: Int,
        val contentStart: Int,
        val closingStart: Int,
        val end: Int,
        val selfClosing: Boolean,
    )

    private fun findOpeningTag(
        text: String,
        localName: String,
        fromIndex: Int,
    ): ElementRange? {
        var index = fromIndex
        while (index < text.length) {
            val lt = text.indexOf('<', index)
            if (lt == -1) return null
            if (text.startsWith("<!--", lt)) {
                val commentEnd = text.indexOf("-->", lt + 4)
                index = if (commentEnd == -1) text.length else commentEnd + 3
                continue
            }
            if (lt + 1 < text.length && (text[lt + 1] == '/' || text[lt + 1] == '!' || text[lt + 1] == '?')) {
                index = lt + 2
                continue
            }
            val parsed = parseTagName(text, lt) ?: return null
            if (parsed.first == localName) {
                return ElementRange(lt, parsed.second, -1, parsed.second, parsed.third)
            }
            index = parsed.second
        }
        return null
    }

    private fun findClosingTag(
        text: String,
        localName: String,
        fromIndex: Int,
    ): ElementRange? {
        var index = fromIndex
        while (index < text.length) {
            val lt = text.indexOf("</", index)
            if (lt == -1) return null
            val parsed = parseTagName(text, lt + 1) ?: return null
            if (parsed.first == localName) {
                return ElementRange(lt, -1, lt, parsed.second, false)
            }
            index = parsed.second
        }
        return null
    }

    private fun parseTagName(text: String, ltIndex: Int): Triple<String, Int, Boolean>? {
        var index = ltIndex + 1
        if (index < text.length && text[index] == '/') index += 1
        val nameStart = index
        while (index < text.length) {
            val ch = text[index]
            if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '-' || ch == ':') {
                index += 1
            } else {
                break
            }
        }
        if (index == nameStart) {
            val gt = text.indexOf('>', index)
            if (gt == -1) return null
            val selfClosing = gt > index && text[gt - 1] == '/'
            return Triple("", gt + 1, selfClosing)
        }
        val qualifiedName = text.substring(nameStart, index)
        val localName = qualifiedName.substringAfter(':')
        val gt = text.indexOf('>', index)
        if (gt == -1) return null
        val selfClosing = gt > index && text[gt - 1] == '/'
        return Triple(localName, gt + 1, selfClosing)
    }

    private fun extractElement(
        text: String,
        localName: String,
        fromIndex: Int,
    ): ElementRange? {
        val opening = findOpeningTag(text, localName, fromIndex) ?: return null
        if (opening.selfClosing) {
            return ElementRange(opening.start, opening.contentStart, opening.contentStart, opening.end, true)
        }
        val closing = findClosingTag(text, localName, opening.contentStart) ?: return null
        return ElementRange(opening.start, opening.contentStart, closing.start, closing.end, false)
    }

    private fun elementText(text: String, localName: String, fromIndex: Int = 0): String? {
        val element = extractElement(text, localName, fromIndex) ?: return null
        if (element.selfClosing) return ""
        val raw = text.substring(element.contentStart, element.closingStart)
        return decodeTextContent(raw).trim()
    }

    private fun decodeTextContent(raw: String): String {
        if (!raw.contains('<') && !raw.contains('&')) return raw
        val out = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val cdataStart = raw.indexOf("<![CDATA[", index)
            if (cdataStart == -1) {
                out.append(decodeXmlEntities(raw.substring(index)))
                break
            }
            out.append(decodeXmlEntities(raw.substring(index, cdataStart)))
            val cdataEnd = raw.indexOf("]]>", cdataStart + 9)
            if (cdataEnd == -1) {
                out.append(raw.substring(cdataStart + 9))
                break
            }
            out.append(raw.substring(cdataStart + 9, cdataEnd))
            index = cdataEnd + 3
        }
        return out.toString()
    }

    private fun decodeXmlEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        val out = StringBuilder()
        var index = 0
        while (index < raw.length) {
            val amp = raw.indexOf('&', index)
            if (amp == -1) {
                out.append(raw, index, raw.length)
                break
            }
            out.append(raw, index, amp)
            val semi = raw.indexOf(';', amp)
            if (semi == -1 || semi - amp > 10) {
                out.append('&')
                index = amp + 1
                continue
            }
            val entity = raw.substring(amp + 1, semi)
            val named = when (entity) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> null
            }
            if (named != null) {
                out.append(named)
                index = semi + 1
                continue
            }
            val codePoint = when {
                entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)
                entity.startsWith("#") -> entity.substring(1).toIntOrNull()
                else -> null
            }
            if (codePoint != null && codePoint in 0..0x10FFFF) {
                out.append(String(Character.toChars(codePoint)))
                index = semi + 1
            } else {
                out.append(raw, amp, semi + 1)
                index = semi + 1
            }
        }
        return out.toString()
    }

    private class ParsedPlacemark(
        val properties: Map<String, RenderProperty>,
        val geometries: List<RenderGeometry>,
    )

    private fun parsePlacemarkBlock(block: String, baseFeatureCount: Int): ParsedPlacemark {
        val name = elementText(block, "name") ?: "Feature ${baseFeatureCount + 1}"
        val description = elementText(block, "description")
        val properties = HashMap<String, RenderProperty>()
        properties["name"] = RenderProperty.Text(name)
        if (description != null) {
            properties["description"] = RenderProperty.Text(description)
        }
        var searchFrom = 0
        while (true) {
            val dataElement = extractElement(block, "Data", searchFrom) ?: break
            val attrName = attributeValue(block, dataElement.start, "name")
            val value = elementText(block, "value", dataElement.contentStart)
            if (attrName != null && value != null) {
                properties[attrName] = RenderProperty.Text(value)
            }
            searchFrom = dataElement.end
            if (searchFrom >= block.length) break
        }
        return ParsedPlacemark(properties, parseGeometries(block))
    }

    private fun attributeValue(text: String, elementStart: Int, attributeName: String): String? {
        val gt = text.indexOf('>', elementStart)
        if (gt == -1) return null
        val openingTag = text.substring(elementStart, gt)
        var searchFrom = 0
        while (true) {
            val attrIndex = openingTag.indexOf(attributeName, searchFrom)
            if (attrIndex == -1) return null
            val afterName = attrIndex + attributeName.length
            if (afterName < openingTag.length && (openingTag[afterName].isLetterOrDigit() || openingTag[afterName] == '_' || openingTag[afterName] == '-')) {
                searchFrom = afterName
                continue
            }
            var index = afterName
            while (index < openingTag.length && openingTag[index].isWhitespace()) index += 1
            if (index >= openingTag.length || openingTag[index] != '=') {
                searchFrom = afterName
                continue
            }
            index += 1
            while (index < openingTag.length && openingTag[index].isWhitespace()) index += 1
            if (index >= openingTag.length) return null
            val quote = openingTag[index]
            if (quote != '"' && quote != '\'') return null
            val valueEnd = openingTag.indexOf(quote, index + 1)
            if (valueEnd == -1) return null
            return decodeXmlEntities(openingTag.substring(index + 1, valueEnd))
        }
    }

    private fun parseGeometries(block: String): List<RenderGeometry> {
        val geometries = ArrayList<RenderGeometry>()
        var index = 0
        while (index < block.length) {
            val next = findNextGeometry(block, index) ?: break
            val element = extractElement(block, next.second, next.first) ?: break
            when (next.second) {
                "MultiGeometry" -> {
                    val innerText = if (element.selfClosing) "" else block.substring(element.contentStart, element.closingStart)
                    val inner = parseGeometries(innerText)
                    if (inner.size > 1 && inner.all { it is RenderGeometry.Polygon }) {
                        geometries.add(
                            RenderGeometry.MultiPolygon(
                                inner.map { (it as RenderGeometry.Polygon).coordinates },
                            ),
                        )
                    } else {
                        geometries.addAll(inner)
                    }
                }
                "Polygon" -> {
                    parsePolygon(block.substring(element.start, element.end))?.let { geometries.add(it) }
                }
                "LineString" -> {
                    val coordinates = extractCoordinates(block.substring(element.start, element.end))
                    if (coordinates.size >= 2) {
                        geometries.add(RenderGeometry.LineString(coordinates))
                    } else if (coordinates.size == 1) {
                        geometries.add(RenderGeometry.Point(coordinates.first()))
                    }
                }
                "Point" -> {
                    val coordinates = extractCoordinates(block.substring(element.start, element.end))
                    if (coordinates.isNotEmpty()) {
                        geometries.add(RenderGeometry.Point(coordinates.first()))
                    }
                }
            }
            index = element.end
            if (index <= next.first) break
        }
        return geometries
    }

    private fun findNextGeometry(block: String, fromIndex: Int): Pair<Int, String>? {
        var index = fromIndex
        while (index < block.length) {
            val lt = block.indexOf('<', index)
            if (lt == -1) return null
            if (block.startsWith("<!--", lt)) {
                val commentEnd = block.indexOf("-->", lt + 4)
                index = if (commentEnd == -1) block.length else commentEnd + 3
                continue
            }
            if (lt + 1 < block.length && (block[lt + 1] == '/' || block[lt + 1] == '!' || block[lt + 1] == '?')) {
                index = lt + 2
                continue
            }
            val parsed = parseTagName(block, lt) ?: return null
            if (parsed.first == "Point" || parsed.first == "LineString" || parsed.first == "Polygon" || parsed.first == "MultiGeometry") {
                return lt to parsed.first
            }
            index = parsed.second
        }
        return null
    }

    private fun parsePolygon(elementText: String): RenderGeometry.Polygon? {
        val outerElement = extractElement(elementText, "outerBoundaryIs", 0)
        if (outerElement != null) {
            val outerCoordinates = extractCoordinates(elementText.substring(outerElement.start, outerElement.end))
            if (outerCoordinates.isNotEmpty()) {
                val rings = ArrayList<List<LngLat>>()
                rings.add(ensureClosed(outerCoordinates))
                var searchFrom = outerElement.end
                while (true) {
                    val innerElement = extractElement(elementText, "innerBoundaryIs", searchFrom) ?: break
                    val innerCoordinates = extractCoordinates(elementText.substring(innerElement.start, innerElement.end))
                    if (innerCoordinates.isNotEmpty()) {
                        rings.add(ensureClosed(innerCoordinates))
                    }
                    searchFrom = innerElement.end
                    if (searchFrom >= elementText.length) break
                }
                return RenderGeometry.Polygon(rings)
            }
            return null
        }
        val coordinates = extractCoordinates(elementText)
        if (coordinates.isEmpty()) return null
        return RenderGeometry.Polygon(listOf(ensureClosed(coordinates)))
    }

    private fun extractCoordinates(text: String): List<LngLat> {
        val raw = elementText(text, "coordinates") ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val tokens = raw.split("\\s+".toRegex())
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
