package app.punktgenau.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Weiße Karte mit feinem Rand, die Grundfläche aller Listen und Blöcke. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content) }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, border = border, shape = MaterialTheme.shapes.medium, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border, shape = MaterialTheme.shapes.medium, content = inner)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

/** Kleines Etikett (z. B. "ÄK 16"). */
@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = textColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Änderung mit Vorzeichen und Pfeil, nie nur Farbe. */
@Composable
fun deltaText(delta: Int): Pair<String, Color> = when {
    delta > 0 -> "▲ +$delta" to PositiveGreen
    delta < 0 -> "▼ −${-delta}" to NegativeRed
    else -> "● 0" to NeutralGrey
}

@Composable
fun LoadingBox() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
}

@Composable
fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
