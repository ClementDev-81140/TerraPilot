package fr.terrapilot.assistant.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.data.geo.*
import fr.terrapilot.assistant.ui.components.BigActionButton
import fr.terrapilot.assistant.ui.components.ResultCard
import fr.terrapilot.assistant.ui.components.WarningBanner
import kotlinx.coroutines.launch

@Composable
fun CarteScreen(chantierId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { TileCache(context) }

    var selectedLayer by remember { mutableStateOf(IgnGeoplateforme.layers.first()) }
    var latText by remember { mutableStateOf("43.968") }
    var lonText by remember { mutableStateOf("1.460") }
    var zoom by remember { mutableStateOf(15) }
    var tileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var gpxSummary by remember { mutableStateOf("Aucun fichier GPX importé.") }
    var cacheBytes by remember { mutableStateOf(cache.cacheSizeBytes()) }

    val importGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val data = GpxIO.parse(inp)
                gpxSummary = "GPX importé : ${data.waypoints.size} repères, ${data.tracks.size} traces."
            }
        }
    }

    val exportGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        if (uri != null) {
            val lat = latText.toDoubleOrNull() ?: 43.968
            val lon = lonText.toDoubleOrNull() ?: 1.460
            context.contentResolver.openOutputStream(uri)?.use { out ->
                GpxIO.write(out, GpxData(listOf(GpxWaypoint("Chantier #$chantierId", lat, lon, null)), emptyList()))
                gpxSummary = "GPX exporté (compatible iPhiGéNie)."
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            WarningBanner("Source : IGN Géoplateforme (WMTS officiel). Téléchargez la zone avant de partir en zone sans réseau.")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IgnGeoplateforme.layers.forEach { layer ->
                    FilterChip(selected = selectedLayer == layer, onClick = { selectedLayer = layer }, label = { Text(layer.label) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latText, { latText = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lonText, { lonText = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            BigActionButton("Charger / Mettre en cache la tuile IGN", onClick = {
                scope.launch {
                    val lat = latText.toDoubleOrNull() ?: 43.968
                    val lon = lonText.toDoubleOrNull() ?: 1.460
                    val (tx, ty) = IgnGeoplateforme.latLonToTile(lat, lon, zoom)
                    val bytes = cache.getOrDownloadTile(selectedLayer, zoom, tx, ty)
                    if (bytes != null) {
                        tileBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    cacheBytes = cache.cacheSizeBytes()
                }
            }, modifier = Modifier.fillMaxWidth())
        }

        if (tileBitmap != null) {
            item {
                Image(
                    bitmap = tileBitmap!!.asImageBitmap(),
                    contentDescription = "Tuile IGN",
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            }
        }

        item {
            ResultCard("Échanges iPhiGéNie (GPX) & Cache hors ligne") {
                Text(gpxSummary)
                Text("Espace cache IGN : ${cacheBytes / 1024} Ko", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importGpx.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Importer GPX") }
                    Button(onClick = { exportGpx.launch("chantier_$chantierId.gpx") }, modifier = Modifier.weight(1f)) { Text("Exporter GPX") }
                }
                OutlinedButton(onClick = {
                    cache.clearCache()
                    cacheBytes = cache.cacheSizeBytes()
                }) { Text("Vider le cache IGN") }
            }
        }
    }
}
