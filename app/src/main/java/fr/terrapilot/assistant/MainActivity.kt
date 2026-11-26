package fr.terrapilot.assistant

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.terrapilot.assistant.data.db.DatabaseAccessGate
import fr.terrapilot.assistant.data.db.TerraRepository
import fr.terrapilot.assistant.domain.AppSettings
import fr.terrapilot.assistant.domain.SettingsRepository
import fr.terrapilot.assistant.ui.screens.*
import fr.terrapilot.assistant.ui.theme.TerraPilotTheme
import kotlinx.coroutines.launch

data class NavItem(val route: String, val label: String)

val navItems = listOf(
    NavItem("accueil", "Accueil"),
    NavItem("chantiers", "Mes chantiers"),
    NavItem("niveaux", "Niveaux"),
    NavItem("pentes", "Pentes"),
    NavItem("volumes", "Terrassement"),
    NavItem("implantation", "Implantation"),
    NavItem("releve", "Relevé topographique"),
    NavItem("plans", "Plans"),
    NavItem("carte", "Carte IGN"),
    NavItem("reseaux", "Réseaux enterrés"),
    NavItem("guidage", "Guidage laser"),
    NavItem("photos", "Photos"),
    NavItem("rapports", "Rapports"),
    NavItem("sauvegardes", "Sauvegardes"),
    NavItem("parametres", "Paramètres")
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepo = SettingsRepository(this)

        setContent {
            val dbEpoch by DatabaseAccessGate.epoch.collectAsState()
            val settings by settingsRepo.settingsFlow.collectAsState(initial = AppSettings())
            val chantiers by key(dbEpoch) { TerraRepository.observeChantiers(this).collectAsState(initial = emptyList()) }
            val activeChantier = chantiers.find { it.id == settings.activeChantierId } ?: chantiers.firstOrNull()

            LaunchedEffect(settings.orientationMode) {
                requestedOrientation = when (settings.orientationMode) {
                    "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            TerraPilotTheme(darkTheme = settings.darkTheme) {
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var currentRoute by remember { mutableStateOf("accueil") }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("ASSISTANT TERRASSEMENT", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                                HorizontalDivider()
                                navItems.forEach { item ->
                                    NavigationDrawerItem(
                                        label = { Text(item.label) },
                                        selected = currentRoute == item.route,
                                        onClick = {
                                            currentRoute = item.route
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    val title = navItems.find { it.route == currentRoute }?.label ?: "TerraPilot"
                                    val sub = activeChantier?.nom?.let { " — $it" } ?: ""
                                    Text("$title$sub", maxLines = 1)
                                },
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        key(dbEpoch) {
                            Box(Modifier.padding(padding).fillMaxSize()) {
                            val cid = activeChantier?.id ?: -1L
                            when (currentRoute) {
                                "accueil" -> HomeScreen(activeChantier) { currentRoute = it }
                                "chantiers" -> ChantiersScreen(
                                    activeChantierId = cid,
                                    onSelectChantier = { id ->
                                        scope.launch { settingsRepo.setActiveChantier(id) }
                                    },
                                    onCreatedChantier = { id ->
                                        scope.launch {
                                            settingsRepo.setActiveChantier(id)
                                            currentRoute = "carte"
                                        }
                                    }
                                )
                                "niveaux" -> NiveauxScreen(cid, settings)
                                "pentes" -> PentesScreen(cid)
                                "volumes" -> VolumesScreen(cid, settings)
                                "implantation" -> ImplantationScreen(cid)
                                "releve" -> ReleveScreen(cid)
                                "plans" -> PlansScreen(cid)
                                "carte" -> CarteScreen(cid)
                                "reseaux" -> ReseauxScreen(cid, settings)
                                "guidage" -> GuidageScreen()
                                "photos" -> PhotosScreen(cid)
                                "rapports" -> RapportsScreen(activeChantier)
                                "sauvegardes" -> SauvegardesScreen()
                                "parametres" -> ParametresScreen(settings, settingsRepo)
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
