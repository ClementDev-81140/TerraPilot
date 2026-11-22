package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.core.calcul.SurveyCalculator
import fr.terrapilot.assistant.core.calcul.SurveyPointData
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.RelevePointEntity
import fr.terrapilot.assistant.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun ReleveScreen(db: AppDatabase, chantierId: Long) {
    val scope = rememberCoroutineScope()
    val points by db.releveDao().observeByChantier(chantierId).collectAsState(initial = emptyList())

    var code by remember { mutableStateOf("T1") }
    var x by remember { mutableStateOf("0.0") }
    var y by remember { mutableStateOf("0.0") }
    var z by remember { mutableStateOf("100.00") }
    var methode by remember { mutableStateOf("Mètre ruban") }
    var cibleZ by remember { mutableStateOf("99.80") }

    val surveyData = points.map { SurveyPointData(it.codePoint, it.x, it.y, it.z, it.methode) }
    val rel = SurveyCalculator.assessReliability(surveyData)
    val cz = cibleZ.replace(',', '.').toDoubleOrNull() ?: 99.8
    val cutFill = if (rel.isReliableForVolume) SurveyCalculator.gridIdwCutFill(surveyData, cz) else null

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            WarningBanner(rel.message, isError = !rel.isReliableForVolume)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Mètre ruban", "GPS tablette", "Report plan").forEach { m ->
                    FilterChip(selected = methode == m, onClick = { methode = m }, label = { Text(m) })
                }
            }
            if (methode == "GPS tablette") {
                Spacer(Modifier.height(4.dp))
                WarningBanner("Le GPS d'une tablette offre une précision métrique (~3 à 10 m), jamais centimétrique.")
            }
        }
        item {
            OutlinedTextField(code, { code = it }, label = { Text("Identifiant point") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { UnitNumberField(x, { x = it }, "X", "m") }
                Box(Modifier.weight(1f)) { UnitNumberField(y, { y = it }, "Y", "m") }
                Box(Modifier.weight(1f)) { UnitNumberField(z, { z = it }, "Alt Z", "m") }
            }
            Spacer(Modifier.height(8.dp))
            BigActionButton("Ajouter le point", onClick = {
                if (chantierId > 0) {
                    scope.launch {
                        db.releveDao().insert(
                            RelevePointEntity(
                                chantierId = chantierId,
                                codePoint = code,
                                x = x.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                y = y.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                z = z.replace(',', '.').toDoubleOrNull() ?: 100.0,
                                methode = methode,
                                precisionEstimeeM = if (methode == "GPS tablette") 5.0 else 0.02
                            )
                        )
                        code = "T${points.size + 2}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }

        if (points.isNotEmpty()) {
            item {
                ResultCard("Carte des points (${points.size})") {
                    val minX = points.minOf { it.x }
                    val maxX = points.maxOf { it.x }.coerceAtLeast(minX + 1.0)
                    val minY = points.minOf { it.y }
                    val maxY = points.maxOf { it.y }.coerceAtLeast(minY + 1.0)
                    val minZ = points.minOf { it.z }
                    val maxZ = points.maxOf { it.z }.coerceAtLeast(minZ + 0.01)

                    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                        points.forEach { p ->
                            val px = (((p.x - minX) / (maxX - minX)) * (size.width - 40f) + 20f).toFloat()
                            val py = (size.height - (((p.y - minY) / (maxY - minY)) * (size.height - 40f) + 20f)).toFloat()
                            val t = ((p.z - minZ) / (maxZ - minZ)).toFloat().coerceIn(0f, 1f)
                            val col = Color(red = t, green = 0.3f, blue = 1f - t)
                            drawCircle(color = col, radius = 12f, center = Offset(px, py))
                        }
                    }
                    Text("Point bas : ${"%.2f".format(minZ)} m | Point haut : ${"%.2f".format(maxZ)} m")
                }
            }
        }

        item {
            UnitNumberField(cibleZ, { cibleZ = it }, "Altitude projetée", "m")
            if (cutFill != null) {
                Spacer(Modifier.height(8.dp))
                ResultCard("Comparaison existant / projeté (grille IDW)") {
                    Text("Déblais (à creuser) : ${"%.2f".format(cutFill.cutM3)} m³", fontWeight = FontWeight.Bold)
                    Text("Remblais (à apporter) : ${"%.2f".format(cutFill.fillM3)} m³")
                    Text("Bilan global : ${"%+.2f".format(cutFill.balanceM3)} m³")
                    Text("Hypothèse : interpolation IDW sur la boîte englobante des points. Fiabilité liée à la densité du relevé.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(points) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.codePoint} (${p.methode})", fontWeight = FontWeight.Bold)
                    Text("X=${p.x} m, Y=${p.y} m, Z=${p.z} m")
                }
            }
        }
    }
}
