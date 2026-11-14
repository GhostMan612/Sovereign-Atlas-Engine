// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.gis

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.sovereignatlas.atlas.geo.gis.GeoJsonParser
import com.sovereignatlas.atlas.geo.gis.GisDataset
import com.sovereignatlas.atlas.geo.gis.GisFeature
import com.sovereignatlas.atlas.geo.gis.GisFormat
import com.sovereignatlas.atlas.geo.gis.GisImportResult
import com.sovereignatlas.atlas.geo.gis.GisSrid
import com.sovereignatlas.atlas.geo.gis.KmlStreamingParser
import com.sovereignatlas.atlas.geo.gis.ShapefileParser
import com.sovereignatlas.atlas.geo.gis.WkbDecoder
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object AndroidGisImporter {

    fun importFromUri(context: Context, uri: Uri): GisImportResult {
        val fileName = queryDisplayName(context, uri) ?: "imported_layer"
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return GisImportResult(null, "Unable to open input stream for $uri")

            inputStream.use { stream ->
                when (extension) {
                    "gpkg" -> importGeoPackage(context, stream, fileName, uri.toString())
                    "kml" -> importKml(stream, fileName, uri.toString())
                    "kmz" -> importKmz(stream, fileName, uri.toString())
                    "geojson", "json" -> importGeoJson(stream, fileName, uri.toString())
                    "zip" -> importZippedShapefile(stream, fileName, uri.toString())
                    else -> GisImportResult(null, "Unsupported GIS format: .$extension")
                }
            }
        } catch (e: Exception) {
            GisImportResult(null, "Import failed: ${e.message}")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        return cursor.getString(idx)
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun importGeoJson(stream: InputStream, name: String, sourceUri: String): GisImportResult {
        val text = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val dataset = GeoJsonParser.parse(
            jsonString = text,
            datasetId = UUID.randomUUID().toString(),
            datasetName = name.substringBeforeLast('.'),
            sourcePathOrUri = sourceUri,
        )
        return GisImportResult(dataset)
    }

    private fun importKml(stream: InputStream, name: String, sourceUri: String): GisImportResult {
        val dataset = KmlStreamingParser.parse(
            inputStream = stream,
            datasetId = UUID.randomUUID().toString(),
            datasetName = name.substringBeforeLast('.'),
            sourcePathOrUri = sourceUri,
            isKmz = false,
        )
        return GisImportResult(dataset)
    }

    internal const val DEFAULT_MAX_ENTRY_COUNT = 512
    internal const val DEFAULT_MAX_ENTRY_BYTES = 32L * 1024L * 1024L
    internal const val DEFAULT_MAX_PRJ_BYTES = 1L * 1024L * 1024L
    internal const val DEFAULT_MAX_TOTAL_DECOMPRESSED_BYTES = 64L * 1024L * 1024L
    internal const val DEFAULT_MAX_COMPRESSED_BYTES = 64L * 1024L * 1024L
    internal const val DEFAULT_MAX_EXPANSION_RATIO = 100.0
    internal const val DEFAULT_MIN_BYTES_FOR_RATIO_CHECK = 256L * 1024L
    internal const val DEFAULT_IMPORT_TIMEOUT_MS = 30_000L

    private const val ARCHIVE_BUFFER_SIZE = 8192
    private val SHAPEFILE_COMPONENT_EXTENSIONS = setOf("shp", "dbf", "prj", "shx", "cpg", "sbn", "sbx", "qpj")

    internal data class ArchiveLimits(
        val maxEntryCount: Int = DEFAULT_MAX_ENTRY_COUNT,
        val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
        val maxPrjBytes: Long = DEFAULT_MAX_PRJ_BYTES,
        val maxTotalDecompressedBytes: Long = DEFAULT_MAX_TOTAL_DECOMPRESSED_BYTES,
        val maxCompressedBytes: Long = DEFAULT_MAX_COMPRESSED_BYTES,
        val maxExpansionRatio: Double = DEFAULT_MAX_EXPANSION_RATIO,
        val minBytesForRatioCheck: Long = DEFAULT_MIN_BYTES_FOR_RATIO_CHECK,
        val timeoutMs: Long = DEFAULT_IMPORT_TIMEOUT_MS,
    )

    private class ArchiveRejectionException(message: String) : IOException(message)

    internal fun importKmz(
        stream: InputStream,
        name: String,
        sourceUri: String,
        limits: ArchiveLimits = ArchiveLimits(),
        clock: () -> Long = System::currentTimeMillis,
    ): GisImportResult = runBoundedArchiveImport(stream, "KMZ", limits, clock) { zip, budget, scratch ->
        val seenPaths = HashSet<String>()
        var dataset: GisDataset? = null
        var kmlEntryPath: String? = null

        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            budget.checkElapsed()
            budget.recordEntry()
            val normalizedPath = normalizeArchivePath(entry.name, entry.isDirectory)
            if (!entry.isDirectory) {
                val lowerPath = normalizedPath.lowercase(Locale.ROOT)
                if (!seenPaths.add(lowerPath)) {
                    throw ArchiveRejectionException("Duplicate entry in KMZ archive: $normalizedPath")
                }
                if (lowerPath.endsWith(".kml")) {
                    if (kmlEntryPath != null) {
                        throw ArchiveRejectionException(
                            "KMZ archive contains multiple .kml documents: $kmlEntryPath and $normalizedPath",
                        )
                    }
                    budget.checkDeclaredEntrySize(entry, limits.maxEntryBytes, normalizedPath)
                    kmlEntryPath = normalizedPath
                    val entryStream = BoundedZipEntryInputStream(zip, budget, limits.maxEntryBytes, normalizedPath)
                    dataset = KmlStreamingParser.parse(
                        inputStream = entryStream,
                        datasetId = UUID.randomUUID().toString(),
                        datasetName = name.substringBeforeLast('.'),
                        sourcePathOrUri = sourceUri,
                        isKmz = true,
                    )
                    entryStream.drainRemainder(scratch)
                } else {
                    drainBoundedEntry(zip, entry, budget, limits.maxEntryBytes, normalizedPath, scratch)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (budget.entryCount == 0) {
            return@runBoundedArchiveImport GisImportResult(null, "KMZ archive contains no valid ZIP entries")
        }
        dataset?.let { GisImportResult(it) }
            ?: GisImportResult(null, "KMZ archive contains no .kml document")
    }

    internal fun importZippedShapefile(
        stream: InputStream,
        name: String,
        sourceUri: String,
        limits: ArchiveLimits = ArchiveLimits(),
        clock: () -> Long = System::currentTimeMillis,
    ): GisImportResult = runBoundedArchiveImport(stream, "Shapefile", limits, clock) { zip, budget, scratch ->
        val seenPaths = HashSet<String>()
        val seenComponentBasenames = HashSet<String>()

        var shpPath: String? = null
        var shpBytes: ByteArray? = null
        var dbfPath: String? = null
        var dbfBytes: ByteArray? = null
        var prjPath: String? = null
        var prjText: String? = null

        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            budget.checkElapsed()
            budget.recordEntry()
            val normalizedPath = normalizeArchivePath(entry.name, entry.isDirectory)
            if (!entry.isDirectory) {
                val lowerPath = normalizedPath.lowercase(Locale.ROOT)
                if (!seenPaths.add(lowerPath)) {
                    throw ArchiveRejectionException("Duplicate entry in zipped shapefile: $normalizedPath")
                }
                val basename = lowerPath.substringAfterLast('/')
                val extension = basename.substringAfterLast('.', "")
                if (extension in SHAPEFILE_COMPONENT_EXTENSIONS && !seenComponentBasenames.add(basename)) {
                    throw ArchiveRejectionException("Duplicate shapefile component basename: $basename")
                }
                when (extension) {
                    "shp" -> {
                        if (shpPath != null) {
                            throw ArchiveRejectionException(
                                "Zipped archive contains multiple .shp geometry files: $shpPath and $normalizedPath",
                            )
                        }
                        shpPath = normalizedPath
                        shpBytes = readBoundedEntryBytes(zip, entry, budget, limits.maxEntryBytes, normalizedPath, scratch)
                    }
                    "dbf" -> {
                        if (dbfPath != null) {
                            throw ArchiveRejectionException(
                                "Zipped archive contains multiple .dbf attribute files: $dbfPath and $normalizedPath",
                            )
                        }
                        dbfPath = normalizedPath
                        dbfBytes = readBoundedEntryBytes(zip, entry, budget, limits.maxEntryBytes, normalizedPath, scratch)
                    }
                    "prj" -> {
                        if (prjPath != null) {
                            throw ArchiveRejectionException(
                                "Zipped archive contains multiple .prj projection files: $prjPath and $normalizedPath",
                            )
                        }
                        prjPath = normalizedPath
                        val prjLimit = minOf(limits.maxEntryBytes, limits.maxPrjBytes)
                        val prjBytes = readBoundedEntryBytes(zip, entry, budget, prjLimit, normalizedPath, scratch)
                        prjText = String(prjBytes, StandardCharsets.UTF_8)
                    }
                    else -> {
                        drainBoundedEntry(zip, entry, budget, limits.maxEntryBytes, normalizedPath, scratch)
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (budget.entryCount == 0) {
            return@runBoundedArchiveImport GisImportResult(null, "Zipped archive contains no valid ZIP entries")
        }
        val finalShp = shpBytes
            ?: return@runBoundedArchiveImport GisImportResult(null, "Zipped archive contains no .shp geometry file")

        val dataset = ShapefileParser.parse(
            shpBytes = finalShp,
            dbfBytes = dbfBytes,
            prjText = prjText,
            datasetId = UUID.randomUUID().toString(),
            datasetName = name.substringBeforeLast('.'),
            sourcePathOrUri = sourceUri,
        )
        GisImportResult(dataset)
    }

    private inline fun runBoundedArchiveImport(
        stream: InputStream,
        archiveLabel: String,
        limits: ArchiveLimits,
        noinline clock: () -> Long,
        block: (ZipInputStream, ArchiveBudget, ByteArray) -> GisImportResult,
    ): GisImportResult {
        val startedAtMs = clock()
        val compressed = BoundedCountingInputStream(stream, limits, clock, startedAtMs)
        return try {
            ZipInputStream(compressed).use { zip ->
                val budget = ArchiveBudget(limits, compressed, clock, startedAtMs)
                block(zip, budget, ByteArray(ARCHIVE_BUFFER_SIZE))
            }
        } catch (e: ArchiveRejectionException) {
            GisImportResult(null, e.message ?: "$archiveLabel archive rejected due to resource or path limits")
        } catch (e: ZipException) {
            GisImportResult(null, "Malformed or truncated $archiveLabel archive: ${e.message ?: "invalid ZIP data"}")
        } catch (e: EOFException) {
            GisImportResult(null, "Malformed or truncated $archiveLabel archive: unexpected end of stream")
        } catch (e: Exception) {
            GisImportResult(null, "$archiveLabel import failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun normalizeArchivePath(rawName: String, isDirectory: Boolean): String {
        if (rawName.isBlank() || rawName.indexOf('\u0000') >= 0) {
            throw ArchiveRejectionException("Invalid empty or NUL archive entry name")
        }
        if (rawName.startsWith("/") || rawName.startsWith("\\")) {
            throw ArchiveRejectionException("Zip slip detected in archive entry: $rawName")
        }
        if (rawName.length >= 2 && rawName[0].isLetter() && rawName[1] == ':') {
            throw ArchiveRejectionException("Zip slip detected in archive entry: $rawName")
        }
        val slashNormalized = rawName.replace('\\', '/')
        val trimmed = if (isDirectory) slashNormalized.trimEnd('/') else slashNormalized
        if (!isDirectory && trimmed.endsWith('/')) {
            throw ArchiveRejectionException("Invalid file entry path in archive: $rawName")
        }
        val segments = ArrayList<String>()
        for (segment in trimmed.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> throw ArchiveRejectionException("Zip slip detected in archive entry: $rawName")
                else -> segments.add(segment)
            }
        }
        if (segments.isEmpty() && !isDirectory) {
            throw ArchiveRejectionException("Invalid archive entry path: $rawName")
        }
        return segments.joinToString("/")
    }

    private fun readBoundedEntryBytes(
        zip: ZipInputStream,
        entry: ZipEntry,
        budget: ArchiveBudget,
        entryLimit: Long,
        entryPath: String,
        scratch: ByteArray,
    ): ByteArray {
        budget.checkDeclaredEntrySize(entry, entryLimit, entryPath)
        val initialCapacity = if (entry.size in 1L..65_536L) entry.size.toInt() else 4096
        val out = ByteArrayOutputStream(initialCapacity)
        val entryStream = BoundedZipEntryInputStream(zip, budget, entryLimit, entryPath)
        var read: Int
        while (entryStream.read(scratch, 0, scratch.size).also { read = it } != -1) {
            if (read > 0) {
                out.write(scratch, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun drainBoundedEntry(
        zip: ZipInputStream,
        entry: ZipEntry,
        budget: ArchiveBudget,
        entryLimit: Long,
        entryPath: String,
        scratch: ByteArray,
    ) {
        budget.checkDeclaredEntrySize(entry, entryLimit, entryPath)
        val entryStream = BoundedZipEntryInputStream(zip, budget, entryLimit, entryPath)
        entryStream.drainRemainder(scratch)
    }

    private fun enforceArchiveTimeout(limits: ArchiveLimits, clock: () -> Long, startedAtMs: Long) {
        if (limits.timeoutMs > 0L && clock() - startedAtMs > limits.timeoutMs) {
            throw ArchiveRejectionException(
                "Archive extraction timed out after ${limits.timeoutMs} ms",
            )
        }
    }

    private class BoundedCountingInputStream(
        private val source: InputStream,
        private val limits: ArchiveLimits,
        private val clock: () -> Long,
        private val startedAtMs: Long,
    ) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            enforceArchiveTimeout(limits, clock, startedAtMs)
            val value = source.read()
            if (value != -1) {
                bytesRead += 1L
                checkLimit()
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            enforceArchiveTimeout(limits, clock, startedAtMs)
            val count = source.read(b, off, len)
            if (count > 0) {
                bytesRead += count.toLong()
                checkLimit()
            }
            return count
        }

        override fun skip(n: Long): Long {
            enforceArchiveTimeout(limits, clock, startedAtMs)
            val skipped = source.skip(n)
            if (skipped > 0L) {
                bytesRead += skipped
                checkLimit()
            }
            return skipped
        }

        override fun available(): Int = source.available()

        override fun close() {
            source.close()
        }

        private fun checkLimit() {
            if (bytesRead > limits.maxCompressedBytes) {
                throw ArchiveRejectionException(
                    "Archive compressed size exceeds limit of ${limits.maxCompressedBytes} bytes",
                )
            }
        }
    }

    private class ArchiveBudget(
        private val limits: ArchiveLimits,
        private val compressed: BoundedCountingInputStream,
        private val clock: () -> Long,
        private val startedAtMs: Long,
    ) {
        var entryCount: Int = 0
            private set
        var totalDecompressedBytes: Long = 0L
            private set

        fun checkElapsed() {
            enforceArchiveTimeout(limits, clock, startedAtMs)
        }

        fun recordEntry() {
            entryCount += 1
            if (entryCount > limits.maxEntryCount) {
                throw ArchiveRejectionException(
                    "Archive entry count exceeds limit of ${limits.maxEntryCount}",
                )
            }
        }

        fun checkDeclaredEntrySize(entry: ZipEntry, entryLimit: Long, entryPath: String) {
            val declaredSize = entry.size
            if (declaredSize > entryLimit) {
                throw ArchiveRejectionException(
                    "Archive entry '$entryPath' declared size exceeds limit of $entryLimit bytes",
                )
            }
            if (declaredSize > 0L && declaredSize > limits.maxTotalDecompressedBytes - totalDecompressedBytes) {
                throw ArchiveRejectionException(
                    "Archive decompressed size exceeds limit of ${limits.maxTotalDecompressedBytes} bytes",
                )
            }
            val declaredCompressed = entry.compressedSize
            if (declaredCompressed > 0L &&
                declaredSize >= limits.minBytesForRatioCheck &&
                declaredSize.toDouble() / declaredCompressed.toDouble() > limits.maxExpansionRatio
            ) {
                throw ArchiveRejectionException(
                    "Archive entry '$entryPath' compression ratio exceeds ${limits.maxExpansionRatio.toInt()}:1 limit",
                )
            }
        }

        fun remainingTotalDecompressedBytes(): Long =
            maxOf(0L, limits.maxTotalDecompressedBytes - totalDecompressedBytes)

        fun recordDecompressed(justRead: Int, entryBytesRead: Long, entryLimit: Long, entryPath: String) {
            checkElapsed()
            if (entryBytesRead > entryLimit) {
                throw ArchiveRejectionException(
                    "Archive entry '$entryPath' exceeds limit of $entryLimit bytes",
                )
            }
            totalDecompressedBytes += justRead.toLong()
            if (totalDecompressedBytes > limits.maxTotalDecompressedBytes) {
                throw ArchiveRejectionException(
                    "Archive decompressed size exceeds limit of ${limits.maxTotalDecompressedBytes} bytes",
                )
            }
            if (totalDecompressedBytes >= limits.minBytesForRatioCheck) {
                val compressedBytes = maxOf(compressed.bytesRead, 1L)
                if (totalDecompressedBytes.toDouble() / compressedBytes.toDouble() > limits.maxExpansionRatio) {
                    throw ArchiveRejectionException(
                        "Archive compression ratio exceeds ${limits.maxExpansionRatio.toInt()}:1 limit",
                    )
                }
            }
        }
    }

    private class BoundedZipEntryInputStream(
        private val zip: ZipInputStream,
        private val budget: ArchiveBudget,
        private val entryLimit: Long,
        private val entryPath: String,
    ) : InputStream() {
        private var entryBytesRead: Long = 0L
        private var hitEof: Boolean = false
        private var closed: Boolean = false
        private val singleByte = ByteArray(1)

        override fun read(): Int {
            if (closed) throw IOException("Archive entry stream is closed")
            val count = readNextChunk(singleByte, 0, 1)
            return if (count == -1) -1 else (singleByte[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || len > b.size - off) {
                throw IndexOutOfBoundsException()
            }
            if (closed) throw IOException("Archive entry stream is closed")
            if (len == 0) return 0
            return readNextChunk(b, off, len)
        }

        fun drainRemainder(scratch: ByteArray) {
            while (!hitEof) {
                readNextChunk(scratch, 0, scratch.size)
            }
        }

        private fun readNextChunk(b: ByteArray, off: Int, len: Int): Int {
            if (hitEof) return -1
            budget.checkElapsed()
            val remainingForEntry = maxOf(0L, entryLimit - entryBytesRead)
            val remainingTotal = budget.remainingTotalDecompressedBytes()
            val maxToRead = minOf(len.toLong(), remainingForEntry + 1L, remainingTotal + 1L).toInt()
            val count = zip.read(b, off, maxToRead)
            if (count == -1) {
                hitEof = true
                return -1
            }
            if (count > 0) {
                entryBytesRead += count.toLong()
                budget.recordDecompressed(count, entryBytesRead, entryLimit, entryPath)
            }
            return count
        }

        override fun close() {
            closed = true
        }
    }

    private fun importGeoPackage(
        context: Context,
        stream: InputStream,
        name: String,
        sourceUri: String,
    ): GisImportResult {
        val tempFile = File.createTempFile("gpkg_import_", ".gpkg", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
            }

            val db = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )

            try {
                val geomCols = HashMap<String, String>()
                val srsMap = HashMap<String, Int>()

                val geomColCursor: Cursor = db.rawQuery(
                    "SELECT table_name, column_name, srs_id FROM gpkg_geometry_columns",
                    null,
                )
                geomColCursor.use { c ->
                    while (c.moveToNext()) {
                        val tbl = c.getString(0)
                        val col = c.getString(1)
                        val srs = c.getInt(2)
                        geomCols[tbl] = col
                        srsMap[tbl] = srs
                    }
                }

                val featureTables = ArrayList<String>()
                val contentsCursor: Cursor = db.rawQuery(
                    "SELECT table_name FROM gpkg_contents WHERE data_type = 'features'",
                    null,
                )
                contentsCursor.use { c ->
                    while (c.moveToNext()) {
                        featureTables.add(c.getString(0))
                    }
                }

                val allFeatures = ArrayList<GisFeature>()
                var detectedSrid = GisSrid.WGS84

                for (table in featureTables) {
                    val geomCol = geomCols[table] ?: "geom"
                    val srsId = srsMap[table] ?: 4326
                    detectedSrid = when (srsId) {
                        3857 -> GisSrid.WEB_MERCATOR
                        else -> GisSrid.WGS84
                    }

                    val rowCursor = db.rawQuery("SELECT * FROM \"$table\"", null)
                    rowCursor.use { rc ->
                        val colNames = rc.columnNames
                        val geomColIdx = rc.getColumnIndex(geomCol)

                        while (rc.moveToNext() && allFeatures.size < GeoJsonParser.MAX_VERTICES) {
                            val rowId = if (rc.getColumnIndex("id") >= 0) {
                                rc.getString(rc.getColumnIndex("id"))
                            } else {
                                "${table}_${rc.position}"
                            }

                            val geomBlob = if (geomColIdx >= 0) rc.getBlob(geomColIdx) else null
                            if (geomBlob != null) {
                                val geometry = WkbDecoder.decode(geomBlob)
                                if (geometry != null) {
                                    val properties = HashMap<String, RenderProperty>()
                                    for (i in colNames.indices) {
                                        if (i == geomColIdx) continue
                                        val colName = colNames[i]
                                        when (rc.getType(i)) {
                                            Cursor.FIELD_TYPE_STRING -> {
                                                rc.getString(i)?.let { properties[colName] = RenderProperty.Text(it) }
                                            }
                                            Cursor.FIELD_TYPE_FLOAT -> {
                                                properties[colName] = RenderProperty.Number(rc.getDouble(i))
                                            }
                                            Cursor.FIELD_TYPE_INTEGER -> {
                                                properties[colName] = RenderProperty.Number(rc.getLong(i).toDouble())
                                            }
                                            else -> {}
                                        }
                                    }

                                    allFeatures.add(
                                        GisFeature(
                                            id = rowId,
                                            geometry = geometry,
                                            properties = properties,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                val dataset = GisDataset(
                    id = UUID.randomUUID().toString(),
                    name = name.substringBeforeLast('.'),
                    format = GisFormat.GEOPACKAGE,
                    srid = detectedSrid,
                    featureCount = allFeatures.size,
                    features = allFeatures,
                    sourcePathOrUri = sourceUri,
                )
                return GisImportResult(dataset)
            } finally {
                db.close()
            }
        } finally {
            tempFile.delete()
        }
    }
}
