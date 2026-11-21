package fr.terrapilot.assistant.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.HistoriqueEntity
import fr.terrapilot.assistant.data.db.ReseauEntity
import fr.terrapilot.assistant.domain.AppSettings
import fr.terrapilot.assistant.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun ReseauxScreen(db: AppDatabase, chantierId: Long, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val reseaux by db.reseauDao().observeByChantier(chantierId).collectAsState(initial = emptyList())

    var type by remember { mutableStateOf("Électricité") }
    var classe by remember { mutableStateOf("B") }
    var fiabilite by remember { mutableStateOf("Approximative") }
    var profondeur by remember { mutableStateOf("0.80") }
    var source by remember { mutableStateOf("Réponse DT-DICT") }
    var marge by remember { mutableStateOf("1.50") }
    var alerteActive by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            WarningBanner(
                "SÉCURITÉ DT-DICT : Cet outil ne remplace ni les DT-DICT, ni les prescriptions des exploitants, ni le marquage-piquetage. Une zone sans réseau cartographié n'est JAMAIS garantie sans danger.",
                isError = true
            )
        }
        item {
            ResultCard("Nouvelle fiche réseau") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Électricité", "Gaz", "Eau", "Télécom").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A", "B", "C").forEach { c ->
                        FilterChip(selected = classe == c, onClick = { classe = c }, label = { Text("Classe $c") })
                    }
                }
                Text("Classe A : ≤ 40 cm (rigide) / ≤ 50 cm (flexible). Classe B : ≤ 1,5 m. Classe C : > 1,5 m ou incertaine.", style = MaterialTheme.typography.bodySmall)
                UnitNumberField(profondeur, { profondeur = it }, "Profondeur connue", "m")
                OutlinedTextField(source, { source = it }, label = { Text("Source (DT-DICT, plan maçon, marquage...)") }, modifier = Modifier.fillMaxWidth())
                UnitNumberField(marge, { marge = it }, "Marge de vigilance (non réglementaire)", "m")
                BigActionButton("Enregistrer le réseau", onClick = {
                    if (chantierId > 0) {
                        scope.launch {
                            db.reseauDao().insert(
                                ReseauEntity(
                                    chantierId = chantierId,
                                    typeReseau = type,
                                    classePrecision = classe,
                                    fiabilite = fiabilite,
                                    profondeurConnueM = profondeur.replace(',', '.').toDoubleOrNull(),
                                    source = source,
                                    dateDocument = "Aujourd'hui",
                                    margeVigilanceM = marge.replace(',', '.').toDoubleOrNull() ?: 1.5,
                                    lat = null,
                                    lon = null
                                )
                            )
                            db.historiqueDao().insert(
                                HistoriqueEntity(chantierId = chantierId, module = "Réseaux", resume = "$type (Classe $classe, prof $profondeur m)")
                            )
                        }
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (alerteActive != null) {
            item { WarningBanner(alerteActive!!, isError = true) }
        }

        items(reseaux) { r ->
            val color = when (r.typeReseau) {
                "Électricité" -> Color(0xFFD32F2F)
                "Gaz" -> Color(0xFFF9A825)
                "Eau" -> Color(0xFF1976D2)
                else -> Color(0xFF388E3C)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${r.typeReseau} — Classe ${r.classePrecision} (${r.fiabilite})", color = color, fontWeight = FontWeight.Bold)
                    Text("Profondeur : ${r.profondeurConnueM ?: "?"} m | Source : ${r.source}")
                    Text("Marge vigilance : ${r.margeVigilanceM} m")
                    OutlinedButton(onClick = {
                        alerteActive = "ALERTE PROXIMITÉ : ${r.typeReseau} (Classe ${r.classePrecision}), prof ${r.profondeurConnueM ?: "?"} m, source ${r.source}."
                        if (settings.soundAlertsEnabled) {
                            try {
                                val tg = ToneGenerator(AudioManager.STREAM_ALARM, settings.soundVolumePercent.coerceIn(0, 100))
                                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                            } catch (_: Exception) {}
                        }
                    }) { Text("Tester alerte de proximité") }
                }
            }
        }
    }
}
