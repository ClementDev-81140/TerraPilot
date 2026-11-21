# Architecture — ASSISTANT TERRASSEMENT (TerraPilot)

## 1. Choix technologiques

- **Kotlin + Jetpack Compose** (Material 3) : moderne, gros boutons, thèmes clair/sombre.
- **Room** : base locale (chantiers, mesures, réseaux, photos, plans, historique). OFFLINE FIRST.
- **WorkManager** : tâches différées (sauvegarde automatique, nettoyage cache tuiles).
- **SAF (Storage Access Framework)** : import/export PDF, JPG, PNG, GPX, ZIP (USB OTG, carte SD, cloud personnel).
- **DataStore** : paramètres (thème, orientation, alertes, foisonnement).
- **Play Services Location** : GPS tablette (FusedLocationProviderClient).
- **PdfDocument natif** : rapports PDF sans dépendance.
- **WMTS Géoplateforme IGN** : tuiles en ligne + cache local (aucun SDK carto lourd).

Aucune bibliothèque payante. Aucun service obligatoire.

## 2. Modules (séparation stricte)

```
fr.terrapilot.assistant/
├── core/calcul/      # Kotlin pur, sans Android — testable en JVM
│   ├── SlopeCalculator.kt       # pentes %, cm/m, altitudes, profil
│   ├── VolumeCalculator.kt      # tranchées, plateformes, fondations, déblai/remblai
│   ├── LevelCalculator.kt       # plan laser, lectures mire, écarts
│   ├── ImplantationCalculator.kt# diagonales, équerrage, report
│   ├── SurveyCalculator.kt      # stats, fiabilité, bilinéaire
│   └── GeoCalculator.kt         # haversine, ENU, tuiles WMTS, proximité
├── data/db/          # Room : Entities, Daos, AppDatabase
├── data/geo/         # GpxIO (iPhiGéNie), TileCache (IGN hors ligne)
├── data/files/       # BackupManager (ZIP+SHA-256), PdfReportGenerator
├── domain/           # SettingsRepository (DataStore)
└── ui/
    ├── theme/        # TerraPilotTheme (clair/sombre, contrastes chantier)
    ├── components/   # BigButton (56 dp), NumberField (unité visible), bannières
    └── screens/      # 15 écrans, tous câblés (aucun bouton mort)
```

Les calculs sont indépendants de l'UI : `core/calcul` ne référence jamais `android.*` (sauf `GeoCalculator` qui reste pur Kotlin).

## 3. Base de données

10 tables, toutes avec `chantierId` (séparation stricte) :

`chantiers` • `niveaux` • `pentes` • `volumes` • `points_releve` • `reseaux` (tracé JSON, classe A/B/C, certitude, marge) • `photos` • `plans` (calibration) • `annotations` (couche indépendante) • `historique`.

Chaque mesure conserve son origine (méthode, source, date, précision GPS).

## 4. Navigation

```
Accueil (chantier ouvert, derniers niveaux, raccourcis)
├── Mes chantiers (création, recherche nom/adresse, suppression confirmée)
├── Niveaux (plan laser → lectures → cote projet → 3 affichages)
├── Pentes (3 modes → profil + hauteurs par mètre)
├── Terrassement (tranchées / plateformes / fondations / déblai-remblai)
├── Implantation (rectangle, diagonales, équerrage)
├── Relevé topo (ruban / GPS / plan → grille → carte colorée)
├── Plans (import → calibration → annotations)
├── Carte IGN (WMTS → cache hors ligne → GPX iPhiGéNie)
├── Réseaux enterrés (fiches → vigilance → alertes)
├── Guidage laser (procédure KX012 + check-list)
├── Photos (avant / pendant / après)
├── Rapports (complet / simplifié → PDF)
├── Sauvegardes (ZIP USB/SD/cloud + intégrité)
└── Paramètres (thème, orientation, alertes, unités)
```

Accès : barre du bas (5 raccourcis) + menu ☰ (15 rubriques). Retour rapide au chantier via `current_chantier` persisté.

## 5. Hors connexion

Disponibles sans Internet : chantiers, tous les calculs, niveaux, pentes, volumes, implantation, plans importés, annotations, photos, réseaux, relevés, tuiles téléchargées, rapports PDF, sauvegarde locale. Seuls le téléchargement de tuiles et l'export cloud nécessitent une connexion (non bloquants).

## 6. Sauvegardes

- Locale automatique (Room, transactionnelle).
- ZIP complet (base + médias) avec manifeste SHA-256, via SAF (USB OTG, SD, dossier cloud personnel).
- Restauration avec vérification d'intégrité.
- Recommandation : 2 sauvegardes datées, dossier cloud chiffré si possible. Aucun abonnement payant.

## 7. Schéma de navigation (texte)

```
[Accueil] ──► [Chantiers] ──► [Niveaux] ──► [Pentes] ──► [Volumes]
   │              │              │             │              │
   ├─► [Implantation] ├─► [Relevé] ├─► [Plans] ├─► [Carte IGN]
   ├─► [Réseaux] ──► [Guidage] ──► [Photos] ──► [Rapports]
   └─► [Sauvegardes] ──► [Paramètres]
```
