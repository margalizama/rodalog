@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rodalog.R
import app.rodalog.data.Expense
import app.rodalog.data.Vehicle
import app.rodalog.data.newId
import app.rodalog.util.Fmt
import app.rodalog.util.toNum

@Composable
fun ExpensesScreen(vm: AppViewModel) {
    val v by vm.current.collectAsState()
    val s by vm.settings.collectAsState()
    val exp by vm.expenses.collectAsState()
    val fmt = remember(s) { Fmt(s) }
    val veh = v
    var dialog by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (veh == null) {
        EmptyState(Icons.Filled.Receipt, stringResource(R.string.need_vehicle_first))
        return
    }

    val total = exp.sumOf { it.cost }
    val byCat = remember(exp) {
        exp.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.cost } }.entries.sortedByDescending { it.value }
    }
    val maxCat = byCat.firstOrNull()?.value ?: 1.0

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card2(Modifier.fillMaxWidth()) {
                    StatTile(stringResource(R.string.total_spent), fmt.money(total), Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.secondary)
                    if (byCat.isNotEmpty()) Spacer(Modifier.height(10.dp))
                    byCat.take(5).forEach { entry ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(categoryLabel(entry.key), style = MaterialTheme.typography.bodyMedium)
                            Text(fmt.money(entry.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier
                                .padding(top = 3.dp, bottom = 8.dp)
                                .fillMaxWidth((entry.value / maxCat).toFloat().coerceIn(0.03f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
            if (exp.isEmpty()) {
                item { Text(stringResource(R.string.no_expenses), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(exp, key = { it.id }) { e ->
                Card2(Modifier.fillMaxWidth(), onClick = { dialog = e }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title.ifBlank { categoryLabel(e.category) }, fontWeight = FontWeight.Bold)
                            Text(
                                "${fmt.date(e.date)} · ${categoryLabel(e.category)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(fmt.money(e.cost), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
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
        ExpenseDialog(null, veh, fmt, { adding = false }, { vm.saveExpense(it, true); adding = false }, null)
    }
    val d = dialog
    if (d != null) {
        ExpenseDialog(d, veh, fmt, { dialog = null }, { vm.saveExpense(it, false); dialog = null }, { vm.deleteExpense(d); dialog = null })
    }
}

@Composable
private fun ExpenseDialog(
    initial: Expense?,
    vehicle: Vehicle,
    fmt: Fmt,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var date by remember { mutableLongStateOf(initial?.date ?: System.currentTimeMillis()) }
    var category by remember { mutableStateOf(initial?.category ?: "SERVICE") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var cost by remember { mutableStateOf(if (initial != null) fmt.num(initial.cost, 2) else "") }
    var odo by remember { mutableStateOf(fmt.num(fmt.distVal(initial?.odometerKm ?: vehicle.odometerKm), 1)) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var menu by remember { mutableStateOf(false) }

    FormDialog(
        title = stringResource(R.string.expense_entry),
        onDismiss = onDismiss,
        saveEnabled = cost.toNum() > 0,
        onDelete = onDelete,
        onSave = {
            onSave(
                Expense(
                    id = initial?.id ?: newId(),
                    vehicleId = vehicle.id,
                    date = date,
                    odometerKm = fmt.kmFromInput(odo.toNum()),
                    category = category,
                    title = title.trim(),
                    cost = cost.toNum(),
                    notes = notes.trim(),
                )
            )
        },
    ) {
        DateField(stringResource(R.string.date), date, fmt) { date = it }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(categoryLabel(category), Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Expense.CATEGORIES.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(categoryLabel(c)) },
                        onClick = {
                            category = c
                            menu = false
                        },
                    )
                }
            }
        }
        TextF(title, { title = it }, stringResource(R.string.description))
        NumField(cost, { cost = it }, stringResource(R.string.cost))
        NumField(odo, { odo = it }, "${stringResource(R.string.odometer)} (${fmt.distUnit})")
        TextF(notes, { notes = it }, stringResource(R.string.notes), single = false)
    }
}
