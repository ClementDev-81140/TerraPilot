package fr.terrapilot.assistant.core.calcul

data class TrenchSection(
    val lengthMeters: Double,
    val widthMeters: Double,
    val depthMeters: Double
) {
    val surfaceM2: Double get() = lengthMeters * widthMeters
    val volumeM3: Double get() = lengthMeters * widthMeters * depthMeters
}

data class VolumeSummary(
    val netVolumeM3: Double,
    val surfaceM2: Double,
    val swellFactor: Double,
    val swelledVolumeM3: Double
)

data class CutFillSummary(
    val cutM3: Double,
    val fillM3: Double,
    val balanceM3: Double,
    val swellFactor: Double,
    val swelledCutM3: Double
)

object VolumeCalculator {
    fun trench(sections: List<TrenchSection>, swellFactor: Double = 1.25): VolumeSummary {
        val surface = sections.sumOf { it.surfaceM2 }
        val volume = sections.sumOf { it.volumeM3 }
        return VolumeSummary(
            netVolumeM3 = volume,
            surfaceM2 = surface,
            swellFactor = swellFactor,
            swelledVolumeM3 = volume * swellFactor
        )
    }

    fun platform(lengthMeters: Double, widthMeters: Double, thicknessMeters: Double, swellFactor: Double = 1.25): VolumeSummary {
        val surface = lengthMeters * widthMeters
        val volume = surface * thicknessMeters
        return VolumeSummary(
            netVolumeM3 = volume,
            surfaceM2 = surface,
            swellFactor = swellFactor,
            swelledVolumeM3 = volume * swellFactor
        )
    }

    fun cutFillFromGrid(
        existingHeights: List<Double>,
        targetHeights: List<Double>,
        cellAreaM2: Double,
        swellFactor: Double = 1.25
    ): CutFillSummary {
        require(existingHeights.size == targetHeights.size) { "Les grilles doivent avoir la même taille" }
        var cut = 0.0
        var fill = 0.0
        for (i in existingHeights.indices) {
            val diff = existingHeights[i] - targetHeights[i]
            if (diff > 0) cut += diff * cellAreaM2
            else if (diff < 0) fill += (-diff) * cellAreaM2
        }
        return CutFillSummary(
            cutM3 = cut,
            fillM3 = fill,
            balanceM3 = cut - fill,
            swellFactor = swellFactor,
            swelledCutM3 = cut * swellFactor
        )
    }
}
