package app.punktgenau.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Öffnet die Navigation zu einer Adresse. Zuerst Google Maps mit gestarteter Route,
 * sonst eine beliebige Karten-App über den geo-Link.
 */
fun openNavigation(context: Context, address: String) {
    val encoded = Uri.encode(address)
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).setPackage("com.google.android.apps.maps"),
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // nächsten Versuch nehmen
        }
    }
    Toast.makeText(context, "Keine Navigations-App gefunden.", Toast.LENGTH_SHORT).show()
}
