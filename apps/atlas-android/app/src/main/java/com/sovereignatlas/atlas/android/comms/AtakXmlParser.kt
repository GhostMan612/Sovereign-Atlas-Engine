// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import android.util.Xml
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.CotParser
import com.sovereignatlas.atlas.geo.cot.CotPli
import com.sovereignatlas.atlas.geo.cot.ParsedCot
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import org.xmlpull.v1.XmlPullParser

class AtakXmlParser : CotParser {
    override fun parse(packetData: ByteArray): ParsedCot? {
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

            var eventTypeAttr: String? = null
            var eventUid: String? = null
            var senderUid = ""
            var senderCallsign = "Unknown"
            var chatroom = "All Chat Rooms"
            var remarksTo = "All Chat Rooms"
            var text = ""
            var timestampMillis = System.currentTimeMillis()
            var isChat = false

            var uid = ""
            var type = ""
            var callsign = ""
            var lat = 0.0
            var lon = 0.0

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "event" -> {
                            eventTypeAttr = parser.getAttributeValue(null, "type")
                            eventUid = parser.getAttributeValue(null, "uid")
                            if (eventTypeAttr == "b-t-f" && eventUid?.startsWith("GeoChat.") == true) {
                                isChat = true
                            }
                            if (!isChat) {
                                uid = eventUid ?: ""
                                type = eventTypeAttr ?: ""
                            }
                        }
                        "__chat" -> {
                            if (isChat) {
                                senderCallsign = parser.getAttributeValue(null, "senderCallsign") ?: "Unknown"
                                chatroom = parser.getAttributeValue(null, "chatroom") ?: "All Chat Rooms"
                            }
                        }
                        "chatgrp" -> {
                            if (isChat) {
                                val uid0 = parser.getAttributeValue(null, "uid0")
                                if (!uid0.isNullOrEmpty()) senderUid = uid0
                            }
                        }
                        "remarks" -> {
                            if (isChat) {
                                remarksTo = parser.getAttributeValue(null, "to") ?: "All Chat Rooms"
                                parseIso8601(parser.getAttributeValue(null, "time"))?.let {
                                    timestampMillis = it
                                }
                                text = parser.nextText()
                            }
                        }
                        "point" -> {
                            if (!isChat) {
                                lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            }
                        }
                        "contact" -> {
                            if (!isChat) {
                                callsign = parser.getAttributeValue(null, "callsign") ?: ""
                            }
                        }
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG &&
                    parser.name == "event"
                ) {
                    if (isChat && senderUid.isNotEmpty() && eventUid != null) {
                        val messageId = eventUid.substringAfterLast(".")
                        return ParsedCot.Chat(
                            ChatMessage(
                                messageId = messageId,
                                senderUid = senderUid,
                                senderCallsign = senderCallsign,
                                chatroom = chatroom,
                                remarksTo = remarksTo,
                                text = text,
                                timestampMillis = timestampMillis,
                                isSelf = false,
                            ),
                        )
                    } else if (isChat) {
                        return null
                    } else if (eventTypeAttr?.startsWith("a-f-G") == true) {
                        if (uid.isEmpty() || lat == 0.0 || lon == 0.0) return null
                        if (callsign.isEmpty()) callsign = uid.takeLast(4)
                        return ParsedCot.Pli(
                            CotPli(uid, type, callsign, lat, lon, System.currentTimeMillis()),
                        )
                    }
                }
                parser.next()
            }
            null
        }.getOrNull()
    }

    private fun parseIso8601(value: String?): Long? {
        if (value == null) return null
        return runCatching {
            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(value)).toEpochMilli()
        }.getOrNull()
    }
}
