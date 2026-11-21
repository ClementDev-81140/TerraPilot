package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.core.calcul.SlopeCalculator
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.HistoriqueEntity
import fr.terrapilot.assistant.data.db.PenteEntity
import fr.terrapilot.assistant.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun PentesScreen(db: AppDatabase, chantierId: Long) {
    val scope = rememberCoroutineScope()
    val pentes by db.penteDao().observeByChantier(chantierId).collectAsState(initial = emptyList())

    var mode by remember { mutableStateOf("A") } // A %, B cm/m, C 2 altitudes
    var libelle by remember { mutableStateOf("Tranchée évacuation") }
    var pct by remember { mutableStateOf("2.0") }
    var cmM by remember { mutableStateOf("2.0") }
    var altDep by remember { mutableStateOf("100.00") }
    var altArr by remember { mutableStateOf("99.80") }
    var dist by remember { mutableStateOf("10.0") }
    var showProfile by remember { mutableStateOf(false) }

    val d = (dist.replace(',', '.').toDoubleOrNull() ?: 10.0).coerceAtLeast(0.01)
    val result = when (mode) {
        "A" -> SlopeCalculator.fromPercent(pct.replace(',', '.').toDoubleOrNull() ?: 2.0, d, altDep.replace(',', '.').toDoubleOrNull() ?: 100.0)
        "B" -> SlopeCalculator.fromCmPerMeter(cmM.replace(',', '.').toDoubleOrNull() ?: 2.0, d, altDep.replace(',', '.').toDoubleOrNull() ?: 100.0)
        else -> SlopeCalculator.fromAltitudes(
            altDep.replace(',', '.').toDoubleOrNull() ?: 100.0,
            altArr.replace(',', '.').toDoubleOrNull() ?: 99.8,
            d
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "A", onClick = { mode = "A" }, label = { Text("Mode A (%)") })
                FilterChip(selected = mode == "B", onClick = { mode = "B" }, label = { Text("Mode B (cm/m)") })
                FilterChip(selected = mode == "C", onClick = { mode = "C" }, label = { Text("Mode C (2 alt)") })
            }
        }
        item {
            OutlinedTextField(libelle, { libelle = it }, label = { Text("Libellé") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            when (mode) {
                "A" -> UnitNumberField(pct, { pct = it }, "Pente (+ montante, - descendante)", "%")
                "B" -> UnitNumberField(cmM, { cmM = it }, "Pente (+ montante, - descendante)", "cm/m")
                else -> {
                    UnitNumberField(altDep, { altDep = it }, "Altitude départ", "m")
                    Spacer(Modifier.height(6.dp))
                    UnitNumberField(altArr, { altArr = it }, "Altitude arrivée", "m")
                }
            }
            Spacer(Modifier.height(6.dp))
            UnitNumberField(dist, { dist = it }, "Distance horizontale", "m")
        }
        item {
            ResultCard("Résultat pente (${if (result.isAscending) "Montante +" else "Descendante -"})") {
                Text("Pente : ${"%.2f".format(result.percent)} % | ${"%.2f".format(result.cmPerMeter)} cm/m", fontWeight = FontWeight.Bold)
                Text("Dénivelé : ${"%+.3f".format(result.deltaHeightMeters)} m (${"%+.1f".format(result.deltaHeightMeters * 100)} cm)")
                Text("Départ : ${"%.3f".format(result.startAltitude)} m → Arrivée : ${"%.3f".format(result.endAltitude)} m")
                Text("Angle : ${"%.2f".format(result.angleDegrees)}°")
                OutlinedButton(onClick = { showProfile = !showProfile }) {
                    Text(if (showProfile) "Masquer le profil mètre par mètre" else "Afficher le profil mètre par mètre")
                }
                if (showProfile) {
                    SlopeCalculator.longitudinalProfile(result, 1.0).forEach { p ->
                        Text("${"%.1f".format(p.distanceMeters)} m → alt ${"%.3f".format(p.altitudeMeters)} m (${"%+.1f".format(p.deltaFromStartMeters * 100)} cm)")
                    }
                }
            }
        }
        item {
            BigActionButton("Enregistrer dans le chantier", onClick = {
                if (chantierId > 0) {
                    scope.launch {
                        db.penteDao().insert(
                            PenteEntity(
                                chantierId = chantierId,
                                libelle = libelle,
                                pourcentage = result.percent,
                                cmParMetre = result.cmPerMeter,
                                distanceMetres = result.distanceMeters,
                                deniveleMetres = result.deltaHeightMeters
                            )
                        )
                        db.historiqueDao().insert(
                            HistoriqueEntity(chantierId = chantierId, module = "Pentes", resume = "$libelle : ${"%.2f".format(result.percent)} %")
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        items(pentes) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.libelle, fontWeight = FontWeight.Bold)
                    Text("${"%.2f".format(p.pourcentage)} % (${"%.2f".format(p.cmParMetre)} cm/m) sur ${p.distanceMetres} m")
                }
            }
        }
    }
}
