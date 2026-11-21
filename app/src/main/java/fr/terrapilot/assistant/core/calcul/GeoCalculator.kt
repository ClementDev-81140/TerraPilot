package fr.terrapilot.assistant.core.calcul

import kotlin.math.*

data class LocalEnu(val eastMeters: Double, val northMeters: Double)

object GeoCalculator {
    private const val EARTH_RADIUS_M = 6371000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    fun toLocalEnu(originLat: Double, originLon: Double, lat: Double, lon: Double): LocalEnu {
        val dLat = Math.toRadians(lat - originLat)
        val dLon = Math.toRadians(lon - originLon)
        val north = dLat * EARTH_RADIUS_M
        val east = dLon * EARTH_RADIUS_M * cos(Math.toRadians(originLat))
        return LocalEnu(eastMeters = east, northMeters = north)
    }
}
