package fr.terrapilot.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chantiers")
data class ChantierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val adresse: String,
    val client: String,
    val dateCreation: Long = System.currentTimeMillis(),
    val dateDebut: Long = System.currentTimeMillis(),
    val observations: String = "",
    val originLat: Double? = null,
    val originLon: Double? = null
)

@Entity(tableName = "niveaux")
data class NiveauEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val nomPoint: String,
    val referenceAlt: Double,
    val lectureMireRef: Double,
    val planLaser: Double,
    val lectureMirePoint: Double,
    val altitudePoint: Double,
    val cibleAlt: Double,
    val ecartMetres: Double,
    val statut: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "pentes")
data class PenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val libelle: String,
    val pourcentage: Double,
    val cmParMetre: Double,
    val distanceMetres: Double,
    val deniveleMetres: Double,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "volumes")
data class VolumeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val typeOuvrage: String,
    val description: String,
    val volumeNetM3: Double,
    val volumeFoisonneM3: Double,
    val coeffFoisonnement: Double,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "implantations")
data class ImplantationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val ouvrage: String,
    val longueurM: Double,
    val largeurM: Double,
    val diagonaleTheoriqueM: Double,
    val diagonale1M: Double?,
    val diagonale2M: Double?,
    val equerrageOk: Boolean,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "releves_topo")
data class RelevePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val codePoint: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val methode: String,
    val precisionEstimeeM: Double?,
    val commentaire: String = "",
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val nom: String,
    val uriString: String,
    val typeFichier: String,
    val echellePixelsParMetre: Double? = null,
    val calibrationNote: String = "",
    val annotationsJson: String = "[]",
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "reseaux")
data class ReseauEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val typeReseau: String,
    val classePrecision: String,
    val fiabilite: String,
    val profondeurConnueM: Double?,
    val source: String,
    val dateDocument: String,
    val margeVigilanceM: Double,
    val lat: Double?,
    val lon: Double?,
    val commentaire: String = ""
)

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val uriString: String,
    val phase: String,
    val commentaire: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "historique")
data class HistoriqueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    val module: String,
    val resume: String,
    val date: Long = System.currentTimeMillis()
)


@Entity(
    tableName = "map_features",
    indices = [androidx.room.Index(value = ["chantierId"])]
)
data class MapFeatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chantierId: Long,
    /** POINT, ZONE or LEVEL. */
    val kind: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val targetAltitudeM: Double? = null,
    /** GeoJSON Geometry only (Point or Polygon), coordinates in longitude/latitude order. */
    val geometryJson: String,
    val colorHex: String = "#F57C00",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "offline_areas",
    indices = [androidx.room.Index(value = ["chantierId"])]
)
data class OfflineAreaEntity(
    /** MapLibre's own offline region identifier. */
    @PrimaryKey val mapLibreRegionId: Long,
    val chantierId: Long,
    val name: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val minZoom: Double,
    val maxZoom: Double,
    val completedResources: Long = 0,
    val requiredResources: Long = 0,
    val downloadedBytes: Long = 0,
    val isComplete: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
