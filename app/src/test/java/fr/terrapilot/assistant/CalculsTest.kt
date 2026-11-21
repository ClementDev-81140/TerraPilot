package fr.terrapilot.assistant

import fr.terrapilot.assistant.core.calcul.*
import org.junit.Assert.*
import org.junit.Test

class CalculsTest {

    @Test
    fun pente_10m_20cm_donne_2_pourcent() {
        val res = SlopeCalculator.fromAltitudes(100.00, 100.20, 10.0)
        assertEquals(2.0, res.percent, 0.001)
        assertEquals(2.0, res.cmPerMeter, 0.001)
        assertTrue(res.isAscending)
    }

    @Test
    fun pente_depuis_pourcentage_et_profil() {
        val res = SlopeCalculator.fromPercent(-2.0, 10.0, 100.0)
        assertEquals(-0.20, res.deltaHeightMeters, 0.001)
        assertEquals(99.80, res.endAltitude, 0.001)
        assertFalse(res.isAscending)
        val profil = SlopeCalculator.longitudinalProfile(res, 1.0)
        assertEquals(11, profil.size)
        assertEquals(99.90, profil[5].altitudeMeters, 0.001)
    }

    @Test
    fun tranchée_10x040x080_donne_3_20_m3() {
        val res = VolumeCalculator.trench(listOf(TrenchSection(10.0, 0.40, 0.80)), swellFactor = 1.25)
        assertEquals(3.20, res.netVolumeM3, 0.001)
        assertEquals(4.00, res.surfaceM2, 0.001)
        assertEquals(4.00, res.swelledVolumeM3, 0.001)
    }

    @Test
    fun plateforme_10x5x020_donne_10_m3() {
        val res = VolumeCalculator.platform(10.0, 5.0, 0.20)
        assertEquals(10.0, res.netVolumeM3, 0.001)
        assertEquals(50.0, res.surfaceM2, 0.001)
    }

    @Test
    fun equerrage_4x3_donne_diagonale_5m() {
        val rect = ImplantationCalculator.rectangle(4.0, 3.0)
        assertEquals(5.0, rect.expectedDiagonalMeters, 0.001)
        val check = ImplantationCalculator.checkSquaring(4.0, 3.0, 5.0, 5.0)
        assertTrue(check.isSquare)
    }

    @Test
    fun nivellement_trop_haut_a_creuser() {
        val plan = LevelCalculator.computeLaserPlane(100.00, 1.50)
        assertEquals(101.50, plan, 0.001)
        val eval = LevelCalculator.evaluatePoint(plan, 1.20, 100.20)
        assertEquals(100.30, eval.pointAltitude, 0.001)
        assertEquals(0.10, eval.diffMeters, 0.001)
        assertEquals(0.10, eval.depthToDigMeters, 0.001)
        assertEquals(LevelStatus.TROP_HAUT, eval.status)
    }

    @Test
    fun nivellement_trop_bas() {
        val plan = LevelCalculator.computeLaserPlane(100.00, 1.50)
        val eval = LevelCalculator.evaluatePoint(plan, 1.60, 100.20)
        assertEquals(99.90, eval.pointAltitude, 0.001)
        assertEquals(-0.30, eval.diffMeters, 0.001)
        assertEquals(0.30, eval.fillNeededMeters, 0.001)
        assertEquals(LevelStatus.TROP_BAS, eval.status)
    }

    @Test
    fun nivellement_arrondi_demi_centimetre() {
        val plan = LevelCalculator.computeLaserPlane(100.00, 1.50)
        val eval = LevelCalculator.evaluatePoint(plan, 1.297, 100.20)
        assertEquals(0.005, eval.diffRoundedHalfCm, 0.0001)
    }

    @Test
    fun releve_fiabilite_insuffisante() {
        val rel = SurveyCalculator.assessReliability(
            listOf(
                SurveyPointData("P1", 0.0, 0.0, 100.0, "ruban"),
                SurveyPointData("P2", 2.0, 0.0, 100.1, "ruban")
            )
        )
        assertFalse(rel.isReliableForVolume)
    }

    @Test
    fun releve_fiabilite_ok() {
        val rel = SurveyCalculator.assessReliability(
            listOf(
                SurveyPointData("P1", 0.0, 0.0, 100.0, "ruban"),
                SurveyPointData("P2", 5.0, 0.0, 100.2, "ruban"),
                SurveyPointData("P3", 5.0, 4.0, 100.1, "ruban"),
                SurveyPointData("P4", 0.0, 4.0, 99.9, "ruban")
            )
        )
        assertTrue(rel.isReliableForVolume)
    }

    @Test
    fun geo_haversine_paris_lyon() {
        val dist = GeoCalculator.haversineMeters(48.8566, 2.3522, 45.7640, 4.8357)
        assertTrue("Distance attendue ~392 km, obtenue $dist", dist in 385_000.0..400_000.0)
    }

    @Test
    fun cut_fill_grid() {
        val res = VolumeCalculator.cutFillFromGrid(
            existingHeights = listOf(100.2, 99.8),
            targetHeights = listOf(100.0, 100.0),
            cellAreaM2 = 10.0
        )
        assertEquals(2.0, res.cutM3, 0.001)
        assertEquals(2.0, res.fillM3, 0.001)
        assertEquals(0.0, res.balanceM3, 0.001)
    }
}
