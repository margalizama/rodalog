@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rodalog.R
import app.rodalog.data.FuelEntry
import app.rodalog.data.Stats
import app.rodalog.data.Vehicle
import app.rodalog.data.newId
import app.rodalog.util.Fmt
import app.rodalog.util.toNum

@Composable
fun FuelScreen(vm: AppViewModel) {
    val v by vm.current.collectAsState()
    val s by vm.settings.collectAsState()
    val fuel by vm.fuel.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    val veh = v
    var dialog by remember { mutableStateOf<FuelEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (veh == null) {
        EmptyState(Icons.Filled.LocalGasStation, stringResource(R.string.need_vehicle_first))
        return
    }

    val asc = remember(fuel) { fuel.sortedWith(compareBy<FuelEntry>({ it.odometerKm }, { it.date })) }
    val intervals = remember(asc) { Stats.intervals(asc) }
    val byDate = remember(intervals) { intervals.associateBy { it.date } }
    val avg = Stats.avgConsumption(asc, veh.baseConsumption)
    val best = intervals.minOfOrNull { it.l100 } ?: 0.0
    val total = fuel.sumOf { it.totalCost }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card2(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(stringResource(R.string.avg_consumption), fmt.cons(avg), Modifier.weight(1f))
                        StatTile(stringResource(R.string.best), fmt.cons(best), Modifier.weight(1f))
                        StatTile(stringResource(R.string.total_spent), fmt.money(total), Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                    }
                    if (intervals.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.consumption_trend, fmt.consUnit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LineChart(intervals.map { fmt.consVal(it.l100) })
                    }
                }
            }
            if (fuel.isEmpty()) {
                item { Text(stringResource(R.string.no_fuel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(fuel, key = { it.id }) { e ->
                Card2(Modifier.fillMaxWidth(), onClick = { dialog = e }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.date(e.date), fontWeight = FontWeight.Bold)
                            Text(
                                listOf(fmt.dist(e.odometerKm, 0), e.station).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt.money(e.totalCost), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                "${fmt.vol(e.liters)} · ${fmt.money(e.pricePerLiter * (if (fmt.volUnit == "gal") 3.785411784 else 1.0))}/${fmt.volUnit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val point = byDate[e.date]
                    if (point != null) {
                        Text(
                            fmt.cons(point.l100),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Icon(Icons.Filled.Add, null) }
    }

    if (adding) {
        FuelDialog(null, veh, fmt, { adding = false }, { vm.saveFuel(it, true); adding = false }, null)
    }
    val d = dialog
    if (d != null) {
        FuelDialog(d, veh, fmt, { dialog = null }, { vm.saveFuel(it, false); dialog = null }, { vm.deleteFuel(d); dialog = null })
    }
}

@Composable
private fun FuelDialog(
    initial: FuelEntry?,
    vehicle: Vehicle,
    fmt: Fmt,
    onDismiss: () -> Unit,
    onSave: (FuelEntry) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var date by remember { mutableLongStateOf(initial?.date ?: System.currentTimeMillis()) }
    var odo by remember { mutableStateOf(fmt.num(fmt.distVal(initial?.odometerKm ?: vehicle.odometerKm), 1)) }
    var liters by remember { mutableStateOf(if (initial != null) fmt.num(fmt.volVal(initial.liters), 2) else "") }
    var cost by remember { mutableStateOf(if (initial != null) fmt.num(initial.totalCost, 2) else "") }
    var full by remember { mutableStateOf(initial?.fullTank ?: true) }
    var station by remember { mutableStateOf(initial?.station ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    val l = liters.toNum()
    val c = cost.toNum()
    FormDialog(
        title = stringResource(R.string.fuel_entry),
        onDismiss = onDismiss,
        saveEnabled = l > 0,
        onDelete = onDelete,
        onSave = {
            onSave(
                FuelEntry(
                    id = initial?.id ?: newId(),
                    vehicleId = vehicle.id,
                    date = date,
                    odometerKm = fmt.kmFromInput(odo.toNum()),
                    liters = fmt.litersFromInput(l),
                    totalCost = c,
                    fullTank = full,
                    station = station.trim(),
                    notes = notes.trim(),
                )
            )
        },
    ) {
        DateField(stringResource(R.string.date), date, fmt) { date = it }
        NumField(odo, { odo = it }, "${stringResource(R.string.odometer)} (${fmt.distUnit})")
        NumField(liters, { liters = it }, "${stringResource(R.string.quantity)} (${fmt.volUnit})")
        NumField(cost, { cost = it }, stringResource(R.string.total_cost))
        if (l > 0 && c > 0) {
            Text(
                "${fmt.money(c / l)}/${fmt.volUnit}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.full_tank), Modifier.weight(1f))
            Switch(checked = full, onCheckedChange = { full = it })
        }
        TextF(station, { station = it }, stringResource(R.string.station))
        TextF(notes, { notes = it }, stringResource(R.string.notes), single = false)
    }
}
