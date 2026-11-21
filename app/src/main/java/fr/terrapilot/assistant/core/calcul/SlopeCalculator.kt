package fr.terrapilot.assistant.core.calcul

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt

data class SlopeResult(
    val percent: Double,
    val cmPerMeter: Double,
    val deltaHeightMeters: Double,
    val distanceMeters: Double,
    val startAltitude: Double,
    val endAltitude: Double,
    val angleDegrees: Double,
    val isAscending: Boolean
)

data class SlopeProfilePoint(
    val distanceMeters: Double,
    val altitudeMeters: Double,
    val deltaFromStartMeters: Double
)

object SlopeCalculator {
    fun fromPercent(percent: Double, distanceMeters: Double, startAltitude: Double = 0.0): SlopeResult {
        require(distanceMeters > 0.0) { "La distance doit être strictement positive" }
        val delta = (percent / 100.0) * distanceMeters
        val endAlt = startAltitude + delta
        val angle = Math.toDegrees(atan(percent / 100.0))
        return SlopeResult(
            percent = percent,
            cmPerMeter = percent,
            deltaHeightMeters = delta,
            distanceMeters = distanceMeters,
            startAltitude = startAltitude,
            endAltitude = endAlt,
            angleDegrees = angle,
            isAscending = delta >= 0.0
        )
    }

    fun fromCmPerMeter(cmPerMeter: Double, distanceMeters: Double, startAltitude: Double = 0.0): SlopeResult {
        return fromPercent(cmPerMeter, distanceMeters, startAltitude)
    }

    fun fromAltitudes(startAltitude: Double, endAltitude: Double, distanceMeters: Double): SlopeResult {
        require(distanceMeters > 0.0) { "La distance doit être strictement positive" }
        val delta = endAltitude - startAltitude
        val percent = (delta / distanceMeters) * 100.0
        val angle = Math.toDegrees(atan(delta / distanceMeters))
        return SlopeResult(
            percent = percent,
            cmPerMeter = percent,
            deltaHeightMeters = delta,
            distanceMeters = distanceMeters,
            startAltitude = startAltitude,
            endAltitude = endAltitude,
            angleDegrees = angle,
            isAscending = delta >= 0.0
        )
    }

    fun longitudinalProfile(result: SlopeResult, stepMeters: Double = 1.0): List<SlopeProfilePoint> {
        require(stepMeters > 0.0) { "Le pas doit être positif" }
        val points = mutableListOf<SlopeProfilePoint>()
        var d = 0.0
        while (d <= result.distanceMeters + 1e-9) {
            val clampedD = minOf(d, result.distanceMeters)
            val delta = (result.percent / 100.0) * clampedD
            points.add(
                SlopeProfilePoint(
                    distanceMeters = (clampedD * 100.0).roundToInt() / 100.0,
                    altitudeMeters = result.startAltitude + delta,
                    deltaFromStartMeters = delta
                )
            )
            if (abs(clampedD - result.distanceMeters) < 1e-9) break
            d += stepMeters
        }
        return points
    }
}
