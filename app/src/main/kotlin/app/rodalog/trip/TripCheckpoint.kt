package app.rodalog.trip

import android.content.Context
import app.rodalog.data.Arrival
import app.rodalog.data.PlaceManager
import app.rodalog.data.Repo
import app.rodalog.data.Trip
import app.rodalog.data.parseRoute
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Guarda el avance del viaje cada ~20 s. Si Android mata la app a mitad de camino,
 * el viaje se recupera y se guarda con lo que llevaba.
 */
object TripCheckpoint {
    private val lock = Mutex()
    private const val FILE = "trip_cp"

    data class Cp(
        val vehicleId: String,
        val startTime: Long,
        val startOdo: Double,
        val distanceM: Double,
        val movingSec: Long,
        val maxKmh: Double,
        val fuelUsedL: Double,
        val route: String,
        val stops: String,
        val savedAt: Long,
    )

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(ctx: Context, c: Cp) {
        sp(ctx).edit()
            .putString("vid", c.vehicleId)
            .putLong("start", c.startTime)
            .putString("odo", c.startOdo.toString())
            .putString("dist", c.distanceM.toString())
            .putLong("moving", c.movingSec)
            .putString("max", c.maxKmh.toString())
            .putString("fuel", c.fuelUsedL.toString())
            .putString("route", c.route)
            .putString("stops", c.stops)
            .putLong("saved", c.savedAt)
            .apply()
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }

    private fun read(ctx: Context): Cp? {
        val p = sp(ctx)
        val vid = p.getString("vid", "") ?: ""
        if (vid.isBlank()) return null
        return Cp(
            vehicleId = vid,
            startTime = p.getLong("start", 0L),
            startOdo = p.getString("odo", "0")?.toDoubleOrNull() ?: 0.0,
            distanceM = p.getString("dist", "0")?.toDoubleOrNull() ?: 0.0,
            movingSec = p.getLong("moving", 0L),
            maxKmh = p.getString("max", "0")?.toDoubleOrNull() ?: 0.0,
            fuelUsedL = p.getString("fuel", "0")?.toDoubleOrNull() ?: 0.0,
            route = p.getString("route", "") ?: "",
            stops = p.getString("stops", "") ?: "",
            savedAt = p.getLong("saved", 0L),
        )
    }

    /** "lat,lon,tiempo;lat,lon,tiempo" -> llegadas. */
    fun parseStops(s: String): List<Arrival> =
        s.split(";").mapNotNull {
            val p = it.split(",")
            if (p.size < 3) {
                null
            } else {
                val la = p[0].toDoubleOrNull()
                val lo = p[1].toDoubleOrNull()
                val t = p[2].toLongOrNull()
                if (la != null && lo != null && t != null) Arrival(la, lo, t) else null
            }
        }

    /**
     * Si quedó un viaje sin cerrar lo guarda como viaje terminado en el último punto registrado.
     * Devuelve true si recuperó algo.
     */
    suspend fun recover(ctx: Context, fromService: Boolean): Boolean {
        val app = ctx.applicationContext
        return lock.withLock {
            if (!fromService && TripService.running) return@withLock false
            val cp = read(app) ?: return@withLock false
            clear(app)
            val dist = cp.distanceM / 1000.0
            if (dist < 0.2) return@withLock false
            val trip = Trip(
                vehicleId = cp.vehicleId,
                startTime = cp.startTime,
                endTime = if (cp.savedAt > cp.startTime) cp.savedAt else System.currentTimeMillis(),
                distanceKm = dist,
                movingSec = cp.movingSec,
                maxSpeedKmh = cp.maxKmh,
                fuelUsedL = cp.fuelUsedL,
                startOdometerKm = cp.startOdo,
                endOdometerKm = cp.startOdo + dist,
                route = cp.route,
            )
            Repo(app).finishTrip(trip)
            val arrivals = ArrayList<Arrival>(parseStops(cp.stops))
            val last = parseRoute(cp.route).lastOrNull()
            if (last != null) arrivals.add(Arrival(last.lat, last.lon, trip.endTime))
            PlaceManager.onArrivals(app, arrivals)
            true
        }
    }
}
