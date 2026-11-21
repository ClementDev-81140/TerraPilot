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
import fr.terrapilot.assistant.ui.components.ResultCard
import fr.terrapilot.assistant.ui.components.WarningBanner

@Composable
fun HomeScreen(
    db: AppDatabase,
    activeChantier: ChantierEntity?,
    onNavigate: (String) -> Unit
) {
    val cid = activeChantier?.id ?: -1L
    val niveaux by db.niveauDao().observeByChantier(cid).collectAsState(initial = emptyList())
    val historique by db.historiqueDao().observeByChantier(cid).collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (activeChantier == null) {
                WarningBanner("Aucun chantier sélectionné. Ouvrez ou créez un chantier dans « Mes chantiers ».")
                Spacer(Modifier.height(8.dp))
                BigActionButton("Ouvrir Mes chantiers", onClick = { onNavigate("chantiers") }, modifier = Modifier.fillMaxWidth())
            } else {
                ResultCard("Chantier actif : ${activeChantier.nom}") {
                    Text("Adresse : ${activeChantier.adresse}")
                    Text("Client / Maçon : ${activeChantier.client}")
                }
            }
        }

        item {
            Text("Raccourcis terrain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigActionButton("Niveaux", onClick = { onNavigate("niveaux") }, modifier = Modifier.weight(1f))
                BigActionButton("Pentes", onClick = { onNavigate("pentes") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigActionButton("Volumes", onClick = { onNavigate("volumes") }, modifier = Modifier.weight(1f))
                BigActionButton("Réseaux", onClick = { onNavigate("reseaux") }, modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.error)
            }
        }

        if (niveaux.isNotEmpty()) {
            item {
                val last = niveaux.first()
                ResultCard("Dernier nivellement (${last.nomPoint})") {
                    Text("Altitude : ${"%.3f".format(last.altitudePoint)} m | Cible : ${"%.3f".format(last.cibleAlt)} m")
                    Text("Statut : ${last.statut}", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Text("Dernières opérations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(historique.take(8)) { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(h.module, fontWeight = FontWeight.Bold)
                    Text(h.resume)
                }
            }
        }
    }
}
