@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place as PlaceIcon
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rodalog.R
import app.rodalog.data.Place
import app.rodalog.data.label

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "HOME" -> Icons.Filled.Home
    "WORK" -> Icons.Filled.Work
    "GAS" -> Icons.Filled.LocalGasStation
    "SHOP" -> Icons.Filled.Store
    else -> Icons.Filled.PlaceIcon
}

@Composable
fun PlacesScreen(vm: AppViewModel) {
    val places by vm.places.collectAsState()
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Place?>(null) }

    val saved = places.filter { it.confirmed }
    val watching = places.filter { !it.confirmed && it.visits >= 2 }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                stringResource(R.string.places_help, s.placeMinVisits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (saved.isEmpty() && watching.isEmpty()) {
            item {
                Text(stringResource(R.string.no_places), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (saved.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.places_saved)) }
            items(saved, key = { it.id }) { p -> PlaceRow(p, p.label(ctx)) { editing = p } }
        }
        if (watching.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.places_watching)) }
            items(watching, key = { it.id }) { p ->
                PlaceRow(p, stringResource(R.string.place_candidate, p.visits, s.placeMinVisits)) { editing = p }
            }
        }
    }

    editing?.let { p ->
        PlaceDialog(
            place = p,
            onDismiss = { editing = null },
            onSave = {
                vm.savePlace(it)
                editing = null
            },
            onDelete = {
                vm.deletePlace(p)
                editing = null
            },
        )
    }
}

@Composable
private fun PlaceRow(p: Place, title: String, onClick: () -> Unit) {
    Card2(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(p.kind), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                val sub = listOf(
                    if (p.address.isNotBlank()) p.address else "",
                    if (p.confirmed) stringResource(R.string.place_visits, p.visits) else "",
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaceDialog(place: Place, onDismiss: () -> Unit, onSave: (Place) -> Unit, onDelete: () -> Unit) {
    var name by remember(place.id) { mutableStateOf(place.name) }
    var kind by remember(place.id) { mutableStateOf(place.kind) }
    FormDialog(
        title = stringResource(R.string.place),
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = { onSave(place.copy(name = name.trim(), kind = kind)) },
    ) {
        TextF(name, { name = it }, stringResource(R.string.place_name_hint))
        Choice(
            listOf(
                "HOME" to stringResource(R.string.kind_home),
                "WORK" to stringResource(R.string.kind_work),
                "GAS" to stringResource(R.string.kind_gas),
            ),
            kind,
        ) { kind = it }
        Choice(
            listOf(
                "SHOP" to stringResource(R.string.kind_shop),
                "OTHER" to stringResource(R.string.kind_other),
            ),
            kind,
        ) { kind = it }
    }
}
