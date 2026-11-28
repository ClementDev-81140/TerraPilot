package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import fr.terrapilot.assistant.core.calcul.TrenchSection
import fr.terrapilot.assistant.core.calcul.VolumeCalculator
import fr.terrapilot.assistant.data.db.HistoriqueEntity
import fr.terrapilot.assistant.data.db.TerraRepository
import fr.terrapilot.assistant.data.db.VolumeEntity
import fr.terrapilot.assistant.domain.AppSettings
import fr.terrapilot.assistant.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun VolumesScreen(chantierId: Long, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val volumes by TerraRepository.observeVolumes(context, chantierId).collectAsState(initial = emptyList())

    var type by remember { mutableStateOf("Tranchée") }
    var longueur by remember { mutableStateOf("10.0") }
    var largeur by remember { mutableStateOf("0.40") }
    var profondeur by remember { mutableStateOf("0.80") }
    var coeff by remember { mutableStateOf(settings.defaultSwellFactor.toString()) }

    val l = longueur.replace(',', '.').toDoubleOrNull() ?: 0.0
    val w = largeur.replace(',', '.').toDoubleOrNull() ?: 0.0
    val p = profondeur.replace(',', '.').toDoubleOrNull() ?: 0.0
    val c = coeff.replace(',', '.').toDoubleOrNull() ?: 1.25

    val summary = if (type == "Plateforme") {
        VolumeCalculator.platform(l, w, p, c)
    } else {
        VolumeCalculator.trench(listOf(TrenchSection(l, w, p)), c)
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tranchée", "Plateforme", "Fondation").forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                }
            }
        }
        item {
            UnitNumberField(longueur, { longueur = it }, "Longueur", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(largeur, { largeur = it }, "Largeur", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(profondeur, { profondeur = it }, if (type == "Plateforme") "Épaisseur à décaisser" else "Profondeur", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(coeff, { coeff = it }, "Coefficient de foisonnement (estimation)", "coeff")
        }
        item {
            ResultCard("Volume calculé ($type)") {
                Text("Surface : ${"%.2f".format(summary.surfaceM2)} m²")
                Text("Volume net en place : ${"%.2f".format(summary.netVolumeM3)} m³", fontWeight = FontWeight.Bold)
                Text("Estimation foisonnée (×${"%.2f".format(summary.swellFactor)}) : ${"%.2f".format(summary.swelledVolumeM3)} m³")
            }
        }
        item {
            BigActionButton("Enregistrer le volume", onClick = {
                if (chantierId > 0) {
                    scope.launch {
                        TerraRepository.insertVolume(
                            context,
                            VolumeEntity(
                                chantierId = chantierId,
                                typeOuvrage = type,
                                description = "${l}×${w}×${p} m",
                                volumeNetM3 = summary.netVolumeM3,
                                volumeFoisonneM3 = summary.swelledVolumeM3,
                                coeffFoisonnement = summary.swellFactor
                            )
                        )
                        TerraRepository.insertHistorique(
                            context,
                            HistoriqueEntity(chantierId = chantierId, module = "Terrassement", resume = "$type : ${"%.2f".format(summary.netVolumeM3)} m³")
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        items(volumes) { v ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${v.typeOuvrage} (${v.description})", fontWeight = FontWeight.Bold)
                    Text("${"%.2f".format(v.volumeNetM3)} m³ net | ${"%.2f".format(v.volumeFoisonneM3)} m³ foisonné")
                }
            }
        }
    }
}
