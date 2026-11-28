package fr.terrapilot.assistant

import android.app.Application
import org.maplibre.android.MapLibre

class TerraPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
