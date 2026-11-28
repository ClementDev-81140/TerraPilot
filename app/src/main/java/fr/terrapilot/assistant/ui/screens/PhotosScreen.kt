package fr.terrapilot.assistant.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import fr.terrapilot.assistant.data.db.PhotoEntity
import fr.terrapilot.assistant.data.db.TerraRepository
import fr.terrapilot.assistant.ui.components.BigActionButton
import kotlinx.coroutines.launch

@Composable
fun PhotosScreen(chantierId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by TerraRepository.observePhotos(context, chantierId).collectAsState(initial = emptyList())

    var phase by remember { mutableStateOf("Pendant") }
    var commentaire by remember { mutableStateOf("") }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && chantierId > 0) {
            scope.launch {
                TerraRepository.insertPhoto(
                    context,
                    PhotoEntity(
                        chantierId = chantierId,
                        uriString = uri.toString(),
                        phase = phase,
                        commentaire = commentaire
                    )
                )
                commentaire = ""
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Avant", "Pendant", "Après").forEach { p ->
                    FilterChip(selected = phase == p, onClick = { phase = p }, label = { Text(p) })
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(commentaire, { commentaire = it }, label = { Text("Commentaire photo") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            BigActionButton("Importer une photo dans le chantier", onClick = {
                pickPhoto.launch(arrayOf("image/*"))
            }, modifier = Modifier.fillMaxWidth())
        }
        items(photos) { ph ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Phase : ${ph.phase}", fontWeight = FontWeight.Bold)
                    if (ph.commentaire.isNotBlank()) Text(ph.commentaire)
                    Text(ph.uriString, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
