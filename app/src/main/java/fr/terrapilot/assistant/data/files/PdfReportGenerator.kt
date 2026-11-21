package fr.terrapilot.assistant.data.files

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import fr.terrapilot.assistant.data.db.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {
    fun generate(
        context: Context,
        destUri: Uri,
        chantier: ChantierEntity,
        niveaux: List<NiveauEntity>,
        pentes: List<PenteEntity>,
        volumes: List<VolumeEntity>,
        reseaux: List<ReseauEntity>,
        simplified: Boolean
    ): Boolean {
        return try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas
            val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
            val h2Paint = Paint().apply { textSize = 13f; isFakeBoldText = true }
            val bodyPaint = Paint().apply { textSize = 10f }

            var y = 45f
            fun nextLine(text: String, paint: Paint = bodyPaint, step: Float = 16f) {
                if (y > 800f) {
                    doc.finishPage(page)
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = 45f
                }
                canvas.drawText(text, 40f, y, paint)
                y += step
            }

            val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
            nextLine(if (simplified) "RAPPORT SIMPLIFIÉ DE CHANTIER" else "RAPPORT COMPLET DE CHANTIER", titlePaint, 24f)
            nextLine("Chantier : ${chantier.nom}", h2Paint, 18f)
            nextLine("Adresse : ${chantier.adresse}")
            nextLine("Client / Maçon : ${chantier.client}")
            nextLine("Date : ${df.format(Date())}")
            if (chantier.observations.isNotBlank()) nextLine("Observations : ${chantier.observations}")
            y += 10f

            nextLine("VOLUMES (${volumes.size})", h2Paint, 18f)
            volumes.take(if (simplified) 5 else 30).forEach {
                nextLine("- ${it.typeOuvrage} : ${"%.2f".format(it.volumeNetM3)} m³ net (${"%.2f".format(it.volumeFoisonneM3)} m³ foisonné)")
            }
            y += 8f

            nextLine("PENTES (${pentes.size})", h2Paint, 18f)
            pentes.take(if (simplified) 5 else 30).forEach {
                nextLine("- ${it.libelle} : ${"%.2f".format(it.pourcentage)} % (${"%.2f".format(it.cmParMetre)} cm/m) sur ${it.distanceMetres} m")
            }
            y += 8f

            nextLine("NIVEAUX (${niveaux.size})", h2Paint, 18f)
            niveaux.take(if (simplified) 5 else 30).forEach {
                nextLine("- ${it.nomPoint} : alt ${"%.3f".format(it.altitudePoint)} m / cible ${"%.3f".format(it.cibleAlt)} m (${it.statut})")
            }

            if (!simplified) {
                y += 8f
                nextLine("RÉSEAUX ENTERRÉS (${reseaux.size})", h2Paint, 18f)
                reseaux.forEach {
                    nextLine("- ${it.typeReseau} [Classe ${it.classePrecision}] prof=${it.profondeurConnueM ?: "?"} m (${it.source})")
                }
                y += 10f
                nextLine("Rappel : Cet outil d'assistance ne remplace pas les DT-DICT ni le marquage-piquetage réglementaire.", bodyPaint)
            }

            doc.finishPage(page)
            context.contentResolver.openOutputStream(destUri)?.use { doc.writeTo(it) }
            doc.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
