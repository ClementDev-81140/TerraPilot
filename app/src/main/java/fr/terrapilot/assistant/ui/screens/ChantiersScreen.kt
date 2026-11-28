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
import fr.terrapilot.assistant.data.db.ChantierEntity
import fr.terrapilot.assistant.data.db.TerraRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ChantiersScreen(
    activeChantierId: Long,
    onSelectChantier: (Long) -> Unit,
    onCreatedChantier: (Long) -> Unit = onSelectChantier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val chantiers by (if (query.isBlank()) TerraRepository.observeChantiers(context) else TerraRepository.searchChantiers(context, query))
        .collectAsState(initial = emptyList())

    var nom by remember { mutableStateOf("") }
    var adresse by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<ChantierEntity?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var creationError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("MES CHANTIERS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Organisez vos interventions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Chaque chantier regroupe sa carte, ses relevés, ses plans et ses sauvegardes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher un chantier") },
                placeholder = { Text("Nom ou adresse") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nouveau chantier", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Le nom est obligatoire. L'adresse peut être complétée plus tard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = nom,
                        onValueChange = { nom = it; nameError = false; creationError = null },
                        label = { Text("Nom du chantier *") },
                        placeholder = { Text("Ex. Terrassement maison Martin") },
                        singleLine = true,
                        isError = nameError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (nameError) {
                        Text("Saisis un nom pour créer le chantier.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(adresse, { adresse = it }, label = { Text("Adresse / commune") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(client, { client = it }, label = { Text("Client / maçon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(observations, { observations = it }, label = { Text("Notes (facultatif)") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    if (creationError != null) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                            Text(creationError!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Button(
                        onClick = {
                            if (isCreating) return@Button
                            if (nom.isBlank()) {
                                nameError = true
                                creationError = null
                                return@Button
                            }
                            scope.launch {
                                isCreating = true
                                creationError = null
                                try {
                                    val id = TerraRepository.insertChantier(
                                        context,
                                        ChantierEntity(
                                            nom = nom.trim(),
                                            adresse = adresse.trim(),
                                            client = client.trim(),
                                            observations = observations.trim()
                                        )
                                    )
                                    check(id > 0L) { "La base n'a pas confirmé la création du chantier." }
                                    onCreatedChantier(id)
                                    nom = ""
                                    adresse = ""
                                    client = ""
                                    observations = ""
                                    nameError = false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    creationError = "Création impossible : ${e.message ?: "erreur inconnue"}"
                                } finally {
                                    isCreating = false
                                }
                            }
                        },
                        enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(10.dp))
                            Text("Création en cours…")
                        } else {
                            Text("Créer le chantier et ouvrir sa carte", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("Chantiers enregistrés · ${chantiers.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (chantiers.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (query.isBlank()) "Aucun chantier pour le moment" else "Aucun résultat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (query.isBlank()) "Crée ton premier chantier ci-dessus. Il sera enregistré sur la tablette." else "Essaie un autre nom ou une autre adresse.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(chantiers, key = { it.id }) { c ->
            val isSelected = c.id == activeChantierId
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else CardDefaults.cardColors()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Text("ACTIF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    val details = listOf(c.adresse, c.client).filter { it.isNotBlank() }.joinToString(" · ")
                    if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSelectChantier(c.id) }, modifier = Modifier.weight(1f)) {
                            Text(if (isSelected) "Afficher le chantier" else "Ouvrir")
                        }
                        OutlinedButton(onClick = { toDelete = c }) { Text("Supprimer") }
                    }
                }
            }
        }
    }

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Confirmer la suppression") },
            text = { Text("Supprimer définitivement le chantier « ${toDelete?.nom} » et ses points/zones cartographiques ?") },
            confirmButton = {
                Button(onClick = {
                    val target = toDelete
                    toDelete = null
                    if (target != null) scope.launch {
                        try {
                            TerraRepository.deleteChantier(context, target)
                        } catch (e: Exception) {
                            creationError = "Suppression impossible : ${e.message ?: "erreur inconnue"}"
                        }
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = { OutlinedButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }
}
