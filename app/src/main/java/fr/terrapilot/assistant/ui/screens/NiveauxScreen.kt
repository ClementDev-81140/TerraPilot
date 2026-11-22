package fr.terrapilot.assistant.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.core.calcul.LevelCalculator
import fr.terrapilot.assistant.core.calcul.LevelStatus
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.HistoriqueEntity
import fr.terrapilot.assistant.data.db.NiveauEntity
import fr.terrapilot.assistant.domain.AppSettings
import fr.terrapilot.assistant.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun NiveauxScreen(db: AppDatabase, chantierId: Long, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val niveaux by db.niveauDao().observeByChantier(chantierId).collectAsState(initial = emptyList())

    var refAlt by remember { mutableStateOf("100.00") }
    var refMire by remember { mutableStateOf("1.50") }
    var nomPoint by remember { mutableStateOf("P1") }
    var mirePoint by remember { mutableStateOf("1.20") }
    var cibleAlt by remember { mutableStateOf("100.20") }
    var modeAffichage by remember { mutableStateOf(1) } // 1 Simple, 2 Numérique, 3 Graphique

    val refA = refAlt.replace(',', '.').toDoubleOrNull() ?: 100.0
    val refM = refMire.replace(',', '.').toDoubleOrNull() ?: 1.5
    val ptM = mirePoint.replace(',', '.').toDoubleOrNull() ?: 1.2
    val cibA = cibleAlt.replace(',', '.').toDoubleOrNull() ?: 100.2
    val planLaser = LevelCalculator.computeLaserPlane(refA, refM)
    val eval = LevelCalculator.evaluatePoint(planLaser, ptM, cibA)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WarningBanner("Affichage par pas de 0,5 cm (5 mm). La précision réelle dépend du laser, de la mire et de la portée.")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = modeAffichage == 1, onClick = { modeAffichage = 1 }, label = { Text("Simple") })
                FilterChip(selected = modeAffichage == 2, onClick = { modeAffichage = 2 }, label = { Text("Numérique") })
                FilterChip(selected = modeAffichage == 3, onClick = { modeAffichage = 3 }, label = { Text("Graphique") })
            }
        }
        item {
            UnitNumberField(refAlt, { refAlt = it }, "Altitude de référence", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(refMire, { refMire = it }, "Lecture mire sur référence", "m")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(nomPoint, { nomPoint = it }, label = { Text("Nom du point") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            UnitNumberField(mirePoint, { mirePoint = it }, "Lecture mire au point", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(cibleAlt, { cibleAlt = it }, "Cote finale souhaitée", "m")
        }

        item {
            val statusColor = when (eval.status) {
                LevelStatus.NIVEAU_ATTEINT -> Color(0xFF2E7D32)
                LevelStatus.TROP_HAUT -> Color(0xFFD96B00)
                LevelStatus.TROP_BAS -> Color(0xFFC62828)
            }
            val statusLabel = when (eval.status) {
                LevelStatus.NIVEAU_ATTEINT -> "NIVEAU ATTEINT"
                LevelStatus.TROP_HAUT -> "TROP HAUT (à creuser)"
                LevelStatus.TROP_BAS -> "TROP BAS (à remblayer)"
            }
            ResultCard("Résultat — Plan laser ${"%.3f".format(planLaser)} m") {
                Text(statusLabel, color = statusColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (modeAffichage >= 2) {
                    Text("Altitude mesurée : ${"%.3f".format(eval.pointAltitude)} m")
                    Text("Cible : ${"%.3f".format(eval.targetAltitude)} m")
                    val shownDiff = if (settings.displayHalfCm) eval.diffRoundedHalfCm else eval.diffMeters
                    Text("Écart : ${"%+.3f".format(shownDiff)} m (${"%+.1f".format(shownDiff * 100)} cm)")
                    if (eval.depthToDigMeters > 0) Text("Profondeur à creuser : ${"%.1f".format(eval.depthToDigMeters * 100)} cm")
                    if (eval.fillNeededMeters > 0) Text("Remblai nécessaire : ${"%.1f".format(eval.fillNeededMeters * 100)} cm")
                }
                if (modeAffichage == 3) {
                    val ratio = ((eval.diffMeters.coerceIn(-0.5, 0.5) + 0.5)).toFloat()
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color.LightGray)) {
                        Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(statusColor))
                    }
                }
            }
        }

        item {
            BigActionButton("Enregistrer le point dans le chantier", onClick = {
                if (chantierId > 0) {
                    scope.launch {
                        db.niveauDao().insert(
                            NiveauEntity(
                                chantierId = chantierId,
                                nomPoint = nomPoint,
                                referenceAlt = refA,
                                lectureMireRef = refM,
                                planLaser = planLaser,
                                lectureMirePoint = ptM,
                                altitudePoint = eval.pointAltitude,
                                cibleAlt = cibA,
                                ecartMetres = eval.diffMeters,
                                statut = eval.status.name
                            )
                        )
                        db.historiqueDao().insert(
                            HistoriqueEntity(chantierId = chantierId, module = "Niveaux", resume = "$nomPoint : ${eval.status.name} (${"%+.1f".format(eval.diffMeters * 100)} cm)")
                        )
                        if (settings.soundAlertsEnabled) {
                            try {
                                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, settings.soundVolumePercent.coerceIn(0, 100))
                                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }

        items(niveaux) { n ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${n.nomPoint} — ${n.statut}", fontWeight = FontWeight.Bold)
                    Text("Alt: ${"%.3f".format(n.altitudePoint)} m | Cible: ${"%.3f".format(n.cibleAlt)} m | Écart: ${"%+.1f".format(n.ecartMetres * 100)} cm")
                }
            }
        }
    }
}
