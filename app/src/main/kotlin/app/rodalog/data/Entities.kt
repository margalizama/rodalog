package app.rodalog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ---------- Helpers de conversión hoja <-> objeto ----------

private val isoFmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

fun Long.toIso(): String = if (this <= 0L) "" else isoFmt.format(Date(this))
fun String.fromIso(): Long = try { isoFmt.parse(trim())?.time ?: 0L } catch (e: Exception) { 0L }

fun Any?.str(): String = when (this) {
    null -> ""
    is Double -> if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()
    else -> this.toString()
}
fun Any?.dbl(): Double = when (this) {
    is Number -> this.toDouble()
    is String -> this.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
    else -> 0.0
}
fun Any?.lng(): Long = when (this) {
    is Number -> this.toLong()
    is String -> this.trim().toDoubleOrNull()?.toLong() ?: 0L
    else -> 0L
}
fun Any?.bool(): Boolean = when (this) {
    is Boolean -> this
    is Number -> this.toInt() != 0
    is String -> this.trim().lowercase() in listOf("true", "1", "si", "sí", "yes")
    else -> false
}
fun List<Any?>.cell(i: Int): Any? = if (i < size) this[i] else null

fun newId(): String = UUID.randomUUID().toString()

/** Contrato común para sincronizar con Google Sheets. */
interface SheetRecord {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val dirty: Boolean
    fun toRow(): List<Any>
}

// ---------- Vehículo ----------

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey override val id: String = newId(),
    val name: String = "",
    val type: String = "CAR", // CAR | MOTORCYCLE
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val plate: String = "",
    val tankL: Double = 0.0,
    val odometerKm: Double = 0.0,
    val fuelLevelL: Double = 0.0,
    val baseConsumption: Double = 0.0, // L/100 km estimado inicial
    val btAddress: String = "",
    val btName: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SheetRecord {
    override fun toRow(): List<Any> = listOf(
        id, name, type, make, model, year, plate, tankL, odometerKm, fuelLevelL,
        baseConsumption, btAddress, btName, updatedAt, if (deleted) 1 else 0
    )

    companion object {
        const val TAB = "Vehiculos"
        val HEADERS = listOf(
            "id", "nombre", "tipo", "marca", "modelo", "anio", "placa", "tanque_L",
            "odometro_km", "nivel_L", "consumo_base_L100", "bt_mac", "bt_nombre", "actualizado_ms", "borrado"
        )
        fun fromRow(r: List<Any?>) = Vehicle(
            id = r.cell(0).str(), name = r.cell(1).str(), type = r.cell(2).str().ifBlank { "CAR" },
            make = r.cell(3).str(), model = r.cell(4).str(), year = r.cell(5).lng().toInt(),
            plate = r.cell(6).str(), tankL = r.cell(7).dbl(), odometerKm = r.cell(8).dbl(),
            fuelLevelL = r.cell(9).dbl(), baseConsumption = r.cell(10).dbl(),
            btAddress = r.cell(11).str(), btName = r.cell(12).str(),
            updatedAt = r.cell(13).lng(), deleted = r.cell(14).bool(), dirty = false
        )
    }
}

// ---------- Carga de combustible ----------

