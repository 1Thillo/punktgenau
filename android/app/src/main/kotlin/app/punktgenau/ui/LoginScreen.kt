package app.punktgenau.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Anmeldung auf der Website im WebView. Passwort und Captcha bleiben dort; die App übernimmt nach "Fertig"
 * nur die Cookies. Das Cookie-Banner wird nicht vorbelegt.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(startUrl: String, onDone: (cookieHeader: String) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Kopfleiste im Stil der Hero-Flächen: Abbrechen links, Hinweis in der Mitte, Fertig rechts.
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF0EA5E9))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroChoice("Abbrechen", onCancel, arrow = false)
                Column(Modifier.weight(1f)) {
                    Text("ANMELDUNG", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    Text("Melde dich an, dann tippe auf Fertig.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Button(
                    {
                        CookieManager.getInstance().flush()
                        onDone(CookieManager.getInstance().getCookie(startUrl).orEmpty())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1D4ED8)),
                ) { Text("Fertig") }
            }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(startUrl)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
