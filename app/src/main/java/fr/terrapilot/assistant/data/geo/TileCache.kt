package fr.terrapilot.assistant.data.geo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class IgnLayer(val id: String, val label: String, val format: String)

object IgnGeoplateforme {
    val layers = listOf(
        IgnLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "Plan IGN v2", "image/png"),
        IgnLayer("ORTHOIMAGERY.ORTHOPHOTOS", "Photos aériennes IGN", "image/jpeg")
    )

    fun tileUrl(layer: IgnLayer, z: Int, x: Int, y: Int): String {
        return "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
            "&LAYER=${layer.id}&STYLE=normal&TILEMATRIXSET=PM" +
            "&TILEMATRIX=$z&TILEROW=$y&TILECOL=$x&FORMAT=${layer.format}"
    }

    fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val latRad = Math.toRadians(lat)
        val n = 1 shl zoom
        val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }
}

class TileCache(context: Context) {
    private val rootDir = File(context.filesDir, "ign_tiles").apply { mkdirs() }

    fun tileFile(layerId: String, z: Int, x: Int, y: Int): File {
        val dir = File(rootDir, "$layerId/$z/$x")
        dir.mkdirs()
        return File(dir, "$y.tile")
    }

    suspend fun getOrDownloadTile(layer: IgnLayer, z: Int, x: Int, y: Int): ByteArray? = withContext(Dispatchers.IO) {
        val file = tileFile(layer.id, z, x, y)
        if (file.exists() && file.length() > 0) {
            return@withContext file.readBytes()
        }
        return@withContext try {
            val conn = URL(IgnGeoplateforme.tileUrl(layer, z, x, y)).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "TerraPilot/1.0")
            if (conn.responseCode == 200) {
                val bytes = conn.inputStream.readBytes()
                file.writeBytes(bytes)
                bytes
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun cacheSizeBytes(): Long = rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache() {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
    }
}
