package fr.terrapilot.assistant.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.data.db.ChantierEntity
import fr.terrapilot.assistant.data.db.TerraRepository
import fr.terrapilot.assistant.data.files.PdfReportGenerator
import fr.terrapilot.assistant.ui.components.BigActionButton
import fr.terrapilot.assistant.ui.components.ResultCard
import fr.terrapilot.assistant.ui.components.WarningBanner
import kotlinx.coroutines.launch

@Composable
fun RapportsScreen(activeChantier: ChantierEntity?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var simplified by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null && activeChantier != null) {
            scope.launch {
                val data = TerraRepository.getReportData(context, activeChantier.id)
                val ok = PdfReportGenerator.generate(context, uri, activeChantier, data.niveaux, data.pentes, data.volumes, data.reseaux, simplified)
                statusMsg = if (ok) "Rapport PDF généré avec succès." else "Erreur lors de la génération PDF."
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (activeChantier == null) {
            WarningBanner("Sélectionnez d'abord un chantier pour générer un rapport PDF.", isError = true)
            return@Column
        }
        ResultCard("Générateur de rapport PDF (hors ligne)") {
            Text("Chantier : ${activeChantier.nom}")
            Spacer(Modifier.height(8.dp))
            BigActionButton("Exporter Rapport Complet (PDF)", onClick = {
                simplified = false
                createPdf.launch("rapport_complet_${activeChantier.nom}.pdf")
            }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            BigActionButton("Exporter Rapport Simplifié Maçon/Client", onClick = {
                simplified = true
                createPdf.launch("rapport_simplifie_${activeChantier.nom}.pdf")
            }, modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondary)
        }
        if (statusMsg != null) {
            WarningBanner(statusMsg!!)
        }
    }
}
