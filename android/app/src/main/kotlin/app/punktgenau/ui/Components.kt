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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

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

/**
 * Kopfleiste für Unterseiten: "Zurück" als runde Tonfläche links, rechts optional eine kleine Aktion.
 * Ersetzt den losen Textknopf, damit jede Unterseite gleich beginnt.
 */
@Composable
fun BackBar(onBack: () -> Unit, label: String = "Zurück", trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onBack, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                "‹  $label",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Kleine Aktion in Akzentfarbe (Koralle), z. B. rechts in der [BackBar]. */
@Composable
fun AccentAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.5f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.5f),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** Umschalter mit klar sichtbarer Auswahl: Segmente in voller Breite, das gewählte blau gefüllt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Ein Wert auf der [HeroCard]: kleine Beschriftung, großer Wert, beides hell. */
@Composable
fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Heller Streifen in der [HeroCard] für mehrere Werte nebeneinander. */
@Composable
fun HeroStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.16f))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

/** Wählbare Fläche in der [HeroCard], z. B. eine Mannschaft. */
@Composable
fun HeroChoice(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, arrow: Boolean = true) {
    val shape = MaterialTheme.shapes.small
    val base = modifier.clip(shape).background(Color.White.copy(alpha = if (onClick != null) 0.2f else 0.1f))
    Box(if (onClick != null) base.clickable(onClick = onClick) else base, contentAlignment = Alignment.Center) {
        Text(
            text + if (onClick != null && arrow) "  ›" else "",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = if (onClick != null) 1f else 0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

/**
 * Listenzeile als Karte: links optional ein Kennzeichen (Rang, Datum), in der Mitte Titel und Unterzeile,
 * rechts die Zahl oder ein Pfeil. [extra] erscheint unter der Zeile.
 */
@Composable
fun ListCard(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = if (onClick != null) ({ Chevron() }) else null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
        }
        extra?.invoke(this)
    }
}

/** Pfeil rechts in Karten, die zu einer weiteren Seite führen. */
@Composable
fun Chevron() {
    Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
}

/** Zahl rechts in Listen: groß und fett, darunter klein die Beschriftung. */
@Composable
fun TrailingNumber(value: String, label: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Ergebnis als gefülltes Kästchen; [strong] blau, sonst zurückhaltend. */
@Composable
fun ScoreBox(text: String, strong: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Surface(color = if (strong) c.primary else c.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (strong) c.onPrimary else c.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Etikett für laufende Spiele in Akzentfarbe. */
@Composable
fun LivePill(text: String) {
    Pill(text, color = MaterialTheme.colorScheme.secondaryContainer, textColor = MaterialTheme.colorScheme.onSecondaryContainer)
}

/** Runder Anfangsbuchstabe als Kennzeichen links in Karten. */
@Composable
fun LetterBadge(text: String) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.trim().firstOrNull()?.uppercase() ?: "·",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Große Wahlkachel (z. B. auf der Ligaseite): Titel und kurze Erklärung, Pfeil oben. */
@Composable
fun ChoiceTile(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("→", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
