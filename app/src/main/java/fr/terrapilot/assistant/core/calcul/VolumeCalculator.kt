package fr.terrapilot.assistant.core.calcul

data class TrenchSection(
    val lengthMeters: Double,
    val widthMeters: Double,
    val depthMeters: Double
) {
    init {
        require(lengthMeters > 0.0) { "La longueur de tranchée doit être > 0" }
        require(widthMeters > 0.0) { "La largeur de tranchée doit être > 0" }
        require(depthMeters > 0.0) { "La profondeur de tranchée doit être > 0" }
    }
    val surfaceM2: Double get() = lengthMeters * widthMeters
    val volumeM3: Double get() = lengthMeters * widthMeters * depthMeters
}

data class FoundationFooting(
    val count: Int,
    val lengthMeters: Double,
    val widthMeters: Double,
    val depthMeters: Double
) {
    init {
        require(count > 0) { "Le nombre de semelles doit être > 0" }
        require(lengthMeters > 0.0 && widthMeters > 0.0 && depthMeters > 0.0) { "Les dimensions doivent être > 0" }
    }
    val surfaceM2: Double get() = count * lengthMeters * widthMeters
    val volumeM3: Double get() = surfaceM2 * depthMeters
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
        require(sections.isNotEmpty()) { "Au moins un tronçon est requis" }
        require(swellFactor >= 1.0) { "Le coefficient de foisonnement doit être >= 1.0" }
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
        require(lengthMeters > 0.0 && widthMeters > 0.0 && thicknessMeters > 0.0) { "Les dimensions doivent être > 0" }
        require(swellFactor >= 1.0) { "Le coefficient de foisonnement doit être >= 1.0" }
        val surface = lengthMeters * widthMeters
        val volume = surface * thicknessMeters
        return VolumeSummary(
            netVolumeM3 = volume,
            surfaceM2 = surface,
            swellFactor = swellFactor,
            swelledVolumeM3 = volume * swellFactor
        )
    }

    fun foundations(footings: List<FoundationFooting>, swellFactor: Double = 1.25): VolumeSummary {
        require(footings.isNotEmpty()) { "Au moins une fondation est requise" }
        require(swellFactor >= 1.0) { "Le coefficient de foisonnement doit être >= 1.0" }
        val surface = footings.sumOf { it.surfaceM2 }
        val volume = footings.sumOf { it.volumeM3 }
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
        require(cellAreaM2 > 0.0) { "L'aire de cellule doit être > 0" }
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
