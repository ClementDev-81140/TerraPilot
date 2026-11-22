package fr.terrapilot.assistant.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.data.db.AppDatabase
import fr.terrapilot.assistant.data.db.PlanEntity
import fr.terrapilot.assistant.ui.components.BigActionButton
import fr.terrapilot.assistant.ui.components.WarningBanner
import kotlinx.coroutines.launch

@Composable
fun PlansScreen(db: AppDatabase, chantierId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plans by db.planDao().observeByChantier(chantierId).collectAsState(initial = emptyList())

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var zoom by remember { mutableStateOf(1f) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && chantierId > 0) {
            val mime = context.contentResolver.getType(uri) ?: "image/*"
            val isPdf = mime.contains("pdf")
            if (isPdf) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    val page = renderer.openPage(0)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    selectedBitmap = bmp
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use {
                    selectedBitmap = BitmapFactory.decodeStream(it)
                }
            }
            scope.launch {
                db.planDao().insert(
                    PlanEntity(
                        chantierId = chantierId,
                        nom = uri.lastPathSegment ?: "Plan",
                        uriString = uri.toString(),
                        typeFichier = if (isPdf) "PDF" else "IMAGE"
                    )
                )
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            WarningBanner("Les photos prises en perspective peuvent fausser les mesures tant qu'elles ne sont pas rectifiées. Les annotations sont enregistrées séparément du document original.")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigActionButton("Importer PDF / Image", onClick = {
                    launcher.launch(arrayOf("application/pdf", "image/*"))
                }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { zoom = if (zoom >= 2f) 1f else zoom + 0.5f }) {
                    Text("Zoom ×${"%.1f".format(zoom)}")
                }
            }
        }

        if (selectedBitmap != null) {
            item {
                val img = selectedBitmap!!.asImageBitmap()
                Box(Modifier.fillMaxWidth().height(340.dp)) {
                    Canvas(
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ -> currentStroke = currentStroke + change.position },
                                onDragEnd = {
                                    strokes = strokes + listOf(currentStroke)
                                    currentStroke = emptyList()
                                }
                            )
                        }
                    ) {
                        drawImage(
                            image = img,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize((size.width * zoom).toInt(), (size.height * zoom).toInt())
                        )
                        (strokes + listOf(currentStroke)).forEach { line ->
                            for (i in 0 until line.size - 1) {
                                drawLine(Color.Red, line[i], line[i + 1], strokeWidth = 5f)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { strokes = emptyList() }) { Text("Effacer annotations") }
                }
            }
        }

        items(plans) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.nom} (${p.typeFichier})", fontWeight = FontWeight.Bold)
                    Text(p.uriString, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
