package app.punktgenau.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.luminance
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
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    // Hell: weicher Schatten statt Rand. Dunkel: feiner Rand, weil Schatten dort kaum sichtbar ist.
    val border = if (dark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 3.dp)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, elevation = elevation, border = border, shape = MaterialTheme.shapes.medium, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, elevation = elevation, border = border, shape = MaterialTheme.shapes.medium, content = inner)
    }
}

/** Farbige Hero-Fläche für die wichtigste Zahl eines Bildschirms (TTR, Ergebnis). Text nutzt onPrimary. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF0EA5E9))))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Kleines Datumsfeld in Akzentfarbe, z. B. für Verlaufseinträge. */
@Composable
fun DateBadge(day: String, year: String) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(day, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(year, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
    }
}

/** Überschrift mit kurzem Akzentstrich, gibt jedem Bildschirm die gleiche Kopfzeile. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 12.dp)) {
        Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
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
