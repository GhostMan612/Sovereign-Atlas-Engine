// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import java.io.DataInputStream
import java.io.File
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.ln

object AtlasRadioLink {
    fun freeSpaceLossDb(distanceMeters: Double, frequencyMHz: Double): Double {
        if (distanceMeters <= 0 || frequencyMHz <= 0) {
            throw IllegalArgumentException("Distance and frequency must be positive.")
        }
        return 20 * log10(distanceMeters) + 20 * log10(frequencyMHz) - 27.55
    }

    fun linkMarginDb(
        distanceMeters: Double,
        frequencyMHz: Double,
        txPowerDbm: Double,
        rxSensitivityDbm: Double,
        antennaGainDbi: Double = 0.0,
        extraLossDb: Double = 0.0,
    ): Double {
        return txPowerDbm +
            antennaGainDbi -
            extraLossDb -
            freeSpaceLossDb(distanceMeters, frequencyMHz) -
            rxSensitivityDbm
    }

    fun rangeMeters(a: AtlasCoordinate, b: AtlasCoordinate): Double {
        return AtlasGeoMath.haversineKm(a, b) * 1000.0
    }

    fun demHeightMeters(z: Int, x: Int, y: Int, index: Int): Double {
        return DemTileReader.read(z, x, y)?.getOrNull(index)?.toDouble() ?: 0.0
    }

    private fun log10(value: Double): Double = kotlin.math.ln(value) / ln(10.0)
}

object DemTileReader {
    const val LOS_DIR_NAME = "los"
    private const val MAGIC_0: Byte = 0x41
    private const val MAGIC_1: Byte = 0x54
    private const val MAGIC_2: Byte = 0x4C
    private const val MAGIC_3: Byte = 0x53
    private const val VERSION: Int = 1
    private const val HEADER_BYTES: Int = 44

    private var losRoot: File? = null

    fun configure(root: File?) {
        losRoot = root
    }

    fun read(z: Int, x: Int, y: Int): ShortArray? {
        val root = losRoot ?: return null
        val file = File(File(File(root, LOS_DIR_NAME), z.toString()), "$x/$y.bin")
        if (!file.isFile) return null
        return try {
            decode(file.readBytes())
        } catch (error: DataFormatException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun decode(packed: ByteArray): ShortArray {
        val inflater = Inflater()
        try {
            inflater.setInput(packed)
            val raw = ByteArray(HEADER_BYTES + 2)
            var headerRead = 0
            while (headerRead < HEADER_BYTES) {
                val count = inflater.inflate(raw, headerRead, HEADER_BYTES - headerRead)
                if (count <= 0) throw DataFormatException("Truncated LOS header.")
                headerRead += count
            }
            if (raw[0] != MAGIC_0 || raw[1] != MAGIC_1 || raw[2] != MAGIC_2 || raw[3] != MAGIC_3) {
                throw IllegalArgumentException("Bad LOS magic.")
            }
            val head = DataInputStream(raw.inputStream())
            head.skipBytes(4)
            val version = head.readUnsignedShort()
            val width = head.readUnsignedShort()
            val height = head.readUnsignedShort()
            if (version != VERSION || width <= 0 || height <= 0) {
                throw IllegalArgumentException("Unsupported LOS tile.")
            }
            if (width.toLong() * height.toLong() > 1_048_576L) {
                throw IllegalArgumentException("LOS tile grid too large.")
            }
            val grid = ShortArray(width * height)
            val pair = ByteArray(2)
            for (i in grid.indices) {
                var filled = 0
                while (filled < 2) {
                    val count = inflater.inflate(pair, filled, 2 - filled)
                    if (count <= 0) throw DataFormatException("Truncated LOS grid.")
                    filled += count
                }
                grid[i] = ((pair[0].toInt() shl 8) or (pair[1].toInt() and 0xFF)).toShort()
            }
            return grid
        } finally {
            inflater.end()
        }
    }
}
