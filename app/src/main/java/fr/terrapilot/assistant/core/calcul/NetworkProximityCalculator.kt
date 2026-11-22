package fr.terrapilot.assistant.core.calcul

import kotlin.math.hypot

data class NetworkSegment(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double
)

data class ProximityAlert(
    val isWithinMargin: Boolean,
    val distanceMeters: Double,
    val effectiveMarginMeters: Double
)

object NetworkProximityCalculator {
    /**
     * Distance minimale d'un point (px, py) à un segment (x1, y1)-(x2, y2) en coordonnées locales (mètres).
     */
    fun distancePointToSegment(px: Double, py: Double, seg: NetworkSegment): Double {
        val dx = seg.x2 - seg.x1
        val dy = seg.y2 - seg.y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9) return hypot(px - seg.x1, py - seg.y1)
        val t = (((px - seg.x1) * dx + (py - seg.y1) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = seg.x1 + t * dx
        val projY = seg.y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    fun classUncertaintyMeters(precisionClass: String): Double = when (precisionClass.uppercase()) {
        "A" -> 0.40
        "B" -> 1.50
        else -> 3.00
    }

    fun evaluateProximity(
        px: Double,
        py: Double,
        seg: NetworkSegment,
        userMarginMeters: Double,
        precisionClass: String
    ): ProximityAlert {
        val dist = distancePointToSegment(px, py, seg)
        val effective = maxOf(userMarginMeters, classUncertaintyMeters(precisionClass))
        return ProximityAlert(
            isWithinMargin = dist <= effective,
            distanceMeters = dist,
            effectiveMarginMeters = effective
        )
    }
}
