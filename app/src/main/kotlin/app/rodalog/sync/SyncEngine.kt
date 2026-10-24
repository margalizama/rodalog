package app.rodalog.sync

import android.content.Context
import app.rodalog.data.Expense
import app.rodalog.data.FuelEntry
import app.rodalog.data.Prefs
import app.rodalog.data.Reminder
import app.rodalog.data.RodaDb
import app.rodalog.data.SheetRecord
import app.rodalog.data.Trip
import app.rodalog.data.Vehicle
import app.rodalog.data.cell
import app.rodalog.data.newId
import app.rodalog.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data object Ok : SyncStatus
    data object NeedAuth : SyncStatus
    data object NoSheet : SyncStatus
    data class Error(val message: String) : SyncStatus
}

object SyncEngine {
    val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    private val lock = Mutex()

    private val TABS = listOf(Vehicle.TAB, FuelEntry.TAB, Expense.TAB, Reminder.TAB, Trip.TAB)

    private fun headersFor(tab: String): List<String> = when (tab) {
        Vehicle.TAB -> Vehicle.HEADERS
        FuelEntry.TAB -> FuelEntry.HEADERS
        Expense.TAB -> Expense.HEADERS
        Reminder.TAB -> Reminder.HEADERS
        else -> Trip.HEADERS
    }

    /** Extrae el ID de una URL de Google Sheets (o devuelve el texto si ya es un ID). */
    fun parseSheetId(input: String): String {
        val m = Regex("/d/([a-zA-Z0-9-_]+)").find(input)
        return m?.groupValues?.get(1) ?: input.trim()
    }

    /** Crea una hoja nueva con todas las pestañas y encabezados. */
    suspend fun createSheet(ctx: Context, title: String): String {
        val token = Auth.silentToken(ctx) ?: throw IllegalStateException("auth")
        val api = SheetsApi(token)
        val id = api.createSpreadsheet(title, TABS)
        api.batchUpdateValues(id, TABS.map { "$it!A1" to listOf(headersFor(it)) })
        Prefs(ctx).setSheet(id)
        return id
    }

    /** Conecta una hoja existente: crea las pestañas que falten. */
    suspend fun connectSheet(ctx: Context, input: String) {
        val token = Auth.silentToken(ctx) ?: throw IllegalStateException("auth")
        val api = SheetsApi(token)
        val id = parseSheetId(input)
        val existing = api.tabTitles(id)
        val missing = TABS.filter { it !in existing }
        api.addTabs(id, missing)
        api.batchUpdateValues(id, missing.map { "$it!A1" to listOf(headersFor(it)) })
        Prefs(ctx).setSheet(id)
    }

    suspend fun run(ctx: Context): SyncStatus = lock.withLock {
        val prefs = Prefs(ctx)
        val sheetId = prefs.get().sheetId
        if (sheetId.isBlank()) {
            status.value = SyncStatus.NoSheet
            return SyncStatus.NoSheet
        }
        val token = Auth.silentToken(ctx)
        if (token == null) {
            status.value = SyncStatus.NeedAuth
            return SyncStatus.NeedAuth
        }
        status.value = SyncStatus.Running
        try {
            val api = SheetsApi(token)
            val db = RodaDb.get(ctx)

            syncTable(
                api, sheetId, Vehicle.TAB, Vehicle.HEADERS,
                parse = { Vehicle.fromRow(it) },
                stamp = { r, t -> r.copy(updatedAt = t, dirty = true) },
                clean = { it.copy(dirty = false) },
                all = { db.vehicles().everything() }, dirty = { db.vehicles().dirty() },
                upsert = { db.vehicles().upsertAll(it) }
            )
            syncTable(
                api, sheetId, FuelEntry.TAB, FuelEntry.HEADERS,
                parse = { FuelEntry.fromRow(it) },
                stamp = { r, t -> r.copy(updatedAt = t, dirty = true) },
                clean = { it.copy(dirty = false) },
                all = { db.fuel().everything() }, dirty = { db.fuel().dirty() },
                upsert = { db.fuel().upsertAll(it) }
            )
            syncTable(
                api, sheetId, Expense.TAB, Expense.HEADERS,
                parse = { Expense.fromRow(it) },
                stamp = { r, t -> r.copy(updatedAt = t, dirty = true) },
                clean = { it.copy(dirty = false) },
                all = { db.expenses().everything() }, dirty = { db.expenses().dirty() },
                upsert = { db.expenses().upsertAll(it) }
            )
            syncTable(
                api, sheetId, Reminder.TAB, Reminder.HEADERS,
                parse = { Reminder.fromRow(it) },
                stamp = { r, t -> r.copy(updatedAt = t, dirty = true) },
                clean = { it.copy(dirty = false) },
                all = { db.reminders().everything() }, dirty = { db.reminders().dirty() },
                upsert = { db.reminders().upsertAll(it) }
            )
            syncTable(
                api, sheetId, Trip.TAB, Trip.HEADERS,
                parse = { Trip.fromRow(it) },
                stamp = { r, t -> r.copy(updatedAt = t, dirty = true) },
                clean = { it.copy(dirty = false) },
                all = { db.trips().everything() }, dirty = { db.trips().dirty() },
                upsert = { db.trips().upsertAll(it) }
            )

            prefs.setLastSync(System.currentTimeMillis())
            status.value = SyncStatus.Ok
            SyncStatus.Ok
        } catch (e: ApiException) {
            val s = if (e.code == 401 || e.code == 403) SyncStatus.NeedAuth else SyncStatus.Error(e.message ?: "error")
            status.value = s
            s
        } catch (e: Exception) {
            val s = SyncStatus.Error(e.message ?: e.javaClass.simpleName)
            status.value = s
            s
        }
    }

