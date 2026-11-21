package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.core.calcul.ImplantationCalculator
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.HistoriqueEntity
import fr.terrapilot.assistant.data.db.ImplantationEntity
import fr.terrapilot.assistant.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun ImplantationScreen(db: AppDatabase, chantierId: Long) {
    val scope = rememberCoroutineScope()
    val itemsList by db.implantationDao().observeByChantier(chantierId).collectAsState(initial = emptyList())

    var ouvrage by remember { mutableStateOf("Fondation garage") }
    var longueur by remember { mutableStateOf("4.0") }
    var largeur by remember { mutableStateOf("3.0") }
    var diag1 by remember { mutableStateOf("5.00") }
    var diag2 by remember { mutableStateOf("5.00") }

    val l = longueur.replace(',', '.').toDoubleOrNull() ?: 4.0
    val w = largeur.replace(',', '.').toDoubleOrNull() ?: 3.0
    val d1 = diag1.replace(',', '.').toDoubleOrNull() ?: 5.0
    val d2 = diag2.replace(',', '.').toDoubleOrNull() ?: 5.0
    val rect = ImplantationCalculator.rectangle(l, w)
    val sq = ImplantationCalculator.checkSquaring(l, w, d1, d2)

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(ouvrage, { ouvrage = it }, label = { Text("Ouvrage (maison, garage, terrasse, piscine...)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            UnitNumberField(longueur, { longueur = it }, "Longueur", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(largeur, { largeur = it }, "Largeur", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(diag1, { diag1 = it }, "Diagonale mesurée 1", "m")
            Spacer(Modifier.height(6.dp))
            UnitNumberField(diag2, { diag2 = it }, "Diagonale mesurée 2", "m")
        }
        item {
            ResultCard("Assistant d'équerrage (90°)") {
                Text("Diagonale théorique : ${"%.3f".format(rect.expectedDiagonalMeters)} m", fontWeight = FontWeight.Bold)
                Text("Surface : ${"%.2f".format(rect.areaM2)} m² | Périmètre : ${"%.2f".format(rect.perimeterM)} m")
                Text("Écart entre diagonales : ${"%.1f".format(sq.differenceMeters * 100)} cm")
                Text(
                    if (sq.isSquare) "ÉQUERRAGE CONFORME (≤ 1 cm)" else "ÉQUERRAGE À CORRIGER",
                    fontWeight = FontWeight.Bold,
                    color = if (sq.isSquare) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            BigActionButton("Enregistrer l'implantation", onClick = {
                if (chantierId > 0) {
                    scope.launch {
                        db.implantationDao().insert(
                            ImplantationEntity(
                                chantierId = chantierId,
                                ouvrage = ouvrage,
                                longueurM = l,
                                largeurM = w,
                                diagonaleTheoriqueM = rect.expectedDiagonalMeters,
                                diagonale1M = d1,
                                diagonale2M = d2,
                                equerrageOk = sq.isSquare
                            )
                        )
                        db.historiqueDao().insert(
                            HistoriqueEntity(chantierId = chantierId, module = "Implantation", resume = "$ouvrage (${l}×${w} m) : diag ${"%.2f".format(rect.expectedDiagonalMeters)} m")
                        )
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        items(itemsList) { imp ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(imp.ouvrage, fontWeight = FontWeight.Bold)
                    Text("${imp.longueurM}×${imp.largeurM} m | Diag théo : ${"%.3f".format(imp.diagonaleTheoriqueM)} m | OK=${imp.equerrageOk}")
                }
            }
        }
    }
}
