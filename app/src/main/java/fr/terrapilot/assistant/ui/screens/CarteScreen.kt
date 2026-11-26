package fr.terrapilot.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import fr.terrapilot.assistant.data.db.ChantierEntity
import fr.terrapilot.assistant.data.db.MapFeatureEntity
import fr.terrapilot.assistant.data.db.OfflineAreaEntity
import fr.terrapilot.assistant.data.db.TerraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

private const val IGN_PLAN_STYLE = "asset://ign_plan_style.json"
private const val IGN_ORTHO_STYLE = "asset://ign_ortho_style.json"
private const val FEATURES_SOURCE = "terrapilot-features"
private const val ZONE_FILL = "terrapilot-zone-fill"
private const val ZONE_LINE = "terrapilot-zone-line"
private const val POINTS_LAYER = "terrapilot-points"
private const val LABELS_LAYER = "terrapilot-labels"

private enum class MapTool { MOVE, POINT, ZONE, LEVEL }

@Composable
fun CarteScreen(chantier: ChantierEntity?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val chantierId = chantier?.id ?: -1L
    val features by TerraRepository.observeMapFeatures(context, chantierId).collectAsState(initial = emptyList())
    val offlineAreas by TerraRepository.observeOfflineAreas(context, chantierId).collectAsState(initial = emptyList())

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var selectedBase by remember { mutableStateOf("PLAN") }
    var selectedTool by remember { mutableStateOf(MapTool.MOVE) }
    var draftVertices by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var mapMessage by remember { mutableStateOf<String?>(null) }
    var mapCenterText by remember { mutableStateOf("Carte IGN") }

    var pendingPoint by remember { mutableStateOf<LatLng?>(null) }
    var pendingKind by remember { mutableStateOf("POINT") }
    var featureName by remember { mutableStateOf("") }
    var targetAltitude by remember { mutableStateOf("") }
    var zoneName by remember { mutableStateOf("Zone de travail") }
    var offlineName by remember { mutableStateOf("Zone chantier") }
    var addressQuery by remember(chantier?.id) { mutableStateOf(chantier?.adresse.orEmpty()) }
    var isSearching by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var showZoneDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }

    val latestTool = rememberUpdatedState(selectedTool)
    val mapView = remember(context) { MapView(context).also { it.onCreate(null) } }
    val locationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allowed =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (allowed) {
            moveToCurrentLocation(locationClient, mapLibreMap) { mapMessage = it }
        } else {
            mapMessage = "Localisation refusée. La carte reste utilisable par déplacement ou recherche d'adresse."
        }
    }

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
            mapLibreMap = null
            mapReady = false
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(mapReady, selectedBase) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        val styleUri = if (selectedBase == "ORTHO") IGN_ORTHO_STYLE else IGN_PLAN_STYLE
        map.setStyle(
            Style.Builder().fromUri(styleUri),
            Style.OnStyleLoaded { style ->
                installFeatureLayers(style, makeFeatureCollection(features, draftVertices))
            }
        )
    }

    LaunchedEffect(features, draftVertices, mapReady, selectedBase) {
        if (!mapReady) return@LaunchedEffect
        mapLibreMap?.getStyle { style ->
            (style.getSource(FEATURES_SOURCE) as? GeoJsonSource)
                ?.setGeoJson(makeFeatureCollection(features, draftVertices))
        }
    }

    LaunchedEffect(mapReady, chantier?.id) {
        if (!mapReady || chantier == null) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val savedLat = chantier.originLat
        val savedLon = chantier.originLon
        if (savedLat != null && savedLon != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(savedLat, savedLon), 16.0), 700)
        } else if (chantier.adresse.isNotBlank()) {
            try {
                val match = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.FRANCE).getFromLocationName(chantier.adresse, 1)?.firstOrNull()
                }
                if (match != null) {
                    val point = LatLng(match.latitude, match.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0), 700)
                    TerraRepository.updateChantierLocation(context, chantier.id, point.latitude, point.longitude)
                    mapMessage = "Carte centrée sur l'adresse du chantier."
                } else {
                    mapMessage = "Adresse non localisée automatiquement. Tu peux la rechercher dans la barre en haut."
                }
            } catch (_: Exception) {
                mapMessage = "Adresse non localisée automatiquement. Vérifie la commune dans la recherche."
            }
        } else {
            map.cameraPosition = CameraPosition.Builder().target(LatLng(46.5, 2.4)).zoom(5.5).build()
            mapMessage = "Recherche une adresse ou utilise « Ma position » pour centrer la carte."
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = false
            map.setMinZoomPreference(5.0)
            map.setMaxZoomPreference(20.0)
            map.setTileCacheEnabled(true)
            map.addOnCameraIdleListener {
                val center = map.cameraPosition.target
                if (center != null) {
                    mapCenterText = String.format(
                        Locale.FRANCE,
                        "%.5f · %.5f · Z%.1f",
                        center.latitude,
                        center.longitude,
                        map.cameraPosition.zoom
                    )
                }
            }
            map.addOnMapClickListener { point ->
                when (latestTool.value) {
                    MapTool.MOVE -> false
                    MapTool.POINT -> {
                        pendingKind = "POINT"
                        featureName = "Point " + (features.count { it.kind == "POINT" } + 1)
                        targetAltitude = ""
                        pendingPoint = point
                        true
                    }
                    MapTool.LEVEL -> {
                        pendingKind = "LEVEL"
                        featureName = "Cote " + (features.count { it.kind == "LEVEL" } + 1)
                        targetAltitude = ""
                        pendingPoint = point
                        true
                    }
                    MapTool.ZONE -> {
                        draftVertices = draftVertices + point
                        true
                    }
                }
            }
            mapReady = true
        }
    }

    fun searchAddress() {
        val query = addressQuery.trim()
        if (query.isBlank()) {
            mapMessage = "Saisis une adresse ou une commune."
            return
        }
        scope.launch {
            isSearching = true
            try {
                val result = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.FRANCE).getFromLocationName(query, 1)?.firstOrNull()
                }
                if (result == null) {
                    mapMessage = "Adresse introuvable. Essaie avec le nom de la commune ou le code postal."
                } else {
                    val point = LatLng(result.latitude, result.longitude)
                    mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0), 650)
                    if (chantier != null) {
                        TerraRepository.updateChantierLocation(context, chantier.id, point.latitude, point.longitude)
                    }
                    mapMessage = "Carte centrée sur " + (result.featureName ?: query) + "."
                }
            } catch (error: Exception) {
                mapMessage = "Recherche indisponible : " + (error.localizedMessage ?: "service de géocodage non accessible")
            } finally {
                isSearching = false
            }
        }
    }

    fun savePointOrLevel() {
        val point = pendingPoint ?: return
        val altitude = if (pendingKind == "LEVEL") targetAltitude.replace(',', '.').toDoubleOrNull() else null
        if (pendingKind == "LEVEL" && altitude == null) {
            mapMessage = "Saisis une altitude numérique en mètres."
            return
        }
        val geometry = JSONObject()
            .put("type", "Point")
            .put("coordinates", JSONArray().put(point.longitude).put(point.latitude))
            .toString()
        scope.launch {
            try {
                TerraRepository.insertMapFeature(
                    context,
                    MapFeatureEntity(
                        chantierId = chantierId,
                        kind = pendingKind,
                        name = featureName.trim().ifBlank { if (pendingKind == "LEVEL") "Cote projetée" else "Point" },
                        latitude = point.latitude,
                        longitude = point.longitude,
                        targetAltitudeM = altitude,
                        geometryJson = geometry,
                        colorHex = if (pendingKind == "LEVEL") "#1976D2" else "#E56F21"
                    )
                )
                mapMessage = if (pendingKind == "LEVEL") "Cote projetée enregistrée." else "Point enregistré."
                pendingPoint = null
                selectedTool = MapTool.MOVE
            } catch (error: Exception) {
                mapMessage = "Enregistrement impossible : " + (error.localizedMessage ?: "erreur Room")
            }
        }
    }

    fun saveZone() {
        if (draftVertices.size < 3) {
            mapMessage = "Une zone doit avoir au moins trois sommets."
            return
        }
        val ring = JSONArray()
        draftVertices.forEach { ring.put(JSONArray().put(it.longitude).put(it.latitude)) }
        val first = draftVertices.first()
        ring.put(JSONArray().put(first.longitude).put(first.latitude))
        val geometry = JSONObject()
            .put("type", "Polygon")
            .put("coordinates", JSONArray().put(ring))
            .toString()
        scope.launch {
            try {
                TerraRepository.insertMapFeature(
                    context,
                    MapFeatureEntity(
                        chantierId = chantierId,
                        kind = "ZONE",
                        name = zoneName.trim().ifBlank { "Zone de travail" },
                        latitude = draftVertices.map { it.latitude }.average(),
                        longitude = draftVertices.map { it.longitude }.average(),
                        geometryJson = geometry,
                        colorHex = "#E56F21"
                    )
                )
                mapMessage = "Zone enregistrée (" + draftVertices.size + " sommets)."
                draftVertices = emptyList()
                selectedTool = MapTool.MOVE
                showZoneDialog = false
            } catch (error: Exception) {
                mapMessage = "Enregistrement de la zone impossible : " + (error.localizedMessage ?: "erreur Room")
            }
        }
    }

    fun downloadVisibleArea() {
        val map = mapLibreMap
        if (map == null || !mapReady) {
            mapMessage = "La carte n'est pas encore prête."
            return
        }
        if (chantierId <= 0L) {
            mapMessage = "Crée un chantier avant de télécharger une zone hors ligne."
            return
        }
        val bounds = map.projection.visibleRegion.latLngBounds
        val estimatedTiles = estimateVisibleTiles(bounds, 13, 17)
        if (estimatedTiles > 4500L) {
            mapMessage = "Zone trop grande. Zoome davantage sur le chantier puis réessaie."
            return
        }
        val styleUri = if (selectedBase == "ORTHO") IGN_ORTHO_STYLE else IGN_PLAN_STYLE
        val definition = OfflineTilePyramidRegionDefinition(styleUri, bounds, 13.0, 17.0, 1.0f)
        val label = offlineName.trim().ifBlank { "Zone chantier" }
        val metadata = JSONObject()
            .put("name", label)
            .put("chantierId", chantierId)
            .put("base", selectedBase)
            .toString()
            .toByteArray(Charsets.UTF_8)

        isDownloading = true
        mapMessage = "Préparation de la zone visible · environ " + estimatedTiles + " tuiles aux zooms 13–17."
        try {
            OfflineManager.getInstance(context).createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val record = OfflineAreaEntity(
                                    mapLibreRegionId = region.id,
                                    chantierId = chantierId,
                                    name = label + " · " + if (selectedBase == "ORTHO") "Photo" else "Plan IGN",
                                    west = bounds.longitudeWest,
                                    south = bounds.latitudeSouth,
                                    east = bounds.longitudeEast,
                                    north = bounds.latitudeNorth,
                                    minZoom = 13.0,
                                    maxZoom = 17.0,
                                    completedResources = status.completedResourceCount,
                                    requiredResources = status.requiredResourceCount,
                                    downloadedBytes = status.completedResourceSize,
                                    isComplete = status.isComplete
                                )
                                scope.launch {
                                    try {
                                        TerraRepository.upsertOfflineArea(context, record)
                                    } catch (error: Exception) {
                                        mapMessage = "Téléchargement en cours, statut local non enregistré : " + (error.localizedMessage ?: "")
                                    }
                                }
                                if (status.isComplete) {
                                    isDownloading = false
                                    mapMessage = "Zone hors ligne prête · " + status.completedTileCount + " tuiles, " +
                                        (status.completedResourceSize / (1024 * 1024)) + " Mo."
                                } else {
                                    val countText = if (status.isRequiredResourceCountPrecise) {
                                        status.completedResourceCount.toString() + "/" + status.requiredResourceCount
                                    } else {
                                        status.completedResourceCount.toString() + " ressources reçues"
                                    }
                                    mapMessage = "Téléchargement hors ligne : " + countText + "."
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                isDownloading = false
                                mapMessage = "Erreur de téléchargement : " + error.toString()
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                isDownloading = false
                                mapMessage = "Limite de tuiles MapLibre atteinte (" + limit + "). Réduis la zone."
                            }
                        })
                        try {
                            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                            mapMessage = "Téléchargement lancé. Garde la tablette connectée jusqu'à la fin."
                        } catch (error: Exception) {
                            isDownloading = false
                            mapMessage = "Activation du téléchargement impossible : " + (error.localizedMessage ?: "")
                        }
                    }

                    override fun onError(error: String) {
                        isDownloading = false
                        mapMessage = "Préparation de la zone impossible : " + error
                    }
                }
            )
        } catch (error: Exception) {
            isDownloading = false
            mapMessage = "Téléchargement impossible : " + (error.localizedMessage ?: "erreur inconnue")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                )
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("CARTE DU CHANTIER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(chantier?.nom ?: "Aucun chantier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(if (selectedBase == "ORTHO") "ORTHOPHOTO" else "PLAN IGN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        OutlinedTextField(
                            value = addressQuery,
                            onValueChange = { addressQuery = it },
                            singleLine = true,
                            placeholder = { Text("Adresse ou commune") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { searchAddress() }, enabled = !isSearching) {
                            Text(if (isSearching) "…" else "Chercher")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(selected = selectedBase == "PLAN", onClick = { selectedBase = "PLAN" }, label = { Text("Plan") })
                            FilterChip(selected = selectedBase == "ORTHO", onClick = { selectedBase = "ORTHO" }, label = { Text("Photo") })
                        }
                        OutlinedButton(onClick = {
                            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) {
                                moveToCurrentLocation(locationClient, mapLibreMap) { mapMessage = it }
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                        }) {
                            Text("Ma position")
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 2.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(features.size.toString() + " objets · " + offlineAreas.size + " zones hors ligne", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(mapCenterText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            )
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("OUTILS DE PLAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            when (selectedTool) {
                                MapTool.MOVE -> "Déplace la carte ou sélectionne un outil"
                                MapTool.POINT -> "Appuie sur la carte pour poser un point"
                                MapTool.ZONE -> "Ajoute les sommets de ta zone sur la carte"
                                MapTool.LEVEL -> "Appuie pour placer une cote projetée"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(features.size.toString() + " éléments", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = selectedTool == MapTool.MOVE, onClick = { selectedTool = MapTool.MOVE; draftVertices = emptyList() }, label = { Text("Déplacer") })
                    FilterChip(selected = selectedTool == MapTool.POINT, onClick = { selectedTool = MapTool.POINT }, label = { Text("+ Point") })
                    FilterChip(selected = selectedTool == MapTool.ZONE, onClick = { selectedTool = MapTool.ZONE; draftVertices = emptyList() }, label = { Text("Zone") })
                    FilterChip(selected = selectedTool == MapTool.LEVEL, onClick = { selectedTool = MapTool.LEVEL }, label = { Text("+ Cote") })
                }
                if (selectedTool == MapTool.ZONE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = { draftVertices = draftVertices.dropLast(1) },
                            enabled = draftVertices.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Annuler sommet") }
                        Button(
                            onClick = {
                                if (draftVertices.size >= 3) showZoneDialog = true
                                else mapMessage = "Ajoute au moins trois sommets."
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Terminer la zone") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(
                        onClick = { showOfflineDialog = true },
                        enabled = mapReady && !isDownloading && chantierId > 0L,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isDownloading) "Téléchargement…" else "Enregistrer hors ligne")
                    }
                    if (draftVertices.isNotEmpty()) {
                        OutlinedButton(onClick = { draftVertices = emptyList(); selectedTool = MapTool.MOVE }) {
                            Text("Effacer")
                        }
                    }
                }
                offlineAreas.firstOrNull()?.let { latest ->
                    val percent = if (latest.requiredResources > 0) {
                        (100L * latest.completedResources / latest.requiredResources).coerceIn(0L, 100L).toInt()
                    } else null
                    Text(
                        if (latest.isComplete) latest.name + " · prêt · " + (latest.downloadedBytes / (1024 * 1024)) + " Mo"
                        else latest.name + " · " + (percent?.toString()?.plus(" %") ?: (latest.completedResources.toString() + " ressources")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!latest.isComplete) {
                        LinearProgressIndicator(
                            progress = { (percent ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text("© IGN · Géoplateforme", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (mapMessage != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 26.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mapMessage!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { mapMessage = null }) {
                        Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }

    if (pendingPoint != null) {
        AlertDialog(
            onDismissRequest = { pendingPoint = null },
            title = { Text(if (pendingKind == "LEVEL") "Cote projetée" else "Nouveau point") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(featureName, { featureName = it }, label = { Text("Nom") }, singleLine = true)
                    if (pendingKind == "LEVEL") {
                        OutlinedTextField(targetAltitude, { targetAltitude = it }, label = { Text("Altitude souhaitée (m)") }, singleLine = true)
                    }
                    pendingPoint?.let {
                        Text(
                            String.format(Locale.FRANCE, "%.6f · %.6f", it.latitude, it.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { savePointOrLevel() }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { pendingPoint = null }) { Text("Annuler") } }
        )
    }

    if (showZoneDialog) {
        AlertDialog(
            onDismissRequest = { showZoneDialog = false },
            title = { Text("Enregistrer la zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(zoneName, { zoneName = it }, label = { Text("Nom de la zone") }, singleLine = true)
                    Text(draftVertices.size.toString() + " sommets · polygone fermé", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { saveZone() }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { showZoneDialog = false }) { Text("Continuer") } }
        )
    }

    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showOfflineDialog = false },
            title = { Text("Carte hors ligne") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(offlineName, { offlineName = it }, label = { Text("Nom de la zone") }, singleLine = true)
                    Text(
                        "Télécharge l'emprise affichée pour les niveaux de zoom 13 à 17. Zoome sur le chantier avant de lancer.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Connecte la tablette au Wi-Fi avant de lancer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { downloadVisibleArea() }, enabled = !isDownloading) { Text("Lancer") } },
            dismissButton = { TextButton(onClick = { showOfflineDialog = false }, enabled = !isDownloading) { Text("Annuler") } }
        )
    }
}

private fun moveToCurrentLocation(
    locationClient: FusedLocationProviderClient,
    map: MapLibreMap?,
    onMessage: (String) -> Unit
) {
    if (map == null) {
        onMessage("La carte n'est pas encore prête.")
        return
    }
    try {
        val token = CancellationTokenSource()
        locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    onMessage("Position GPS indisponible. Vérifie que la localisation est activée.")
                } else {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17.0),
                        650
                    )
                    onMessage("Carte centrée sur la position GPS.")
                }
            }
            .addOnFailureListener { error ->
                onMessage("GPS indisponible : " + (error.localizedMessage ?: "erreur inconnue"))
            }
    } catch (_: SecurityException) {
        onMessage("Autorisation de localisation refusée.")
    }
}

private fun installFeatureLayers(style: Style, geoJson: String) {
    if (style.getSource(FEATURES_SOURCE) == null) {
        style.addSource(GeoJsonSource(FEATURES_SOURCE, geoJson))
    } else {
        (style.getSource(FEATURES_SOURCE) as? GeoJsonSource)?.setGeoJson(geoJson)
    }
    if (style.getLayer(ZONE_FILL) == null) {
        style.addLayer(
            FillLayer(ZONE_FILL, FEATURES_SOURCE).withProperties(
                PropertyFactory.fillColor(android.graphics.Color.parseColor("#E56F21")),
                PropertyFactory.fillOpacity(0.20f),
                PropertyFactory.fillAntialias(true)
            )
        )
    }
    if (style.getLayer(ZONE_LINE) == null) {
        style.addLayer(
            LineLayer(ZONE_LINE, FEATURES_SOURCE).withProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#C75E1B")),
                PropertyFactory.lineWidth(2.5f)
            )
        )
    }
    if (style.getLayer(POINTS_LAYER) == null) {
        style.addLayer(
            CircleLayer(POINTS_LAYER, FEATURES_SOURCE).withProperties(
                PropertyFactory.circleColor(android.graphics.Color.parseColor("#1976D2")),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                PropertyFactory.circleStrokeWidth(2.5f)
            )
        )
    }
    if (style.getLayer(LABELS_LAYER) == null) {
        style.addLayer(
            SymbolLayer(LABELS_LAYER, FEATURES_SOURCE).withProperties(
                PropertyFactory.textField("{name}"),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor(android.graphics.Color.parseColor("#172127")),
                PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f))
            )
        )
    }
}

private fun makeFeatureCollection(features: List<MapFeatureEntity>, draftVertices: List<LatLng>): String {
    val objects = JSONArray()
    features.forEach { feature ->
        try {
            val properties = JSONObject()
                .put("name", feature.name)
                .put("kind", feature.kind)
                .put("targetAltitudeM", feature.targetAltitudeM)
                .put("color", feature.colorHex)
            objects.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", JSONObject(feature.geometryJson))
                    .put("properties", properties)
            )
        } catch (_: Exception) {
            // Ignore une géométrie invalide sans masquer les autres objets cartographiques.
        }
    }
    if (draftVertices.size >= 2) {
        val coordinates = JSONArray()
        draftVertices.forEach { coordinates.put(JSONArray().put(it.longitude).put(it.latitude)) }
        val geometry = if (draftVertices.size >= 3) {
            val closed = JSONArray()
            for (index in 0 until coordinates.length()) closed.put(coordinates.get(index))
            val first = draftVertices.first()
            closed.put(JSONArray().put(first.longitude).put(first.latitude))
            JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(closed))
        } else {
            JSONObject().put("type", "LineString").put("coordinates", coordinates)
        }
        objects.put(
            JSONObject()
                .put("type", "Feature")
                .put("geometry", geometry)
                .put("properties", JSONObject().put("name", "Zone en cours").put("kind", "DRAFT"))
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", objects).toString()
}

private fun estimateVisibleTiles(bounds: LatLngBounds, minZoom: Int, maxZoom: Int): Long {
    var total = 0L
    for (zoom in minZoom..maxZoom) {
        val west = tileX(bounds.longitudeWest, zoom)
        val east = tileX(bounds.longitudeEast, zoom)
        val north = tileY(bounds.latitudeNorth, zoom)
        val south = tileY(bounds.latitudeSouth, zoom)
        total += (kotlin.math.abs(east - west) + 1L) * (kotlin.math.abs(south - north) + 1L)
    }
    return total
}

private fun tileX(longitude: Double, zoom: Int): Long {
    val count = 1L shl zoom
    return floor((longitude + 180.0) / 360.0 * count).toLong().coerceIn(0L, count - 1L)
}

private fun tileY(latitude: Double, zoom: Int): Long {
    val count = 1L shl zoom
    val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
    val radians = Math.toRadians(clamped)
    val value = (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0 * count
    return floor(value).toLong().coerceIn(0L, count - 1L)
}
