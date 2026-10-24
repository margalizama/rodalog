@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.rodalog.R
import app.rodalog.sync.SyncStatus

/** Acciones que dependen de la Activity (permisos, login de Google, compartir...). */
data class Host(
    val connectGoogle: () -> Unit,
    val requestBase: () -> Unit,
    val requestBackground: () -> Unit,
    val requestBattery: () -> Unit,
    val openUrl: (String) -> Unit,
    val share: (String) -> Unit,
    val permTick: Int,
)

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun RodaRoot(vm: AppViewModel, host: Host) {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route ?: "home"
    val tabs = remember {
        listOf(
            Tab("home", R.string.tab_home, Icons.Filled.Home),
            Tab("trips", R.string.tab_trips, Icons.Filled.Speed),
            Tab("fuel", R.string.tab_fuel, Icons.Filled.LocalGasStation),
            Tab("expenses", R.string.tab_expenses, Icons.Filled.Receipt),
            Tab("more", R.string.tab_more, Icons.Filled.MoreHoriz),
        )
    }
    val isTab = tabs.any { it.route == route }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.message.collect { snack.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.refreshAuth()
        vm.syncNow()
    }

    fun goTab(r: String) {
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            RodaTopBar(
                vm = vm,
                route = route,
                isTab = isTab,
                onBack = { nav.popBackStack() },
                onAddVehicle = { nav.navigate("vehicles") },
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { goTab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.labelRes)) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm) { r -> if (r == "vehicles") nav.navigate(r) else goTab(r) } }
            composable("trips") { TripsScreen(vm, host) }
            composable("fuel") { FuelScreen(vm) }
            composable("expenses") { ExpensesScreen(vm) }
            composable("more") { MoreScreen { r -> nav.navigate(r) } }
            composable("reminders") { RemindersScreen(vm) }
            composable("vehicles") { VehiclesScreen(vm, host) }
            composable("settings") { SettingsScreen(vm, host) }
        }
    }
}

@Composable
private fun RodaTopBar(
    vm: AppViewModel,
    route: String,
    isTab: Boolean,
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
) {
    val vehicles by vm.vehicles.collectAsState()
    val cur by vm.current.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    var open by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isTab && route != "more") {
                Box {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { open = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (cur?.type == "MOTORCYCLE") Icons.Filled.TwoWheeler else Icons.Filled.DirectionsCar,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cur?.name ?: stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        vehicles.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.name) },
                                onClick = {
                                    vm.select(v.id)
                                    open = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_vehicle)) },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            onClick = {
                                open = false
                                onAddVehicle()
                            },
                        )
                    }
                }
            } else {
                Text(
                    stringResource(
                        when (route) {
                            "reminders" -> R.string.reminders
                            "vehicles" -> R.string.vehicles
                            "settings" -> R.string.settings
                            else -> R.string.app_name
                        }
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        navigationIcon = {
            if (!isTab) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
        },
        actions = {
            IconButton(onClick = { vm.syncNow() }) {
                when (sync) {
                    is SyncStatus.Running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    is SyncStatus.Ok -> Icon(Icons.Filled.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                    is SyncStatus.Idle -> Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.outline)
                    else -> Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
