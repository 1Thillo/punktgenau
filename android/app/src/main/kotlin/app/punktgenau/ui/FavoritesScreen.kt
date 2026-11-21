package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.punktgenau.data.FavoriteStore
import app.punktgenau.data.PlayerLink

/** Gemerkte Spielerinnen und Spieler. Antippen öffnet die Person, „Entfernen“ nimmt sie aus der Liste. */
@Composable
fun FavoritesScreen(store: FavoriteStore, onOpen: (PlayerLink) -> Unit, onClose: () -> Unit) {
    // Wird bei jedem Öffnen neu gelesen, damit Änderungen auf der Spielerseite sofort sichtbar sind.
    val favorites = remember { mutableStateListOf<PlayerLink>().also { it.addAll(store.load()) } }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Favoriten")
        if (favorites.isEmpty()) {
            InfoText("Noch keine Favoriten. Auf der Seite einer Person „Als Favorit merken“ tippen.")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites.toList(), key = { it.nuId }) { p ->
                FavoriteRow(
                    player = p,
                    onOpen = { onOpen(p) },
                    onRemove = {
                        store.remove(p.nuId)
                        favorites.removeAll { it.nuId == p.nuId }
                    },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FavoriteRow(player: PlayerLink, onOpen: () -> Unit, onRemove: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClickLabel = "${player.name} ansehen", onClick = onOpen)) {
                Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                player.club?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onRemove) { Text("Entfernen") }
        }
    }
}
