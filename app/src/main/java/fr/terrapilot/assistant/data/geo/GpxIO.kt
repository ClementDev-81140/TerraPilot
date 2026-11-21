package fr.terrapilot.assistant.data.geo

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream

data class GpxWaypoint(val name: String, val lat: Double, val lon: Double, val ele: Double?)
data class GpxTrack(val name: String, val points: List<GpxWaypoint>)
data class GpxData(val waypoints: List<GpxWaypoint>, val tracks: List<GpxTrack>)

object GpxIO {
    fun parse(input: InputStream): GpxData {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val waypoints = mutableListOf<GpxWaypoint>()
        val tracks = mutableListOf<GpxTrack>()
        var currentTrackName = "Trace"
        var currentTrackPoints = mutableListOf<GpxWaypoint>()
        var inTrk = false
        var inTrkPt = false
        var inWpt = false
        var lat = 0.0
        var lon = 0.0
        var ele: Double? = null
        var name = ""
        var currentTag = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "wpt" -> {
                            inWpt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            ele = null
                            name = ""
                        }
                        "trk" -> {
                            inTrk = true
                            currentTrackName = "Trace"
                            currentTrackPoints = mutableListOf()
                        }
                        "trkpt" -> {
                            inTrkPt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            ele = null
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "name" -> if (inWpt) name = text else if (inTrk && !inTrkPt) currentTrackName = text
                            "ele" -> if (inWpt || inTrkPt) ele = text.toDoubleOrNull()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "wpt" -> {
                            waypoints.add(GpxWaypoint(name.ifBlank { "Repère" }, lat, lon, ele))
                            inWpt = false
                        }
                        "trkpt" -> {
                            currentTrackPoints.add(GpxWaypoint("", lat, lon, ele))
                            inTrkPt = false
                        }
                        "trk" -> {
                            tracks.add(GpxTrack(currentTrackName, currentTrackPoints))
                            inTrk = false
                        }
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
        return GpxData(waypoints, tracks)
    }

    fun write(output: OutputStream, data: GpxData) {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="TerraPilot">""").append("\n")
        for (w in data.waypoints) {
            sb.append("""  <wpt lat="${w.lat}" lon="${w.lon}">""")
            if (w.ele != null) sb.append("<ele>${w.ele}</ele>")
            sb.append("<name>${escapeXml(w.name)}</name></wpt>\n")
        }
        for (t in data.tracks) {
            sb.append("  <trk><name>${escapeXml(t.name)}</name><trkseg>\n")
            for (p in t.points) {
                sb.append("""    <trkpt lat="${p.lat}" lon="${p.lon}">""")
                if (p.ele != null) sb.append("<ele>${p.ele}</ele>")
                sb.append("</trkpt>\n")
            }
            sb.append("  </trkseg></trk>\n")
        }
        sb.append("</gpx>\n")
        output.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
