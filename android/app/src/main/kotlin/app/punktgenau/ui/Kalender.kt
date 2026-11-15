package app.punktgenau.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Schreibt die .ics-Datei in den Cache-Ordner und öffnet sie in der Kalender-App. Die Datei geht über einen
 * FileProvider, nur die Kalender-App bekommt Lesezugriff.
 */
fun oeffneKalender(context: Context, dateiname: String, inhalt: String) {
    val dir = File(context.cacheDir, "kalender").apply { mkdirs() }
    val file = File(dir, dateiname)
    file.writeText(inhalt, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.kalender", file)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/calendar")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(view, "Im Kalender öffnen"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Keine Kalender-App gefunden", Toast.LENGTH_LONG).show()
    }
}

/** Dateiname aus Wortteilen, ASCII und klein; Umlaute und Sonderzeichen werden zu Bindestrichen. */
fun kalenderDateiname(vararg teile: String): String =
    teile.joinToString("-").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + ".ics"
