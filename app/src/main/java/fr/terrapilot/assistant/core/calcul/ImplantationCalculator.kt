package fr.terrapilot.assistant.core.calcul

import kotlin.math.abs
import kotlin.math.hypot

data class RectangleImplantation(
    val lengthMeters: Double,
    val widthMeters: Double,
    val expectedDiagonalMeters: Double,
    val areaM2: Double,
    val perimeterM: Double
)

data class SquaringCheck(
    val expectedDiagonalMeters: Double,
    val measuredDiagonal1Meters: Double,
    val measuredDiagonal2Meters: Double,
    val differenceMeters: Double,
    val isSquare: Boolean
)

object ImplantationCalculator {
    fun rectangle(lengthMeters: Double, widthMeters: Double): RectangleImplantation {
        val diag = hypot(lengthMeters, widthMeters)
        return RectangleImplantation(
            lengthMeters = lengthMeters,
            widthMeters = widthMeters,
            expectedDiagonalMeters = diag,
            areaM2 = lengthMeters * widthMeters,
            perimeterM = 2.0 * (lengthMeters + widthMeters)
        )
    }

    fun checkSquaring(
        lengthMeters: Double,
        widthMeters: Double,
        diag1Meters: Double,
        diag2Meters: Double,
        toleranceMeters: Double = 0.01
    ): SquaringCheck {
        val expected = hypot(lengthMeters, widthMeters)
        val diff = abs(diag1Meters - diag2Meters)
        return SquaringCheck(
            expectedDiagonalMeters = expected,
            measuredDiagonal1Meters = diag1Meters,
            measuredDiagonal2Meters = diag2Meters,
            differenceMeters = diff,
            isSquare = diff <= toleranceMeters && abs(diag1Meters - expected) <= toleranceMeters
        )
    }

    fun distanceBetween(x1: Double, y1: Double, x2: Double, y2: Double): Double = hypot(x2 - x1, y2 - y1)
}
