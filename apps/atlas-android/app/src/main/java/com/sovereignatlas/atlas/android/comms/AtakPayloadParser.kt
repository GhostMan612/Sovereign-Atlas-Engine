// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import android.util.Log
import android.util.Xml
import atakmap.commoncommo.v1.TakMessage
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.CotParser
import com.sovereignatlas.atlas.geo.cot.CotPli
import com.sovereignatlas.atlas.geo.cot.ParsedCot
import java.io.ByteArrayInputStream
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

class AtakPayloadParser : CotParser {
    override fun parse(packetData: ByteArray): ParsedCot? {
        if (packetData.size < 3) return null

        if (packetData[0] == 0xBF.toByte() && packetData[2] == 0xBF.toByte()) {
            val version = packetData[1]
            val data = packetData.copyOfRange(3, packetData.size)
            return when (version) {
                0x00.toByte() -> parseXml(String(data, Charsets.UTF_8))
                0x01.toByte() -> parseProtobuf(data)
                else -> null
            }
        }

        return runCatching { parseXml(String(packetData, Charsets.UTF_8)) }.getOrNull()
    }

    fun parse(xml: String): ParsedCot? = parseXml(xml)

    private fun parseXml(xml: String): ParsedCot? {
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(
                ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
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

    private fun parseProtobuf(data: ByteArray): ParsedCot? {
        val takMsg = runCatching { TakMessage.ADAPTER.decode(data) }.getOrNull() ?: return null
        val event = takMsg.cotEvent ?: return null
        val type = event.type

        return when {
            type.startsWith("a-f-") -> {
                val altitude = event.hae.takeIf {
                    it.isFinite() && it < 9999999.0 && (it != 0.0 || event.detail?.precisionLocation != null)
                }
                val callsign = event.detail?.contact?.callsign?.takeIf { it.isNotBlank() } ?: "Unknown"

                ParsedCot.Pli(
                    CotPli(
                        uid = event.uid,
                        type = type,
                        callsign = callsign,
                        latitude = event.lat,
                        longitude = event.lon,
                        timestamp = event.sendTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        altitude = altitude,
                    ),
                )
            }
            type == "b-t-f" -> {
                val xmlDetail = event.detail?.xmlDetail ?: return null

                var senderUid = ""
                var senderCallsign = "Unknown"
                var chatroom = "All Chat Rooms"
                var remarksTo = "All Chat Rooms"
                var text = ""
                var timestampMillis = event.sendTime.takeIf { it > 0 } ?: System.currentTimeMillis()

                runCatching {
                    val parser = Xml.newPullParser()
                    parser.setInput(StringReader("<root>$xmlDetail</root>"))

                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "__chat" -> {
                                    senderCallsign = parser.getAttributeValue(null, "senderCallsign") ?: senderCallsign
                                    chatroom = parser.getAttributeValue(null, "chatroom") ?: chatroom
                                }
                                "chatgrp" -> {
                                    val uid0 = parser.getAttributeValue(null, "uid0")
                                    if (!uid0.isNullOrEmpty()) senderUid = uid0
                                }
                                "remarks" -> {
                                    remarksTo = parser.getAttributeValue(null, "to") ?: remarksTo
                                    parseIso8601(parser.getAttributeValue(null, "time"))?.let {
                                        timestampMillis = it
                                    }
                                    text = parser.nextText()
                                }
                            }
                        }
                        parser.next()
                    }
                }

                if (senderUid.isNotEmpty()) {
                    val messageId = event.uid.substringAfterLast(".", "")
                        .ifEmpty { java.util.UUID.randomUUID().toString() }

                    ParsedCot.Chat(
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
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun parseIso8601(value: String?): Long? {
        if (value == null) return null
        return runCatching {
            java.time.Instant.from(java.time.format.DateTimeFormatter.ISO_INSTANT.parse(value)).toEpochMilli()
        }.getOrNull()
    }
}
