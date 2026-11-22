package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.domain.AppSettings
import fr.terrapilot.assistant.domain.SettingsRepository
import fr.terrapilot.assistant.ui.components.ResultCard
import kotlinx.coroutines.launch

@Composable
fun ParametresScreen(settings: AppSettings, repo: SettingsRepository) {
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResultCard("Affichage & Chantier") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Thème sombre")
                Switch(checked = settings.darkTheme, onCheckedChange = { scope.launch { repo.setDarkTheme(it) } })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Affichage nivellement au 0,5 cm")
                Switch(checked = settings.displayHalfCm, onCheckedChange = { scope.launch { repo.setHalfCm(it) } })
            }
            Text("Orientation de l'écran")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AUTO", "LANDSCAPE", "PORTRAIT").forEach { mode ->
                    FilterChip(
                        selected = settings.orientationMode == mode,
                        onClick = { scope.launch { repo.setOrientation(mode) } },
                        label = { Text(mode) }
                    )
                }
            }
        }

        ResultCard("Alertes sonores") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Activer les alertes sonores")
                Switch(checked = settings.soundAlertsEnabled, onCheckedChange = { scope.launch { repo.setSoundAlerts(it) } })
            }
            Text("Volume : ${settings.soundVolumePercent} %")
            Slider(
                value = settings.soundVolumePercent.toFloat(),
                onValueChange = { scope.launch { repo.setSoundVolume(it.toInt()) } },
                valueRange = 0f..100f
            )
        }
    }
}
