package fr.terrapilot.assistant.core.calcul

import kotlin.math.hypot
import kotlin.math.pow

data class SurveyPointData(
    val id: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val method: String
)

data class SurveyReliability(
    val isReliableForVolume: Boolean,
    val message: String,
    val pointCount: Int,
    val minZ: Double?,
    val maxZ: Double?
)

object SurveyCalculator {
    fun assessReliability(points: List<SurveyPointData>): SurveyReliability {
        if (points.size < 4) {
            return SurveyReliability(
                isReliableForVolume = false,
                message = "Nombre de points insuffisant (${points.size}/4 minimum) pour un calcul de volume fiable.",
                pointCount = points.size,
                minZ = points.minOfOrNull { it.z },
                maxZ = points.maxOfOrNull { it.z }
            )
        }
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val spanX = (xs.maxOrNull() ?: 0.0) - (xs.minOrNull() ?: 0.0)
        val spanY = (ys.maxOrNull() ?: 0.0) - (ys.minOrNull() ?: 0.0)
        if (spanX < 0.5 || spanY < 0.5) {
            return SurveyReliability(
                isReliableForVolume = false,
                message = "Répartition spatiale insuffisante (emprise trop étroite) pour modéliser une surface.",
                pointCount = points.size,
                minZ = points.minOfOrNull { it.z },
                maxZ = points.maxOfOrNull { it.z }
            )
        }
        return SurveyReliability(
            isReliableForVolume = true,
            message = "Relevé exploitable (${points.size} points, emprise ${"%.1f".format(spanX)} × ${"%.1f".format(spanY)} m).",
            pointCount = points.size,
            minZ = points.minOfOrNull { it.z },
            maxZ = points.maxOfOrNull { it.z }
        )
    }

    /**
     * Calcul des déblais/remblais par interpolation IDW (Inverse Distance Weighting)
     * sur une grille régulière N×N couvrant l'emprise du relevé.
     */
    fun gridIdwCutFill(
        points: List<SurveyPointData>,
        targetZ: Double,
        gridResolution: Int = 10,
        swellFactor: Double = 1.25
    ): CutFillSummary {
        require(points.size >= 4) { "Au moins 4 points sont requis pour la grille IDW" }
        require(gridResolution >= 2) { "Résolution de grille >= 2 requise" }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val dx = (maxX - minX) / gridResolution
        val dy = (maxY - minY) / gridResolution
        val cellArea = dx * dy

        val existing = mutableListOf<Double>()
        val target = mutableListOf<Double>()

        for (ix in 0 until gridResolution) {
            val cx = minX + (ix + 0.5) * dx
            for (iy in 0 until gridResolution) {
                val cy = minY + (iy + 0.5) * dy
                val zInterp = interpolateIdw(points, cx, cy)
                existing.add(zInterp)
                target.add(targetZ)
            }
        }
        return VolumeCalculator.cutFillFromGrid(existing, target, cellArea, swellFactor)
    }

    private fun interpolateIdw(points: List<SurveyPointData>, x: Double, y: Double, power: Double = 2.0): Double {
        var num = 0.0
        var den = 0.0
        for (p in points) {
            val d = hypot(p.x - x, p.y - y)
            if (d < 1e-6) return p.z
            val w = 1.0 / d.pow(power)
            num += w * p.z
            den += w
        }
        return num / den
    }
}
