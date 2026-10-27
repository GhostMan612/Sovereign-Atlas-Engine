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
    private const val MAX_PLACEMARK_CHARS = 2_000_000
    private const val MAX_GEOMETRY_NESTING = 16
    private const val MAX_PARTIAL_TAG_CHARS = 8192

    fun parse(
        inputStream: InputStream,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "KML Layer",
        sourcePathOrUri: String? = null,
        isKmz: Boolean = false,
    ): GisDataset {
        val reader = inputStream.bufferedReader(StandardCharsets.UTF_8)
        return reader.use { parseFromReader(it, datasetId, datasetName, sourcePathOrUri, isKmz) }
    }

    fun parseString(
        kmlText: String,
        datasetId: String = UUID.randomUUID().toString(),
        datasetName: String = "KML Layer",
        sourcePathOrUri: String? = null,
        isKmz: Boolean = false,
    ): GisDataset {
        val reader = StringReader(kmlText)
        return reader.use { parseFromReader(it, datasetId, datasetName, sourcePathOrUri, isKmz) }
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
        var skipTail = ""
        var skipContext: SkipContext? = null
        while (!endOfInput && vertexCount < MAX_VERTICES) {
            val read = reader.read(chunk)
            if (read == -1) {
                endOfInput = true
            } else if (read > 0) {
                val chunkText = String(chunk, 0, read)
                val context = skipContext
                if (context != null) {
                    val scanText = skipTail + chunkText
                    val closingEnd = scanForPlacemarkClose(scanText, context, 0)
                    if (closingEnd != -1) {
                        skipContext = null
                        skipTail = ""
                        buffer.setLength(0)
                        buffer.append(scanText.substring(closingEnd))
                    } else {
                        skipTail = scanText.takeLast(64)
                        buffer.setLength(0)
                        continue
                    }
                } else {
                    buffer.append(chunkText)
                }
            }
            while (vertexCount < MAX_VERTICES) {
                val extraction = extractNextPlacemarkBlock(buffer, endOfInput)
                if (extraction.oversized) {
                    skipContext = extraction.skipContext
                    skipTail = ""
                    buffer.setLength(0)
                    break
                }
                val block = extraction.block ?: break
                if (block.length > MAX_PLACEMARK_CHARS) continue
                val placemark = parsePlacemarkBlock(block, features.size, MAX_VERTICES - vertexCount)
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

    private class BlockExtraction(
        val block: String?,
        val oversized: Boolean,
        val skipContext: SkipContext?,
    )

    private class SkipContext(
        var inCdata: Boolean = false,
        var inComment: Boolean = false,
        var inProcessingInstruction: Boolean = false,
    )

    private fun extractNextPlacemarkBlock(buffer: StringBuilder, endOfInput: Boolean): BlockExtraction {
        val text = buffer.toString()
        val start = findOpeningTag(text, "Placemark", 0)
            ?: return BlockExtraction(null, false, null)
        val closing = findClosingTag(text, "Placemark", start.contentStart)
        if (closing == null) {
            if (text.length - start.start > MAX_PLACEMARK_CHARS) {
                val context = SkipContext()
                scanForPlacemarkClose(text, context, start.start)
                return BlockExtraction(null, true, context)
            }
            if (endOfInput) {
                buffer.setLength(0)
            }
            return BlockExtraction(null, false, null)
        }
        val block = text.substring(start.start, closing.end)
        buffer.delete(0, closing.end)
        return BlockExtraction(block, false, null)
    }

    private fun scanForPlacemarkClose(text: String, context: SkipContext, fromIndex: Int): Int {
        var index = fromIndex
        while (index < text.length) {
            if (context.inCdata) {
                val cdataEnd = text.indexOf("]]>", index)
                if (cdataEnd == -1) return -1
                index = cdataEnd + 3
                context.inCdata = false
                continue
            }
            if (context.inComment) {
                val commentEnd = text.indexOf("-->", index)
                if (commentEnd == -1) return -1
                index = commentEnd + 3
                context.inComment = false
                continue
            }
            if (context.inProcessingInstruction) {
                val piEnd = text.indexOf("?>", index)
                if (piEnd == -1) return -1
                index = piEnd + 2
                context.inProcessingInstruction = false
                continue
            }
            val lt = text.indexOf('<', index)
            if (lt == -1) return -1
            if (text.startsWith("<![CDATA[", lt)) {
                context.inCdata = true
                index = lt + 9
                continue
            }
            if (text.startsWith("<!--", lt)) {
                context.inComment = true
                index = lt + 4
                continue
            }
            if (text.startsWith("<?", lt)) {
                context.inProcessingInstruction = true
                index = lt + 2
                continue
            }
            if (text.startsWith("<!", lt)) {
                val gt = text.indexOf('>', lt + 2)
                if (gt == -1) return -1
                index = gt + 1
                continue
            }
            val parsed = parseTagName(text, lt) ?: return -1
            val isClosing = lt + 1 < text.length && text[lt + 1] == '/'
            if (isClosing && parsed.first == "Placemark") {
                return parsed.second
            }
            index = parsed.second
        }
        return -1
    }

    private fun discardBeforePlacemarkStart(buffer: StringBuilder) {
        val text = buffer.toString()
        val start = findOpeningTag(text, "Placemark", 0)
        if (start != null) {
            if (start.start > 0) {
                buffer.delete(0, start.start)
            }
            return
        }
        val lastLt = text.lastIndexOf('<')
        if (lastLt == -1) {
            buffer.setLength(0)
            return
        }
        if (text.indexOf('>', lastLt) == -1) {
            if (text.length - lastLt <= MAX_PARTIAL_TAG_CHARS) {
                if (lastLt > 0) {
                    buffer.delete(0, lastLt)
                }
            } else {
                buffer.setLength(0)
            }
            return
        }
        buffer.setLength(0)
    }

    private class ElementRange(
        val start: Int,
        val contentStart: Int,
        val closingStart: Int,
        val end: Int,
        val selfClosing: Boolean,
    )

    private class TagOccurrence(
        val start: Int,
        val end: Int,
        val localName: String,
        val isClosing: Boolean,
        val selfClosing: Boolean,
    )

    private fun nextTag(text: String, fromIndex: Int): TagOccurrence? {
        var index = fromIndex
        while (index < text.length) {
            val lt = text.indexOf('<', index)
            if (lt == -1) return null
            if (text.startsWith("<!--", lt)) {
                val commentEnd = text.indexOf("-->", lt + 4)
                index = if (commentEnd == -1) text.length else commentEnd + 3
                continue
            }
            if (text.startsWith("<![CDATA[", lt)) {
                val cdataEnd = text.indexOf("]]>", lt + 9)
                index = if (cdataEnd == -1) text.length else cdataEnd + 3
                continue
            }
            if (text.startsWith("<?", lt)) {
                val piEnd = text.indexOf("?>", lt + 2)
                index = if (piEnd == -1) text.length else piEnd + 2
                continue
            }
            if (text.startsWith("<!", lt)) {
                val gt = text.indexOf('>', lt + 2)
                index = if (gt == -1) text.length else gt + 1
                continue
            }
            val parsed = parseTagName(text, lt) ?: return null
            val isClosing = lt + 1 < text.length && text[lt + 1] == '/'
            return TagOccurrence(lt, parsed.second, parsed.first, isClosing, parsed.third)
        }
        return null
    }

    private fun findOpeningTag(
        text: String,
        localName: String,
        fromIndex: Int,
    ): ElementRange? {
        var index = fromIndex
        while (true) {
            val tag = nextTag(text, index) ?: return null
            if (!tag.isClosing && tag.localName == localName) {
                return ElementRange(tag.start, tag.end, -1, tag.end, tag.selfClosing)
            }
            index = tag.end
        }
    }

    private fun findClosingTag(
        text: String,
        localName: String,
        fromIndex: Int,
    ): ElementRange? {
        var index = fromIndex
        while (true) {
            val tag = nextTag(text, index) ?: return null
            if (tag.isClosing && tag.localName == localName) {
                return ElementRange(tag.start, -1, tag.start, tag.end, false)
            }
            index = tag.end
        }
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
        val gt = text.indexOf('>', index)
        if (gt == -1) return null
        val selfClosing = gt > index && text[gt - 1] == '/'
        if (index == nameStart) {
            return Triple("", gt + 1, selfClosing)
        }
        val qualifiedName = text.substring(nameStart, index)
        val localName = qualifiedName.substringAfter(':')
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
        var depth = 1
        var searchFrom = opening.contentStart
        while (depth > 0) {
            val tag = nextTag(text, searchFrom) ?: return null
            if (tag.localName == localName) {
                if (tag.isClosing) {
                    depth -= 1
                    if (depth == 0) {
                        return ElementRange(opening.start, opening.contentStart, tag.start, tag.end, false)
                    }
                } else if (!tag.selfClosing) {
                    depth += 1
                }
            }
            searchFrom = tag.end
        }
        return null
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

    private fun parsePlacemarkBlock(block: String, baseFeatureCount: Int, vertexBudget: Int): ParsedPlacemark {
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
            val dataText = block.substring(dataElement.start, dataElement.end)
            val value = elementText(dataText, "value")
            if (attrName != null && value != null) {
                properties[attrName] = RenderProperty.Text(value)
            }
            searchFrom = dataElement.end
            if (searchFrom >= block.length) break
        }
        return ParsedPlacemark(properties, parseGeometries(block, vertexBudget, 0))
    }

    private fun attributeValue(text: String, elementStart: Int, attributeName: String): String? {
        val gt = text.indexOf('>', elementStart)
        if (gt == -1) return null
        val openingTag = text.substring(elementStart, gt)
        var searchFrom = 0
        while (true) {
            val attrIndex = openingTag.indexOf(attributeName, searchFrom)
            if (attrIndex == -1) return null
            if (attrIndex > 0) {
                val before = openingTag[attrIndex - 1]
                if (before.isLetterOrDigit() || before == '_' || before == '-' || before == '.' || before == ':') {
                    searchFrom = attrIndex + attributeName.length
                    continue
                }
            }
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

    private fun parseGeometries(block: String, budget: Int, depth: Int): List<RenderGeometry> {
        if (depth > MAX_GEOMETRY_NESTING || budget <= 0) return emptyList()
        val geometries = ArrayList<RenderGeometry>()
        var used = 0
        var index = 0
        while (index < block.length && used < budget) {
            val next = findNextGeometry(block, index) ?: break
            val element = extractElement(block, next.second, next.first) ?: break
            when (next.second) {
                "MultiGeometry" -> {
                    val innerText = if (element.selfClosing) "" else block.substring(element.contentStart, element.closingStart)
                    val inner = parseGeometries(innerText, budget - used, depth + 1)
                    if (inner.size > 1 && inner.all { it is RenderGeometry.Polygon }) {
                        geometries.add(
                            RenderGeometry.MultiPolygon(
                                inner.map { (it as RenderGeometry.Polygon).coordinates },
                            ),
                        )
                    } else {
                        geometries.addAll(inner)
                    }
                    used += inner.sumOf { it.allCoordinates().size }
                }
                "Polygon" -> {
                    val polygon = parsePolygon(block.substring(element.start, element.end), budget - used)
                    if (polygon != null) {
                        geometries.add(polygon)
                        used += polygon.allCoordinates().size
                    }
                }
                "LineString" -> {
                    val result = extractCoordinates(block.substring(element.start, element.end), budget - used)
                    if (!result.truncated) {
                        if (result.points.size >= 2) {
                            geometries.add(RenderGeometry.LineString(result.points))
                            used += result.points.size
                        } else if (result.points.size == 1) {
                            geometries.add(RenderGeometry.Point(result.points.first()))
                            used += 1
                        }
                    }
                }
                "Point" -> {
                    val result = extractCoordinates(block.substring(element.start, element.end), budget - used)
                    if (!result.truncated && result.points.isNotEmpty()) {
                        geometries.add(RenderGeometry.Point(result.points.first()))
                        used += 1
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
        while (true) {
            val tag = nextTag(block, index) ?: return null
            if (!tag.isClosing && (tag.localName == "Point" || tag.localName == "LineString" || tag.localName == "Polygon" || tag.localName == "MultiGeometry")) {
                return tag.start to tag.localName
            }
            index = tag.end
        }
    }

    private fun parsePolygon(elementText: String, budget: Int): RenderGeometry.Polygon? {
        if (budget <= 0) return null
        val outerElement = extractElement(elementText, "outerBoundaryIs", 0)
        if (outerElement != null) {
            val outerResult = extractCoordinates(elementText.substring(outerElement.start, outerElement.end), budget)
            if (outerResult.truncated || outerResult.points.isEmpty()) return null
            val rings = ArrayList<List<LngLat>>()
            rings.add(ensureClosed(outerResult.points))
            var used = outerResult.points.size
            var searchFrom = outerElement.end
            while (true) {
                val innerElement = extractElement(elementText, "innerBoundaryIs", searchFrom) ?: break
                val innerResult = extractCoordinates(elementText.substring(innerElement.start, innerElement.end), budget - used)
                if (innerResult.truncated) return null
                if (innerResult.points.isNotEmpty()) {
                    rings.add(ensureClosed(innerResult.points))
                    used += innerResult.points.size
                }
                searchFrom = innerElement.end
                if (searchFrom >= elementText.length) break
            }
            return RenderGeometry.Polygon(rings)
        }
        val result = extractCoordinates(elementText, budget)
        if (result.truncated || result.points.isEmpty()) return null
        return RenderGeometry.Polygon(listOf(ensureClosed(result.points)))
    }

    private class CoordinatesResult(
        val points: List<LngLat>,
        val truncated: Boolean,
    )

    private fun extractCoordinates(text: String, maxPoints: Int): CoordinatesResult {
        val raw = elementText(text, "coordinates") ?: return CoordinatesResult(emptyList(), false)
        if (raw.isEmpty() || maxPoints <= 0) return CoordinatesResult(emptyList(), false)
        val tokens = raw.split("\\s+".toRegex())
        val points = ArrayList<LngLat>(minOf(tokens.size, maxPoints))
        var truncated = false
        for (token in tokens) {
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].trim().toDoubleOrNull()
                val lat = parts[1].trim().toDoubleOrNull()
                if (lon != null && lat != null && lon.isFinite() && lat.isFinite()) {
                    if (lon in -180.0..180.0 && lat in -90.0..90.0) {
                        if (points.size < maxPoints) {
                            points.add(LngLat(lon, lat))
                        } else {
                            truncated = true
                        }
                    }
                }
            }
        }
        return CoordinatesResult(points, truncated)
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
