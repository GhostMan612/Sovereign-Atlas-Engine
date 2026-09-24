// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.Deflater

const val DEM_DIR_NAME = "dem"
const val LOS_DIR_NAME = "los"
const val DEM_LICENSE_POINTER = "See SOURCE.txt"

data class DemBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)

data class DemPackSpec(
    val packId: String,
    val datasetVersion: String,
    val retrievalUrl: String,
    val bounds: DemBounds,
) {
    fun mbtilesBounds(): String {
        return "${bounds.minLng},${bounds.minLat},${bounds.maxLng},${bounds.maxLat}"
    }
}

object DemPackMetadata {
    fun mbtilesMetadata(spec: DemPackSpec): Map<String, String> {
        return mapOf(
            "name" to spec.packId,
            "type" to "raster-dem",
            "encoding" to "mapbox",
            "bounds" to spec.mbtilesBounds(),
            "license" to DEM_LICENSE_POINTER,
        )
    }
}

object DemSidecars {
    fun sourceText(spec: DemPackSpec): String {
        return "Sovereign Atlas DEM pack: ${spec.packId}\n" +
            "Provenance chain: SRTM -> NASADEM -> LP DAAC\n" +
            "Dataset version: ${spec.datasetVersion}\n" +
            "Retrieval URL: ${spec.retrievalUrl}\n" +
            "Bounds (WGS84 minLat,minLng,maxLat,maxLng): " +
            "${spec.bounds.minLat},${spec.bounds.minLng}," +
            "${spec.bounds.maxLat},${spec.bounds.maxLng}\n"
    }

    fun licenseText(spec: DemPackSpec): String {
        return "Sovereign Atlas DEM pack: ${spec.packId}\n" +
            "Elevation data provenance: SRTM -> NASADEM -> LP DAAC.\n" +
            "Dataset version: ${spec.datasetVersion}\n" +
            "Retrieval URL: ${spec.retrievalUrl}\n" +
            "Redistribution terms follow the source dataset license; " +
            "see SOURCE.txt before redistributing this pack.\n"
    }

    fun write(root: File, spec: DemPackSpec, payloadFiles: List<File>) {
        root.mkdirs()
        File(root, "SOURCE.txt").writeText(sourceText(spec), Charsets.UTF_8)
        File(root, "LICENSE.txt").writeText(licenseText(spec), Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = ArrayList<String>()
        val ordered = (listOf(File(root, "LICENSE.txt"), File(root, "SOURCE.txt")) + payloadFiles)
            .distinct()
            .sortedBy { it.name }
        for (file in ordered) {
            if (!file.isFile) continue
            digest.reset()
            val hash = digest.digest(file.readBytes())
            val hex = hash.joinToString("") { String.format(Locale.US, "%02x", it) }
            lines.add("$hex  ${file.name}")
        }
        File(root, "SHA256SUMS").writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
    }
}

/**
 * Binary LOS Tile Header (44 Bytes total):
 * Endianness: Big-endian (JVM ByteBuffer default).
 *
 * [0..3]   Magic Bytes ("ATLS") : 4 bytes
 * [4..5]   Version              : Short (2 bytes, value = 1)
 * [6..7]   Width                : Short (2 bytes, samples per row)
 * [8..9]   Height               : Short (2 bytes, samples per column)
 * [10..11] Nodata               : Short (2 bytes, void-cell sentinel)
 * [12..43] Bounds               : 4 x Double (32 bytes, minLat/minLng/maxLat/maxLng WGS84)
 * [44..]   Grid                 : width*height big-endian shorts, zlib(deflate) compressed
 */
object LosTileCodec {
    const val MAGIC_0: Byte = 0x41
    const val MAGIC_1: Byte = 0x54
    const val MAGIC_2: Byte = 0x4C
    const val MAGIC_3: Byte = 0x53
    const val VERSION: Int = 1
    const val HEADER_BYTES: Int = 44

    fun tileFile(root: File, z: Int, x: Int, y: Int): File {
        return File(File(File(root, LOS_DIR_NAME), z.toString()), "$x/$y.bin")
    }

    fun encode(
        width: Int,
        height: Int,
        nodata: Short,
        bounds: DemBounds,
        grid: ShortArray,
    ): ByteArray {
        require(width > 0 && height > 0) { "LOS tile dimensions must be positive." }
        require(grid.size == width * height) { "LOS grid size must equal width*height." }
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeByte(MAGIC_0.toInt())
            out.writeByte(MAGIC_1.toInt())
            out.writeByte(MAGIC_2.toInt())
            out.writeByte(MAGIC_3.toInt())
            out.writeShort(VERSION)
            out.writeShort(width)
            out.writeShort(height)
            out.writeShort(nodata.toInt())
            out.writeDouble(bounds.minLat)
            out.writeDouble(bounds.minLng)
            out.writeDouble(bounds.maxLat)
            out.writeDouble(bounds.maxLng)
            for (sample in grid) out.writeShort(sample.toInt())
        }
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(raw.toByteArray())
            deflater.finish()
            val packed = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(chunk)
                packed.write(chunk, 0, count)
            }
            return packed.toByteArray()
        } finally {
            deflater.end()
        }
    }

    fun writeTile(
        root: File,
        z: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        nodata: Short,
        bounds: DemBounds,
        grid: ShortArray,
    ): File {
        val file = tileFile(root, z, x, y)
        file.parentFile?.mkdirs()
        file.writeBytes(encode(width, height, nodata, bounds, grid))
        return file
    }
}
