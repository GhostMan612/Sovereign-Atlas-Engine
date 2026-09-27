// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import android.util.Xml
import com.sovereignatlas.atlas.geo.cot.CotParser
import com.sovereignatlas.atlas.geo.cot.CotPli
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser

class AtakXmlParser : CotParser {
    override fun parse(packetData: ByteArray): CotPli? {
        return runCatching {
            var offset = 0
            if (packetData.size >= 3 &&
                packetData[0] == 0xBF.toByte() &&
                packetData[2] == 0xBF.toByte()
            ) {
                when (packetData[1]) {
                    0x00.toByte() -> offset = 3
                    0x01.toByte() -> return null
                    else -> return null
                }
            }

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(
                ByteArrayInputStream(packetData, offset, packetData.size - offset),
                null,
            )

            var uid = ""
            var type = ""
            var callsign = ""
            var lat = 0.0
            var lon = 0.0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name ?: "") {
                        "event" -> {
                            uid = parser.getAttributeValue(null, "uid") ?: ""
                            type = parser.getAttributeValue(null, "type") ?: ""
                        }
                        "point" -> {
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        }
                        "contact" -> {
                            callsign = parser.getAttributeValue(null, "callsign") ?: ""
                        }
                    }
                }
                eventType = parser.next()
            }
            if (uid.isEmpty() || lat == 0.0 || lon == 0.0) return null
            if (callsign.isEmpty()) callsign = uid.takeLast(4)

            CotPli(uid, type, callsign, lat, lon, System.currentTimeMillis())
        }.getOrNull()
    }
}
