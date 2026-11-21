package fr.terrapilot.assistant.core.calcul

import kotlin.math.abs
import kotlin.math.roundToInt

enum class LevelStatus {
    TROP_HAUT,
    TROP_BAS,
    NIVEAU_ATTEINT
}

data class LevelEvaluation(
    val laserPlaneAltitude: Double,
    val pointAltitude: Double,
    val targetAltitude: Double,
    val diffMeters: Double,
    val diffRoundedHalfCm: Double,
    val depthToDigMeters: Double,
    val fillNeededMeters: Double,
    val status: LevelStatus
)

object LevelCalculator {
    fun computeLaserPlane(referenceAltitude: Double, referenceStaffReadingMeters: Double): Double {
        return referenceAltitude + referenceStaffReadingMeters
    }

    fun evaluatePoint(
        laserPlaneAltitude: Double,
        staffReadingMeters: Double,
        targetAltitude: Double,
        toleranceMeters: Double = 0.005
    ): LevelEvaluation {
        val pointAlt = laserPlaneAltitude - staffReadingMeters
        val diff = pointAlt - targetAltitude
        val roundedHalfCm = ((diff * 200.0).roundToInt()) / 200.0
        val status = when {
            abs(diff) <= toleranceMeters -> LevelStatus.NIVEAU_ATTEINT
            diff > 0 -> LevelStatus.TROP_HAUT
            else -> LevelStatus.TROP_BAS
        }
        return LevelEvaluation(
            laserPlaneAltitude = laserPlaneAltitude,
            pointAltitude = pointAlt,
            targetAltitude = targetAltitude,
            diffMeters = diff,
            diffRoundedHalfCm = roundedHalfCm,
            depthToDigMeters = if (diff > 0) diff else 0.0,
            fillNeededMeters = if (diff < 0) -diff else 0.0,
            status = status
        )
    }
}
