package app.punktgenau.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.punktgenau.PunktgenauApp
import app.punktgenau.data.MatchAlerts
import app.punktgenau.data.MatchTime
import app.punktgenau.data.Result
import java.util.concurrent.TimeUnit

/**
 * Prüft im Hintergrund die Spielpläne der eigenen und gemerkten Mannschaften und meldet kommende Spiele
 * und neue Ergebnisse. Läuft nur, wenn die Funktion eingeschaltet ist und man angemeldet ist.
 *
 * Schonend zur Website: alle drei Stunden, nur mit Netz, und über dieselben Abrufe wie der Spieltag-Bildschirm
 * (der Zwischenspeicher für Spielpläne greift dabei ebenfalls).
 */
class MatchAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val app = applicationContext as PunktgenauApp
        if (!app.alertsEnabled) return ListenableWorker.Result.success()
        val matches = when (val r = app.spieltag.ownMatches()) {
            is Result.Ok -> r.value
            // Nicht angemeldet oder Netzfehler: nichts melden, der nächste Lauf versucht es erneut.
            else -> return ListenableWorker.Result.success()
        }
        val store = app.alertStore
        val now = MatchTime.now()
        if (!app.alertsBaselined) {
            // Erster Lauf nach dem Einschalten: nur den Stand merken, nichts melden.
            store.add(MatchAlerts.baseline(matches, now))
            app.markAlertsBaselined()
            return ListenableWorker.Result.success()
        }
        val alerts = MatchAlerts.plan(matches, now, store.sent())
        if (alerts.isEmpty()) return ListenableWorker.Result.success()
        // Ohne Erlaubnis bleibt alles offen und wird beim nächsten Lauf mit Erlaubnis gemeldet.
        if (!Notifier.canPost(applicationContext)) return ListenableWorker.Result.success()
        alerts.forEachIndexed { i, a -> Notifier.post(applicationContext, notificationId = 1000 + i, title = a.title, text = a.text) }
        store.add(alerts.map { it.key })
        return ListenableWorker.Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "match-alerts"

        /** Startet die periodische Prüfung oder beendet sie. */
        fun setEnabled(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<MatchAlertWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Benachrichtigungen auf dem Gerät: Kanal, Erlaubnis und Anzeige. */
object Notifier {
    const val CHANNEL_ID = "spiele"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Spiele", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Ankündigungen kommender Spiele und Ergebnisse" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, notificationId: Int, title: String, text: String) {
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // Erlaubnis wird in canPost() geprüft
        NotificationManagerCompat.from(context).notify(notificationId, n)
    }
}
