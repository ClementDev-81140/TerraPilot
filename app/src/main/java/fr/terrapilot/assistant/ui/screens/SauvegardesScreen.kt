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
import fr.terrapilot.assistant.data.files.BackupManager
import fr.terrapilot.assistant.ui.components.BigActionButton
import fr.terrapilot.assistant.ui.components.ResultCard
import fr.terrapilot.assistant.ui.components.WarningBanner
import kotlinx.coroutines.launch

@Composable
fun SauvegardesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = BackupManager.exportDatabaseToZip(context, uri)
                status = if (res.success) "${res.message}\nSHA-256 : ${res.sha256}" else res.message
            }
        }
    }

    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = BackupManager.restoreDatabaseFromZip(context, uri)
                status = res.message
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResultCard("Sauvegarde locale / Clé USB OTG / Carte SD") {
            Text("Exporte l'intégralité des chantiers, mesures, niveaux, réseaux et historiques dans une archive ZIP avec empreinte SHA-256.")
            Spacer(Modifier.height(8.dp))
            BigActionButton("Exporter sauvegarde complète (ZIP)", onClick = {
                exportZip.launch("terrapilot_backup.zip")
            }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                importZip.launch(arrayOf("application/zip"))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurer depuis une archive ZIP")
            }
        }
        ResultCard("Sauvegarde cloud économique") {
            Text("Vous pouvez enregistrer l'archive ZIP directement dans Google Drive, Nextcloud ou OneDrive via le sélecteur de fichiers Android, sans abonnement supplémentaire.")
        }
        if (status != null) {
            WarningBanner(status!!)
        }
    }
}
