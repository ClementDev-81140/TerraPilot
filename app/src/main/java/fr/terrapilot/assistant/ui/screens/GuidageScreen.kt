package fr.terrapilot.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.ui.components.ResultCard
import fr.terrapilot.assistant.ui.components.UnitNumberField
import fr.terrapilot.assistant.ui.components.WarningBanner

@Composable
fun GuidageScreen() {
    var cibleProf by remember { mutableStateOf("0.80") }
    var hauteurCellule by remember { mutableStateOf("1.50") }
    var checkLaser by remember { mutableStateOf(false) }
    var checkFixation by remember { mutableStateOf(false) }
    var checkPelle by remember { mutableStateOf(false) }

    val prof = cibleProf.replace(',', '.').toDoubleOrNull() ?: 0.8
    val hCell = hauteurCellule.replace(',', '.').toDoubleOrNull() ?: 1.5

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            WarningBanner("Guidage simple Type A sur Kubota KX012 : le récepteur (ex. Geo Fennel FMR 600) détecte uniquement le plan laser. La tablette ne mesure pas la position réelle du godet et ne simule aucune connexion Bluetooth.")
        }
        item {
            ResultCard("Réglage de référence sur le bras KX012") {
                UnitNumberField(cibleProf, { cibleProf = it }, "Profondeur de fouille souhaitée", "m")
                Spacer(Modifier.height(6.dp))
                UnitNumberField(hauteurCellule, { hauteurCellule = it }, "Hauteur plan laser au-dessus du sol", "m")
                Spacer(Modifier.height(8.dp))
                Text("Hauteur à reporter sur le balancier : ${"%.2f".format(hCell + prof)} m", fontWeight = FontWeight.Bold)
                Text("1. Poser le godet au niveau fini souhaité.\n2. Fixer la cellule magnétique sur le balancier face au laser.\n3. Régler la hauteur de la cellule jusqu'au bip continu (niveau atteint).")
            }
        }
        item {
            ResultCard("Check-list sécurité avant terrassement") {
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = checkLaser, onCheckedChange = { checkLaser = it })
                    Text("Laser rotatif calé et stable hors zone de passage")
                }
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = checkFixation, onCheckedChange = { checkFixation = it })
                    Text("Fixation magnétique vérifiée sans modification du bras KX012")
                }
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = checkPelle, onCheckedChange = { checkPelle = it })
                    Text("Zone dégagée et réseaux enterrés vérifiés (DT-DICT)")
                }
            }
        }
    }
}
