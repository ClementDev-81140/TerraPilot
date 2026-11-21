package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.ChantierEntity
import fr.terrapilot.assistant.ui.components.BigActionButton
import kotlinx.coroutines.launch

@Composable
fun ChantiersScreen(
    db: AppDatabase,
    activeChantierId: Long,
    onSelectChantier: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val chantiers by (if (query.isBlank()) db.chantierDao().observeAll() else db.chantierDao().search(query))
        .collectAsState(initial = emptyList())

    var nom by remember { mutableStateOf("") }
    var adresse by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<ChantierEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher un chantier (nom ou adresse)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nouveau chantier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(nom, { nom = it }, label = { Text("Nom du chantier") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(adresse, { adresse = it }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(client, { client = it }, label = { Text("Client / Maçon") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(observations, { observations = it }, label = { Text("Observations") }, modifier = Modifier.fillMaxWidth())
                    BigActionButton("Créer et ouvrir", onClick = {
                        if (nom.isNotBlank()) {
                            scope.launch {
                                val id = db.chantierDao().insert(
                                    ChantierEntity(
                                        nom = nom.trim(),
                                        adresse = adresse.trim(),
                                        client = client.trim(),
                                        observations = observations.trim()
                                    )
                                )
                                onSelectChantier(id)
                                nom = ""; adresse = ""; client = ""; observations = ""
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        items(chantiers) { c ->
            val isSelected = c.id == activeChantierId
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else CardDefaults.cardColors()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${c.adresse} — ${c.client}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSelectChantier(c.id) }) {
                            Text(if (isSelected) "Ouvert" else "Ouvrir")
                        }
                        OutlinedButton(onClick = { toDelete = c }) {
                            Text("Supprimer")
                        }
                    }
                }
            }
        }
    }

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Confirmer la suppression") },
            text = { Text("Supprimer définitivement le chantier « ${toDelete?.nom} » ?") },
            confirmButton = {
                Button(onClick = {
                    val target = toDelete
                    toDelete = null
                    if (target != null) scope.launch { db.chantierDao().delete(target) }
                }) { Text("Supprimer") }
            },
            dismissButton = {
                OutlinedButton(onClick = { toDelete = null }) { Text("Annuler") }
            }
        )
    }
}