    /**
     * Sincroniza una pestaña: primero baja cambios de la hoja (gana el más reciente por
     * actualizado_ms; una fila sin actualizado_ms se considera edición manual y gana),
     * luego sube los registros locales pendientes.
     */
    private suspend fun <T : SheetRecord> syncTable(
        api: SheetsApi,
        sheetId: String,
        tab: String,
        headers: List<String>,
        parse: (List<Any?>) -> T,
        stamp: (T, Long) -> T,
        clean: (T) -> T,
        all: suspend () -> List<T>,
        dirty: suspend () -> List<T>,
        upsert: suspend (List<T>) -> Unit,
    ) {
        var rows = api.getValues(sheetId, tab)
        if (rows.isEmpty()) {
            api.updateRange(sheetId, "$tab!A1", listOf(headers))
            rows = listOf(headers)
        }

        val localMap = all().associateBy { it.id }
        val rowOf = HashMap<String, Int>() // id -> número de fila (1-based)
        val pulled = ArrayList<T>()
        val idWrites = ArrayList<Pair<String, List<List<Any?>>>>()
        val now = System.currentTimeMillis()

        for (i in 1 until rows.size) {
            val r = rows[i]
            if (r.all { it.str().isBlank() }) continue
            val rowNum = i + 1
            var id = r.cell(0).str().trim()
            var cells = r
            var assigned = false
            if (id.isBlank()) {
                id = newId()
                assigned = true
                val m = r.toMutableList()
                if (m.isEmpty()) m.add(id) else m[0] = id
                cells = m
                idWrites.add("$tab!A$rowNum" to listOf(listOf<Any?>(id)))
            }
            rowOf[id] = rowNum
            val parsed = parse(cells)
            val local = localMap[id]
            when {
                assigned || parsed.updatedAt == 0L -> pulled.add(stamp(parsed, now))
                local == null -> pulled.add(parsed)
                parsed.updatedAt > local.updatedAt -> pulled.add(parsed)
            }
        }
        api.batchUpdateValues(sheetId, idWrites)
        if (pulled.isNotEmpty()) upsert(pulled)

        // Subir pendientes
        val pending = dirty()
        if (pending.isEmpty()) return
        val updates = ArrayList<Pair<String, List<List<Any?>>>>()
        val appends = ArrayList<List<Any?>>()
        for (rec in pending) {
            val rn = rowOf[rec.id]
            if (rn != null) updates.add("$tab!A$rn" to listOf(rec.toRow())) else appends.add(rec.toRow())
        }
        api.batchUpdateValues(sheetId, updates)
        api.append(sheetId, tab, appends)

        // Marcar como limpios sólo los que no cambiaron mientras se sincronizaba
        val fresh = all().associateBy { it.id }
        val cleaned = pending.mapNotNull { p ->
            val f = fresh[p.id]
            if (f != null && f.updatedAt == p.updatedAt) clean(f) else null
        }
        if (cleaned.isNotEmpty()) upsert(cleaned)
    }
}
