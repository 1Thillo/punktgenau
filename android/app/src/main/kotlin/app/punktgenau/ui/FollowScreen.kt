package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.punktgenau.data.ClubRepository
import app.punktgenau.data.ClubTeam
import app.punktgenau.data.FollowStore
import app.punktgenau.data.Result
import app.punktgenau.data.Season
import app.punktgenau.data.TeamInfo
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Mannschaften merken: Verein über den genauen Namen suchen, Mannschaften wählen. */
@Composable
fun FollowScreen(clubs: ClubRepository, store: FollowStore, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<ClubTeam>() }
    var foundClub by remember { mutableStateOf("") }
    val followed = remember { mutableStateListOf<TeamInfo>().also { it.addAll(store.load()) } }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    fun toggle(t: ClubTeam) {
        val info = TeamInfo(t.teamId, t.name, t.league.ifBlank { null }, t.season.ifBlank { null }, t.groupId, t.organization, foundClub.ifBlank { null })
        if (followed.any { it.id == t.teamId }) {
            store.remove(t.teamId)
            followed.removeAll { it.id == t.teamId }
        } else {
            store.add(info)
            followed.add(info)
        }
    }

    fun remove(id: String) {
        store.remove(id)
        followed.removeAll { it.id == id }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Mannschaften merken")
        InfoText("Den Vereinsnamen genau so eingeben, wie er in click-TT steht, zum Beispiel \"DJK Mayen\".")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Vereinsname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            {
                scope.launch {
                    busy = true
                    status = null
                    found.clear()
                    val ref = clubs.findClub(query)
                    if (ref == null) {
                        status = "Kein Verein mit diesem Namen gefunden."
                    } else {
                        when (val r = clubs.load(ref.clubNr, ref.association, Season.label(LocalDate.now()))) {
                            is Result.Ok -> {
                                if (r.value.teams.isEmpty()) status = "Der Verein hat keine Mannschaften in dieser Saison."
                                foundClub = r.value.info?.name ?: query.trim()
                                found.addAll(r.value.teams)
                            }
                            else -> status = "Die Mannschaften konnten nicht geladen werden."
                        }
                    }
                    busy = false
                }
            },
            Modifier.fillMaxWidth(),
            enabled = query.isNotBlank() && !busy,
        ) { Text("Suchen") }
        status?.let { InfoText(it) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (found.isNotEmpty()) {
                item { SectionLabel("Mannschaften des Vereins") }
                items(found) { t ->
                    val on = followed.any { it.id == t.teamId }
                    TeamToggleRow(label(foundClub, t.name), t.league, on) { toggle(t) }
                }
            }
            if (followed.isNotEmpty()) {
                item { SectionLabel("Gemerkt") }
                items(followed.toList()) { t ->
                    TeamToggleRow(label(t.club, t.name), t.league.orEmpty(), true) { remove(t.id) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Verein und Mannschaft in einer Zeile, damit gleiche Mannschaftsnamen verschiedener Vereine unterscheidbar sind. */
private fun label(club: String?, team: String): String = if (club.isNullOrBlank()) team else "$club – $team"

@Composable
private fun TeamToggleRow(name: String, league: String, followed: Boolean, onToggle: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (league.isNotBlank()) {
                    Text(league, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (followed) {
                OutlinedButton(onToggle) { Text("Entfernen") }
            } else {
                Button(onToggle) { Text("Merken") }
            }
        }
    }
}
