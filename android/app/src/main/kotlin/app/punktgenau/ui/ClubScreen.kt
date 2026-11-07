package app.punktgenau.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.punktgenau.data.Club
import app.punktgenau.data.ClubRepository
import app.punktgenau.data.ClubTeam
import app.punktgenau.data.GroupRef
import app.punktgenau.data.Result

/** Verein: Stammdaten und Mannschaften, Tipp auf eine Mannschaft öffnet ihre Liga. */
@Composable
fun ClubScreen(
    clubs: ClubRepository,
    clubNr: String,
    association: String,
    season: String,
    onClose: () -> Unit,
    onOpenLeague: (GroupRef) -> Unit,
) {
    var state by remember { mutableStateOf<Result<Club>?>(null) }
    LaunchedEffect(clubNr, season) { state = clubs.load(clubNr, association, season) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                when (val s = state) {
                    null -> LoadingBox()
                    is Result.Ok -> {
                        ScreenTitle(s.value.info?.name ?: "Verein")
                        s.value.info?.region?.let { InfoText(it) }
                    }
                    Result.NeedsLogin -> InfoText("Bitte zuerst anmelden.")
                    is Result.Failed -> InfoText(s.message)
                }
            }
            (state as? Result.Ok)?.value?.info?.let { info ->
                item {
                    AppCard {
                        SectionLabel("Kontakt")
                        info.contactName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        info.contactPhone?.let { Text(it.trim(), style = MaterialTheme.typography.bodyMedium) }
                        info.contactEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { SectionLabel("Mannschaften") }
            items((state as? Result.Ok)?.value?.teams.orEmpty()) { t: ClubTeam ->
                AppCard(onClick = {
                    onOpenLeague(GroupRef(t.organization.ifBlank { association }, t.season.ifBlank { season }, t.groupId, t.league.ifBlank { t.name }))
                }) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    Text(t.league, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
