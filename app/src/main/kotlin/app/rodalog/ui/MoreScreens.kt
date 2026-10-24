@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rodalog.R
import app.rodalog.data.Reminder
import app.rodalog.data.Vehicle
import app.rodalog.data.newId
import app.rodalog.util.Fmt
import app.rodalog.util.toNum

// ---------------------------------------------------------------- Más

@Composable
fun MoreScreen(go: (String) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MoreItem(Icons.Filled.Notifications, stringResource(R.string.reminders)) { go("reminders") }
        MoreItem(Icons.Filled.DirectionsCar, stringResource(R.string.vehicles)) { go("vehicles") }
        MoreItem(Icons.Filled.Settings, stringResource(R.string.settings)) { go("settings") }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card2(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

// ---------------------------------------------------------------- Recordatorios

@Composable
fun RemindersScreen(vm: AppViewModel) {
    val v by vm.current.collectAsState()
    val s by vm.settings.collectAsState()
    val rem by vm.reminders.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    val veh = v
    var dialog by remember { mutableStateOf<Reminder?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (veh == null) {
        EmptyState(Icons.Filled.Notifications, stringResource(R.string.need_vehicle_first))
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rem.isEmpty()) {
                item { Text(stringResource(R.string.no_reminders), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(rem, key = { it.id }) { r ->
                val (text, over) = dueText(r, veh, fmt)
                Card2(Modifier.fillMaxWidth(), onClick = { dialog = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.Bold)
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!r.done) {
                            IconButton(onClick = { vm.completeReminder(r) }) {
                                Icon(Icons.Filled.Check, stringResource(R.string.mark_done), tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Text(stringResource(R.string.done), color = MaterialTheme.colorScheme.outline)
                        }
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
        ReminderDialog(null, veh, fmt, { adding = false }, { vm.saveReminder(it); adding = false }, null)
    }
    val d = dialog
    if (d != null) {
        ReminderDialog(d, veh, fmt, { dialog = null }, { vm.saveReminder(it); dialog = null }, { vm.deleteReminder(d); dialog = null })
    }
}

@Composable
private fun ReminderDialog(
    initial: Reminder?,
    vehicle: Vehicle,
    fmt: Fmt,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableLongStateOf(initial?.dueDate ?: 0L) }
    var dueKm by remember {
        mutableStateOf(if (initial != null && initial.dueOdometerKm > 0) fmt.num(fmt.distVal(initial.dueOdometerKm), 0) else "")
    }
    var repKm by remember {
        mutableStateOf(if (initial != null && initial.repeatKm > 0) fmt.num(fmt.distVal(initial.repeatKm), 0) else "")
    }
    var repMonths by remember { mutableStateOf(if (initial != null && initial.repeatMonths > 0) "${initial.repeatMonths}" else "") }

    FormDialog(
        title = stringResource(R.string.reminder),
        onDismiss = onDismiss,
        saveEnabled = title.isNotBlank(),
        onDelete = onDelete,
        onSave = {
            val base = initial ?: Reminder(vehicleId = vehicle.id)
            onSave(
                base.copy(
                    id = initial?.id ?: newId(),
                    vehicleId = vehicle.id,
                    title = title.trim(),
                    dueDate = date,
                    dueOdometerKm = if (dueKm.isBlank()) 0.0 else fmt.kmFromInput(dueKm.toNum()),
                    repeatKm = if (repKm.isBlank()) 0.0 else fmt.kmFromInput(repKm.toNum()),
                    repeatMonths = repMonths.toNum().toInt(),
                    notifiedAt = 0L,
                )
            )
        },
    ) {
        TextF(title, { title = it }, stringResource(R.string.title_hint))
        DateField(stringResource(R.string.due_date), date, fmt) { date = it }
        if (date > 0) {
            OutlinedButton(onClick = { date = 0L }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.clear_date))
            }
        }
        NumField(dueKm, { dueKm = it }, "${stringResource(R.string.due_odometer)} (${fmt.distUnit})")
        NumField(repKm, { repKm = it }, "${stringResource(R.string.repeat_every)} (${fmt.distUnit})")
        NumField(repMonths, { repMonths = it }, stringResource(R.string.repeat_months))
    }
}

// ---------------------------------------------------------------- Vehículos

@Composable
fun VehiclesScreen(vm: AppViewModel, host: Host) {
    val vehicles by vm.vehicles.collectAsState()
    val s by vm.settings.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    var dialog by remember { mutableStateOf<Vehicle?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vehicles.isEmpty()) {
                item { Text(stringResource(R.string.no_vehicles), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(vehicles, key = { it.id }) { v ->
                Card2(Modifier.fillMaxWidth(), onClick = { dialog = v }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (v.type == "MOTORCYCLE") Icons.Filled.TwoWheeler else Icons.Filled.DirectionsCar,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontWeight = FontWeight.Bold)
                            Text(
                                listOf(v.make, v.model, v.plate).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (v.btAddress.isNotBlank()) {
                            Icon(Icons.Filled.Bluetooth, null, tint = MaterialTheme.colorScheme.tertiary)
                        }
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
        VehicleDialog(null, fmt, host, { adding = false }, { vm.saveVehicle(it); adding = false }, null)
    }
    val d = dialog
    if (d != null) {
        VehicleDialog(d, fmt, host, { dialog = null }, { vm.saveVehicle(it); dialog = null }, { vm.deleteVehicle(d); dialog = null })
    }
}

@SuppressLint("MissingPermission")
private fun pairedDevices(ctx: Context): List<Pair<String, String>> = try {
    val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    adapter?.bondedDevices?.map { Pair(it.name ?: it.address, it.address) } ?: emptyList()
} catch (e: Exception) {
    emptyList()
}

@Composable
private fun VehicleDialog(
    initial: Vehicle?,
    fmt: Fmt,
    host: Host,
    onDismiss: () -> Unit,
    onSave: (Vehicle) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "CAR") }
    var make by remember { mutableStateOf(initial?.make ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var year by remember { mutableStateOf(if ((initial?.year ?: 0) > 0) "${initial?.year}" else "") }
    var plate by remember { mutableStateOf(initial?.plate ?: "") }
    var tank by remember { mutableStateOf(if ((initial?.tankL ?: 0.0) > 0) fmt.num(fmt.volVal(initial!!.tankL), 1) else "") }
    var odo by remember { mutableStateOf(if (initial != null) fmt.num(fmt.distVal(initial.odometerKm), 1) else "") }
    var level by remember { mutableStateOf(if ((initial?.fuelLevelL ?: 0.0) > 0) fmt.num(fmt.volVal(initial!!.fuelLevelL), 1) else "") }
    var base by remember { mutableStateOf(if ((initial?.baseConsumption ?: 0.0) > 0) fmt.num(fmt.consVal(initial!!.baseConsumption), 1) else "") }
    var btAddr by remember { mutableStateOf(initial?.btAddress ?: "") }
    var btName by remember { mutableStateOf(initial?.btName ?: "") }
    var menu by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = host.permTick

    FormDialog(
        title = stringResource(R.string.vehicle),
        onDismiss = onDismiss,
        saveEnabled = name.isNotBlank(),
        onDelete = onDelete,
        onSave = {
            val tankL = fmt.litersFromInput(tank.toNum())
            val lvl = fmt.litersFromInput(level.toNum())
            val baseL100 = if (base.isBlank()) 0.0 else toL100(base.toNum(), fmt)
            val b = initial ?: Vehicle()
            onSave(
                b.copy(
                    name = name.trim(),
                    type = type,
                    make = make.trim(),
                    model = model.trim(),
                    year = year.toNum().toInt(),
                    plate = plate.trim(),
                    tankL = tankL,
                    odometerKm = fmt.kmFromInput(odo.toNum()),
                    fuelLevelL = if (tankL > 0) minOf(lvl, tankL) else lvl,
                    baseConsumption = baseL100,
                    btAddress = btAddr,
                    btName = btName,
                )
            )
        },
    ) {
        TextF(name, { name = it }, stringResource(R.string.name))
        Choice(
            listOf("CAR" to stringResource(R.string.car), "MOTORCYCLE" to stringResource(R.string.motorcycle)),
            type,
        ) { type = it }
        TextF(make, { make = it }, stringResource(R.string.make))
        TextF(model, { model = it }, stringResource(R.string.model))
        NumField(year, { year = it }, stringResource(R.string.year))
        TextF(plate, { plate = it }, stringResource(R.string.plate))
        NumField(tank, { tank = it }, "${stringResource(R.string.tank_capacity)} (${fmt.volUnit})")
        NumField(odo, { odo = it }, "${stringResource(R.string.odometer)} (${fmt.distUnit})")
        NumField(level, { level = it }, "${stringResource(R.string.current_fuel)} (${fmt.volUnit})")
        NumField(base, { base = it }, "${stringResource(R.string.base_consumption)} (${fmt.consUnit})")

        Box {
            OutlinedButton(
                onClick = {
                    if (!Perms.bt(ctx)) host.requestBase() else menu = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Bluetooth, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (btAddr.isBlank()) stringResource(R.string.link_bluetooth) else btName.ifBlank { btAddr },
                    Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.none)) },
                    onClick = {
                        btAddr = ""
                        btName = ""
                        menu = false
                    },
                )
                pairedDevices(ctx).forEach { (n, a) ->
                    DropdownMenuItem(
                        text = { Text(n) },
                        onClick = {
                            btAddr = a
                            btName = n
                            menu = false
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.bt_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Convierte lo escrito en la unidad de consumo elegida a L/100 km. */
private fun toL100(v: Double, fmt: Fmt): Double {
    if (v <= 0) return 0.0
    return when (fmt.consUnit) {
        "km/L" -> 100.0 / v
        "mpg" -> 235.214583 / v
        else -> v
    }
}
