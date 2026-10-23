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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
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

    private fun importKmz(stream: InputStream, name: String, sourceUri: String): GisImportResult {
        val zip = ZipInputStream(stream)
        var entry: ZipEntry? = zip.nextEntry
        var kmlBytes: ByteArray? = null

        while (entry != null) {
            val entryName = entry.name.replace('\\', '/')
            if (entryName.contains("..") || entryName.startsWith("/")) {
                throw SecurityException("Zip slip detected in KMZ entry: ${entry.name}")
            }
            if (entryName.lowercase().endsWith(".kml") && !entry.isDirectory) {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var read: Int
                while (zip.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                kmlBytes = out.toByteArray()
                break
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (kmlBytes == null) {
            return GisImportResult(null, "KMZ archive contains no .kml document")
        }

        val dataset = KmlStreamingParser.parseString(
            kmlText = String(kmlBytes, StandardCharsets.UTF_8),
            datasetId = UUID.randomUUID().toString(),
            datasetName = name.substringBeforeLast('.'),
            sourcePathOrUri = sourceUri,
            isKmz = true,
        )
        return GisImportResult(dataset)
    }

    private fun importZippedShapefile(stream: InputStream, name: String, sourceUri: String): GisImportResult {
        val zip = ZipInputStream(stream)
        var entry: ZipEntry? = zip.nextEntry

        var shpBytes: ByteArray? = null
        var dbfBytes: ByteArray? = null
        var prjText: String? = null

        while (entry != null) {
            val entryName = entry.name.replace('\\', '/')
            if (entryName.contains("..") || entryName.startsWith("/")) {
                throw SecurityException("Zip slip detected in Shapefile archive entry: ${entry.name}")
            }

            if (!entry.isDirectory) {
                val lower = entryName.lowercase()
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var read: Int
                while (zip.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                val data = out.toByteArray()

                when {
                    lower.endsWith(".shp") -> shpBytes = data
                    lower.endsWith(".dbf") -> dbfBytes = data
                    lower.endsWith(".prj") -> prjText = String(data, StandardCharsets.UTF_8)
                }
            }

            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (shpBytes == null) {
            return GisImportResult(null, "Zipped archive contains no .shp geometry file")
        }

        val dataset = ShapefileParser.parse(
            shpBytes = shpBytes,
            dbfBytes = dbfBytes,
            prjText = prjText,
            datasetId = UUID.randomUUID().toString(),
            datasetName = name.substringBeforeLast('.'),
            sourcePathOrUri = sourceUri,
        )
        return GisImportResult(dataset)
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
