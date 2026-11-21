package fr.terrapilot.assistant.core.calcul

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

    fun averageTargetComparison(points: List<SurveyPointData>, targetZ: Double, areaM2: Double): CutFillSummary {
        val existing = points.map { it.z }
        val target = List(points.size) { targetZ }
        val cellArea = if (points.isNotEmpty()) areaM2 / points.size else 0.0
        return VolumeCalculator.cutFillFromGrid(existing, target, cellArea)
    }
}
