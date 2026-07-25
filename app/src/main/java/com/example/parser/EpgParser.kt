package com.example.parser

import android.util.Xml
import com.example.ui.viewmodel.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object EpgParser {

    private val inputDateFormatterFull = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val inputDateFormatterShort = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    private val outputTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun parseDate(dateStr: String): Date? {
        val trimmed = dateStr.trim()
        return try {
            inputDateFormatterFull.parse(trimmed)
        } catch (e: Exception) {
            try {
                inputDateFormatterShort.parse(trimmed)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun formatTime(date: Date?): String {
        if (date == null) return ""
        return outputTimeFormatter.format(date)
    }

    /**
     * Parses the EPG XML stream and returns a mapping of:
     * - Key: channel ID or channel display name (lowercased, trimmed)
     * - Value: List of parsed programs sorted chronologically
     */
    fun parse(inputStream: InputStream): Map<String, List<EpgProgram>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        val channelIdToName = mutableMapOf<String, String>()
        val programs = mutableListOf<RawEpgProgram>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            var displayName = ""
                            // Move to inside the channel tag to find display-name
                            var childType = parser.next()
                            var displayNameFound = false
                            while (childType != XmlPullParser.END_TAG || parser.name != "channel") {
                                if (childType == XmlPullParser.START_TAG && parser.name == "display-name") {
                                    displayName = parser.nextText().trim()
                                    displayNameFound = true
                                }
                                childType = parser.next()
                            }
                            if (id.isNotEmpty() && displayName.isNotEmpty()) {
                                channelIdToName[id] = displayName
                            }
                        }
                        "programme" -> {
                            val startAttr = parser.getAttributeValue(null, "start") ?: ""
                            val stopAttr = parser.getAttributeValue(null, "stop") ?: ""
                            val channelAttr = parser.getAttributeValue(null, "channel") ?: ""
                            
                            var title = ""
                            var desc = ""

                            var childType = parser.next()
                            while (!(childType == XmlPullParser.END_TAG && parser.name == "programme")) {
                                if (childType == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "title" -> title = parser.nextText().trim()
                                        "desc" -> desc = parser.nextText().trim()
                                    }
                                }
                                childType = parser.next()
                            }

                            if (channelAttr.isNotEmpty() && title.isNotEmpty()) {
                                programs.add(
                                    RawEpgProgram(
                                        channelId = channelAttr,
                                        title = title,
                                        description = desc,
                                        startRaw = startAttr,
                                        stopRaw = stopAttr
                                    )
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // Match raw programmes and map them
        val result = mutableMapOf<String, MutableList<EpgProgram>>()
        val now = Date()

        for (raw in programs) {
            val startDate = parseDate(raw.startRaw)
            val stopDate = parseDate(raw.stopRaw)

            // Optimize: Skip programs older than 6 hours to save memory and avoid cluttering
            if (stopDate != null && stopDate.time < (now.time - 6 * 3600 * 1000)) {
                continue
            }

            val epgProg = EpgProgram(
                title = raw.title,
                description = raw.description,
                startTime = raw.startRaw,
                stopTime = raw.stopRaw,
                displayStart = formatTime(startDate),
                displayEnd = formatTime(stopDate),
                startDateRaw = startDate,
                stopDateRaw = stopDate
            )

            // Map using the channel ID as key
            val listById = result.getOrPut(raw.channelId.lowercase()) { mutableListOf() }
            listById.add(epgProg)

            // Also map using the channel display-name if available, for fuzzy alignment with the M3U
            val channelName = channelIdToName[raw.channelId]
            if (channelName != null) {
                val listByName = result.getOrPut(channelName.lowercase()) { mutableListOf() }
                listByName.add(epgProg)
            }
        }

        // Sort program times for chronological accuracy
        for (key in result.keys) {
            result[key]?.sortBy { it.startDateRaw ?: Date(0) }
        }

        return result
    }

    private data class RawEpgProgram(
        val channelId: String,
        val title: String,
        val description: String,
        val startRaw: String,
        val stopRaw: String
    )
}
