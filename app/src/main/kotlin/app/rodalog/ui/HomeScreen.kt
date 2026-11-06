@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rodalog.R
import app.rodalog.data.FuelEntry
import app.rodalog.data.Reminder
import app.rodalog.data.Stats
import app.rodalog.data.Vehicle
import app.rodalog.trip.Phase
import app.rodalog.trip.TripBus
import app.rodalog.util.Fmt
import app.rodalog.util.KM_PER_MI
import java.util.Calendar

@Composable
fun HomeScreen(vm: AppViewModel, go: (String) -> Unit) {
    val v by vm.current.collectAsState()
    val s by vm.settings.collectAsState()
    val fuel by vm.fuel.collectAsState()
    val exp by vm.expenses.collectAsState()
    val rem by vm.reminders.collectAsState()
    val trips by vm.trips.collectAsState()
    val live by TripBus.state.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    val veh = v
    var adjust by remember { mutableStateOf(false) }

    if (veh == null) {
        EmptyState(
            Icons.Filled.DirectionsCar,
            stringResource(R.string.no_vehicles),
            stringResource(R.string.add_vehicle),
        ) { go("vehicles") }
        return
    }

    val asc = remember(fuel) { fuel.sortedWith(compareBy<FuelEntry>({ it.odometerKm }, { it.date })) }
    val cons = remember(asc, veh.baseConsumption) { Stats.avgConsumption(asc, veh.baseConsumption) }
    val monthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
    }
    val monthSpend = fuel.filter { it.date >= monthStart }.sumOf { it.totalCost } +
        exp.filter { it.date >= monthStart }.sumOf { it.cost }
    val monthKm = trips.filter { it.startTime >= monthStart }.sumOf { it.distanceKm }
    val activeTrip = live.phase == Phase.RECORDING
    val liters = if (activeTrip) live.fuelLeftL else veh.fuelLevelL
    val range = if (activeTrip) live.rangeKm else if (cons > 0) veh.fuelLevelL / cons * 100.0 else 0.0
    val dist = if (asc.size >= 2) asc.last().odometerKm - asc.first().odometerKm else 0.0
    val perKm = if (dist > 0) asc.drop(1).sumOf { it.totalCost } / dist else 0.0
    val perUnit = if (s.distanceUnit == "MI") perKm * KM_PER_MI else perKm
    val lastFill = fuel.firstOrNull()
    val pending = rem.filter { !it.done }.take(3)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(veh, fmt, range, liters, activeTrip) { adjust = true } }

        if (live.phase != Phase.IDLE) {
            item {
                Card2(Modifier.fillMaxWidth(), onClick = { go("trips") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (live.phase == Phase.RECORDING) R.string.trip_recording else R.string.trip_waiting),
                                fontWeight = FontWeight.Bold,
                            )
                            Text("${fmt.dist(live.distanceKm)} · ${fmt.speed(live.speedKmh)}")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.avg_consumption), fmt.cons(cons), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.last_price),
                    if (lastFill != null && lastFill.liters > 0) "${fmt.money(lastFill.pricePerLiter * if (fmt.volUnit == "gal") 3.785411784 else 1.0)}/${fmt.volUnit}" else "—",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.month_spend), fmt.money(monthSpend), Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                StatTile(stringResource(R.string.month_distance), fmt.dist(monthKm, 0), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.cost_per_distance, fmt.distUnit),
                    if (perUnit > 0) fmt.money(perUnit) else "—",
                    Modifier.weight(1f),
                )
                StatTile(stringResource(R.string.trips_count), "${trips.size}", Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { go("fuel") }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.LocalGasStation, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tab_fuel))
                }
                FilledTonalButton(onClick = { go("expenses") }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Receipt, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tab_expenses))
                }
                FilledTonalButton(onClick = { go("trips") }, Modifier.weight(1f)) {
                    Icon(Icons.Filled.Speed, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tab_trips))
                }
            }
        }

        if (pending.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.upcoming)) }
            items(pending.size) { i ->
                ReminderRow(pending[i], veh, fmt, onClick = { go("reminders") })
            }
        }
    }
    if (adjust && veh.tankL > 0) {
        var lvl by remember(adjust) { mutableFloatStateOf((veh.fuelLevelL / veh.tankL).coerceIn(0.0, 1.0).toFloat()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { adjust = false },
            title = { Text(stringResource(R.string.adjust_level)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.adjust_level_help))
                    Text(
                        "${(lvl * 100).toInt()}% · ${fmt.vol(lvl * veh.tankL, 1)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(value = lvl, onValueChange = { lvl = it }, steps = 19)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.setFuelLevel(veh, lvl * veh.tankL)
                    adjust = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { adjust = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Hero(v: Vehicle, fmt: Fmt, range: Double, liters: Double, locked: Boolean, onAdjust: () -> Unit) {
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val frac = if (v.tankL > 0) (liters / v.tankL).coerceIn(0.0, 1.0).toFloat() else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOf(v.make, v.model, v.plate).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { v.name },
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(stringResource(R.string.odometer), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Text(fmt.dist(v.odometerKm, 0), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.tank_level), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (v.tankL > 0) "${fmt.vol(liters, 1)} / ${fmt.vol(v.tankL, 0)}" else fmt.vol(liters, 1),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.range_est, fmt.dist(range, 0)),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Box(
                    Modifier.size(width = 112.dp, height = 144.dp).clickable(enabled = v.tankL > 0 && !locked) { onAdjust() },
                    contentAlignment = Alignment.Center,
                ) {
                    TankGauge(frac, Modifier.fillMaxSize())
                    if (v.tankL > 0) {
                        Text("${(frac * 100).toInt()}%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (v.tankL > 0 && !locked) {
                Text(stringResource(R.string.adjust_hint), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
            if (v.tankL <= 0) {
                Text(stringResource(R.string.set_tank_hint), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            } else if (frac < 0.15f) {
                Text(stringResource(R.string.low_fuel), color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReminderRow(r: Reminder, v: Vehicle?, fmt: Fmt, onClick: () -> Unit) {
    val (text, over) = dueText(r, v, fmt)
    Card2(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(r.title, fontWeight = FontWeight.Bold)
        Text(
            text,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun dueText(r: Reminder, v: Vehicle?, fmt: Fmt): Pair<String, Boolean> {
    val now = System.currentTimeMillis()
    val parts = ArrayList<String>()
    var over = false
    if (r.dueDate > 0) {
        parts.add(fmt.date(r.dueDate))
        if (r.dueDate < now && !r.done) over = true
    }
    if (r.dueOdometerKm > 0) {
        parts.add(fmt.dist(r.dueOdometerKm, 0))
        if (v != null && v.odometerKm >= r.dueOdometerKm && !r.done) over = true
    }
    if (parts.isEmpty()) parts.add(stringResource(R.string.no_due))
    return Pair(parts.joinToString(" · "), over)
}
