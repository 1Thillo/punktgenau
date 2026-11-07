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
import androidx.compose.material3.TextButton
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onCancel) { Text("Abbrechen") }
            Text(
                "Melde dich an, dann tippe auf Fertig.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Button({
                CookieManager.getInstance().flush()
                onDone(CookieManager.getInstance().getCookie(startUrl).orEmpty())
            }) { Text("Fertig") }
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