@Entity(tableName = "fuel")
data class FuelEntry(
    @PrimaryKey override val id: String = newId(),
    val vehicleId: String = "",
    val date: Long = System.currentTimeMillis(),
    val odometerKm: Double = 0.0,
    val liters: Double = 0.0,
    val totalCost: Double = 0.0,
    val fullTank: Boolean = true,
    val station: String = "",
    val notes: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SheetRecord {
    val pricePerLiter: Double get() = if (liters > 0) totalCost / liters else 0.0

    override fun toRow(): List<Any> = listOf(
        id, vehicleId, date.toIso(), odometerKm, liters, totalCost, if (fullTank) 1 else 0,
        station, notes, updatedAt, if (deleted) 1 else 0
    )

    companion object {
        const val TAB = "Combustible"
        val HEADERS = listOf(
            "id", "vehiculo_id", "fecha", "odometro_km", "litros", "costo_total",
            "tanque_lleno", "estacion", "notas", "actualizado_ms", "borrado"
        )
        fun fromRow(r: List<Any?>) = FuelEntry(
            id = r.cell(0).str(), vehicleId = r.cell(1).str(), date = r.cell(2).str().fromIso(),
            odometerKm = r.cell(3).dbl(), liters = r.cell(4).dbl(), totalCost = r.cell(5).dbl(),
            fullTank = r.cell(6).bool(), station = r.cell(7).str(), notes = r.cell(8).str(),
            updatedAt = r.cell(9).lng(), deleted = r.cell(10).bool(), dirty = false
        )
    }
}

// ---------- Gasto / servicio ----------

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey override val id: String = newId(),
    val vehicleId: String = "",
    val date: Long = System.currentTimeMillis(),
    val odometerKm: Double = 0.0,
    val category: String = "SERVICE", // SERVICE, REPAIR, TIRES, INSURANCE, TAX, PARKING, TOLL, FINE, WASH, OTHER
    val title: String = "",
    val cost: Double = 0.0,
    val notes: String = "",
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SheetRecord {
    override fun toRow(): List<Any> = listOf(
        id, vehicleId, date.toIso(), odometerKm, category, title, cost, notes, updatedAt, if (deleted) 1 else 0
    )

    companion object {
        const val TAB = "Gastos"
        val HEADERS = listOf(
            "id", "vehiculo_id", "fecha", "odometro_km", "categoria", "titulo", "costo",
            "notas", "actualizado_ms", "borrado"
        )
        val CATEGORIES = listOf("SERVICE", "REPAIR", "TIRES", "INSURANCE", "TAX", "PARKING", "TOLL", "FINE", "WASH", "OTHER")
        fun fromRow(r: List<Any?>) = Expense(
            id = r.cell(0).str(), vehicleId = r.cell(1).str(), date = r.cell(2).str().fromIso(),
            odometerKm = r.cell(3).dbl(), category = r.cell(4).str().ifBlank { "OTHER" },
            title = r.cell(5).str(), cost = r.cell(6).dbl(), notes = r.cell(7).str(),
            updatedAt = r.cell(8).lng(), deleted = r.cell(9).bool(), dirty = false
        )
    }
}

// ---------- Recordatorio ----------

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey override val id: String = newId(),
    val vehicleId: String = "",
    val title: String = "",
    val dueDate: Long = 0L,        // 0 = sin fecha
    val dueOdometerKm: Double = 0.0, // 0 = sin km
    val repeatKm: Double = 0.0,
    val repeatMonths: Int = 0,
    val done: Boolean = false,
    val notifiedAt: Long = 0L,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SheetRecord {
    override fun toRow(): List<Any> = listOf(
        id, vehicleId, title, dueDate.toIso(), dueOdometerKm, repeatKm, repeatMonths,
        if (done) 1 else 0, updatedAt, if (deleted) 1 else 0
    )

    companion object {
        const val TAB = "Recordatorios"
        val HEADERS = listOf(
            "id", "vehiculo_id", "titulo", "fecha_limite", "odometro_limite_km",
            "repetir_km", "repetir_meses", "hecho", "actualizado_ms", "borrado"
        )
        fun fromRow(r: List<Any?>) = Reminder(
            id = r.cell(0).str(), vehicleId = r.cell(1).str(), title = r.cell(2).str(),
            dueDate = r.cell(3).str().fromIso(), dueOdometerKm = r.cell(4).dbl(),
            repeatKm = r.cell(5).dbl(), repeatMonths = r.cell(6).lng().toInt(),
            done = r.cell(7).bool(), updatedAt = r.cell(8).lng(),
            deleted = r.cell(9).bool(), dirty = false
        )
    }
}

// ---------- Viaje ----------

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey override val id: String = newId(),
    val vehicleId: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val distanceKm: Double = 0.0,
    val movingSec: Long = 0L,
    val maxSpeedKmh: Double = 0.0,
    val fuelUsedL: Double = 0.0,
    val startOdometerKm: Double = 0.0,
    val endOdometerKm: Double = 0.0,
    val category: String = "PERSONAL", // PERSONAL | WORK
    val notes: String = "",
    val route: String = "", // "lat,lon;lat,lon" simplificado
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SheetRecord {
    val avgSpeedKmh: Double get() = if (movingSec > 0) distanceKm / (movingSec / 3600.0) else 0.0
    val consumptionL100: Double get() = if (distanceKm > 0.05) fuelUsedL / distanceKm * 100.0 else 0.0

    override fun toRow(): List<Any> = listOf(
        id, vehicleId, startTime.toIso(), endTime.toIso(), distanceKm, movingSec, maxSpeedKmh,
        fuelUsedL, startOdometerKm, endOdometerKm, category, notes, route, updatedAt, if (deleted) 1 else 0
    )

    companion object {
        const val TAB = "Viajes"
        val HEADERS = listOf(
            "id", "vehiculo_id", "inicio", "fin", "distancia_km", "tiempo_mov_s", "vel_max_kmh",
            "combustible_L", "odo_inicio_km", "odo_fin_km", "categoria", "notas", "ruta",
            "actualizado_ms", "borrado"
        )
        fun fromRow(r: List<Any?>) = Trip(
            id = r.cell(0).str(), vehicleId = r.cell(1).str(), startTime = r.cell(2).str().fromIso(),
            endTime = r.cell(3).str().fromIso(), distanceKm = r.cell(4).dbl(), movingSec = r.cell(5).lng(),
            maxSpeedKmh = r.cell(6).dbl(), fuelUsedL = r.cell(7).dbl(), startOdometerKm = r.cell(8).dbl(),
            endOdometerKm = r.cell(9).dbl(), category = r.cell(10).str().ifBlank { "PERSONAL" },
            notes = r.cell(11).str(), route = r.cell(12).str(),
            updatedAt = r.cell(13).lng(), deleted = r.cell(14).bool(), dirty = false
        )
    }
}
