package app.rodalog.trip

import android.content.Context
import app.rodalog.data.FuelEntry
import app.rodalog.data.PlaceManager
import app.rodalog.data.Repo
import app.rodalog.data.Vehicle
import app.rodalog.util.Fmt
import app.rodalog.util.Notifs
import kotlin.math.max
import kotlin.math.round

/**
 * Si el vehículo estuvo detenido varios minutos en una gasolinera se asume tanque lleno:
 * se registra la carga con los litros que faltaban (estimados) y se avisa para confirmarla.
 */
object AutoRefuel {
    const val MIN_STOP_MS = 2 * 60_000L

    /**
     * @param usedL combustible gastado en el viaje hasta ese momento
     * @return true si registró una carga (el nivel base queda en tanque + usedL)
     */
    suspend fun detect(ctx: Context, v: Vehicle, lat: Double, lon: Double, time: Long, odoKm: Double, usedL: Double): Boolean {
        val app = ctx.applicationContext
        val repo = Repo(app)
        val s = repo.prefs.get()
        if (!s.autoRefuel || v.tankL <= 0.0) return false

        val station = PlaceManager.gasStationAt(app, lat, lon) ?: return false

        val left = max(0.0, v.fuelLevelL - usedL)
        val liters = round((v.tankL - left) * 10.0) / 10.0
        if (liters < max(3.0, v.tankL * 0.10)) return false // casi lleno: no parece una carga

        val history = repo.db.fuel().listAsc(v.id)
        val last = history.maxByOrNull { it.date }
        if (last != null && kotlin.math.abs(time - last.date) < 30 * 60_000L) return false // ya registrada

        val price = history.filter { it.liters > 0 && it.totalCost > 0 }.maxByOrNull { it.date }?.pricePerLiter ?: 0.0
        val entry = FuelEntry(
            vehicleId = v.id,
            date = time,
            odometerKm = odoKm,
            liters = liters,
            totalCost = round(liters * price * 100.0) / 100.0,
            fullTank = true,
            station = station,
            notes = FuelEntry.AUTO_TAG,
        )
        repo.autoFuel(entry, v.tankL + usedL)
        Notifs.autoFuel(app, v.name, station, Fmt(s).vol(liters, 1))
        return true
    }
}
