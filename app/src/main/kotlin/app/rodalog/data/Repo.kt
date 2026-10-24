package app.rodalog.data

import android.content.Context
import app.rodalog.sync.SyncWorker

/** Punto único para escribir datos: marca registros como pendientes y pide sincronizar. */
class Repo(private val ctx: Context) {
    val db = RodaDb.get(ctx)
    val prefs = Prefs(ctx)

    private fun touch() = System.currentTimeMillis()

    private fun sync() = SyncWorker.enqueue(ctx)

    // ----- Vehículos -----
    suspend fun saveVehicle(v: Vehicle) {
        db.vehicles().upsert(v.copy(updatedAt = touch(), dirty = true))
        sync()
    }

    suspend fun deleteVehicle(v: Vehicle) {
        db.vehicles().upsert(v.copy(deleted = true, updatedAt = touch(), dirty = true))
        sync()
    }

    // ----- Combustible -----
    suspend fun saveFuel(e: FuelEntry, isNew: Boolean) {
        db.fuel().upsert(e.copy(updatedAt = touch(), dirty = true))
        if (isNew) {
            val v = db.vehicles().get(e.vehicleId)
            if (v != null) {
                val newOdo = if (e.odometerKm > v.odometerKm) e.odometerKm else v.odometerKm
                val level = if (e.fullTank && v.tankL > 0) v.tankL
                else if (v.tankL > 0) minOf(v.tankL, v.fuelLevelL + e.liters)
                else v.fuelLevelL + e.liters
                db.vehicles().upsert(v.copy(odometerKm = newOdo, fuelLevelL = level, updatedAt = touch(), dirty = true))
            }
        }
        sync()
    }

    suspend fun deleteFuel(e: FuelEntry) {
        db.fuel().upsert(e.copy(deleted = true, updatedAt = touch(), dirty = true))
        sync()
    }

    // ----- Gastos -----
    suspend fun saveExpense(e: Expense, isNew: Boolean) {
        db.expenses().upsert(e.copy(updatedAt = touch(), dirty = true))
        if (isNew && e.odometerKm > 0) {
            val v = db.vehicles().get(e.vehicleId)
            if (v != null && e.odometerKm > v.odometerKm) {
                db.vehicles().upsert(v.copy(odometerKm = e.odometerKm, updatedAt = touch(), dirty = true))
            }
        }
        sync()
    }

    suspend fun deleteExpense(e: Expense) {
        db.expenses().upsert(e.copy(deleted = true, updatedAt = touch(), dirty = true))
        sync()
    }

    // ----- Recordatorios -----
    suspend fun saveReminder(r: Reminder) {
        db.reminders().upsert(r.copy(updatedAt = touch(), dirty = true))
        sync()
    }

    suspend fun deleteReminder(r: Reminder) {
        db.reminders().upsert(r.copy(deleted = true, updatedAt = touch(), dirty = true))
        sync()
    }

    /** Marca como hecho; si es repetitivo crea el siguiente. */
    suspend fun completeReminder(r: Reminder) {
        db.reminders().upsert(r.copy(done = true, updatedAt = touch(), dirty = true))
        if (r.repeatKm > 0 || r.repeatMonths > 0) {
            val v = db.vehicles().get(r.vehicleId)
            val nextDate = if (r.repeatMonths > 0) {
                val c = java.util.Calendar.getInstance()
                c.add(java.util.Calendar.MONTH, r.repeatMonths)
                c.timeInMillis
            } else 0L
            val nextOdo = if (r.repeatKm > 0 && v != null) v.odometerKm + r.repeatKm else 0.0
            db.reminders().upsert(
                r.copy(
                    id = newId(), done = false, dueDate = nextDate, dueOdometerKm = nextOdo,
                    notifiedAt = 0L, updatedAt = touch(), dirty = true
                )
            )
        }
        sync()
    }

    // ----- Viajes -----
    suspend fun saveTrip(t: Trip) {
        db.trips().upsert(t.copy(updatedAt = touch(), dirty = true))
        sync()
    }

    suspend fun deleteTrip(t: Trip) {
        db.trips().upsert(t.copy(deleted = true, updatedAt = touch(), dirty = true))
        sync()
    }

    /** Al terminar un viaje: guarda el viaje y actualiza odómetro y nivel de tanque. */
    suspend fun finishTrip(t: Trip) {
        db.trips().upsert(t.copy(updatedAt = touch(), dirty = true))
        val v = db.vehicles().get(t.vehicleId)
        if (v != null) {
            db.vehicles().upsert(
                v.copy(
                    odometerKm = v.odometerKm + t.distanceKm,
                    fuelLevelL = maxOf(0.0, v.fuelLevelL - t.fuelUsedL),
                    updatedAt = touch(), dirty = true
                )
            )
        }
        sync()
    }

    /** Consumo promedio actual (L/100km) de un vehículo. */
    suspend fun consumptionOf(v: Vehicle): Double =
        Stats.avgConsumption(db.fuel().listAsc(v.id), v.baseConsumption)
}
