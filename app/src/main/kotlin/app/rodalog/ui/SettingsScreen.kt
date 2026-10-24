@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rodalog.R
import app.rodalog.sync.SyncStatus
import app.rodalog.util.Fmt

@Composable
fun SettingsScreen(vm: AppViewModel, host: Host) {
    val s by vm.settings.collectAsState()
    val authed by vm.authed.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    val ctx = LocalContext.current
    val fmt = remember(s) { Fmt(s) }
    @Suppress("UNUSED_VARIABLE") val tick = host.permTick

    var currency by remember(s.currency) { mutableStateOf(s.currency) }
    var sheetInput by remember { mutableStateOf("") }
    val sheetUrl = "https://docs.google.com/spreadsheets/d/${s.sheetId}"

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ------------------------------------------------ Sincronización
        item {
            Card2(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.sync_section))
                Spacer(Modifier.height(6.dp))
                Text(
                    syncLabel(sync),
                    color = if (sync is SyncStatus.Error || sync is SyncStatus.NeedAuth) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.lastSync > 0) {
                    Text(
                        stringResource(R.string.last_sync, fmt.dateTime(s.lastSync)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = host.connectGoogle, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (authed) R.string.reconnect_google else R.string.connect_google))
                }
                if (s.sheetId.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { vm.createSheet() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.create_sheet))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextF(sheetInput, { sheetInput = it }, stringResource(R.string.sheet_link_hint))
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { vm.connectSheet(sheetInput) },
                        enabled = sheetInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.connect_sheet)) }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { host.openUrl(sheetUrl) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.open_sheet))
                        }
                        FilledTonalButton(onClick = { vm.syncNow() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sync_now))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { host.share(ctx.getString(R.string.share_text, sheetUrl)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.share_sheet)) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.share_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ------------------------------------------------ Unidades
        item {
            Card2(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.units))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.distance), style = MaterialTheme.typography.labelMedium)
                Choice(listOf("KM" to "km", "MI" to "mi"), s.distanceUnit) {
                    vm.setUnits(it, s.volumeUnit, s.consumptionUnit, currency)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.volume), style = MaterialTheme.typography.labelMedium)
                Choice(listOf("L" to "L", "GAL" to "gal"), s.volumeUnit) {
                    vm.setUnits(s.distanceUnit, it, s.consumptionUnit, currency)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.consumption), style = MaterialTheme.typography.labelMedium)
                Choice(listOf("L100" to "L/100 km", "KML" to "km/L", "MPG" to "mpg"), s.consumptionUnit) {
                    vm.setUnits(s.distanceUnit, s.volumeUnit, it, currency)
                }
                Spacer(Modifier.height(8.dp))
                TextF(currency, { currency = it }, stringResource(R.string.currency_symbol))
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { vm.setUnits(s.distanceUnit, s.volumeUnit, s.consumptionUnit, currency.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save)) }
            }
        }

        // ------------------------------------------------ Viajes automáticos
        item {
            Card2(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.auto_trips))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_trips_switch), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.auto_trips_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Switch(checked = s.autoTrips, onCheckedChange = { vm.setAutoTrips(it) })
                }
                Spacer(Modifier.height(10.dp))
                PermRow(stringResource(R.string.perm_location), Perms.fine(ctx), host.requestBase)
                PermRow(stringResource(R.string.perm_background), Perms.background(ctx), host.requestBackground)
                PermRow(stringResource(R.string.perm_bluetooth), Perms.bt(ctx), host.requestBase)
                PermRow(stringResource(R.string.perm_notifications), Perms.notif(ctx), host.requestBase)
                PermRow(stringResource(R.string.perm_battery), Perms.battery(ctx), host.requestBattery)
                if (Build.VERSION.SDK_INT >= 30) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.perm_background_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Filled.Check else Icons.Filled.Close,
            null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(label, Modifier.weight(1f))
        if (!granted) {
            androidx.compose.material3.TextButton(onClick = onFix) { Text(stringResource(R.string.grant)) }
        }
    }
}

@Composable
private fun syncLabel(st: SyncStatus): String = when (st) {
    is SyncStatus.Idle -> stringResource(R.string.sync_idle)
    is SyncStatus.Running -> stringResource(R.string.sync_running)
    is SyncStatus.Ok -> stringResource(R.string.sync_ok)
    is SyncStatus.NeedAuth -> stringResource(R.string.sync_need_auth)
    is SyncStatus.NoSheet -> stringResource(R.string.sync_no_sheet)
    is SyncStatus.Error -> st.message
}
