package app.rodalog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import app.rodalog.R
import app.rodalog.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------- Entidad

/**
 * Lugar frecuente. Mientras `confirmed` es false sólo es un candidato local que va sumando
 * visitas; al llegar al mínimo se confirma, se etiqueta sola y se sincroniza con la hoja.
 */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey override val id: String = newId(),
    val name: String = "",          // nombre personalizado (vacío = automático)
    val kind: String = "OTHER",     // HOME | WORK | GAS | SHOP | OTHER
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val visits: Int = 0,
    val morning: Int = 0,           // llegadas entre semana de 6 a 11
    val evening: Int = 0,           // llegadas de 17 a 6
    val confirmed: Boolean = false,
    val lookedUp: Boolean = false,  // ya se consultó OpenStreetMap
    val osmName: String = "",
    val address: String = "",
    val lastVisit: Long = 0L,
    override val updatedAt: Long = System.currentTimeMillis(),
    override val deleted: Boolean = false,
    override val dirty: Boolean = false,
) : SheetRecord {
    override fun toRow(): List<Any> = listOf(
        id, name, kind, lat, lon, visits, osmName, address, updatedAt, if (deleted) 1 else 0
    )

    companion object {
        const val TAB = "Lugares"
        val HEADERS = listOf(
            "id", "nombre", "tipo", "lat", "lon", "visitas", "nombre_osm", "direccion",
            "actualizado_ms", "borrado"
        )
        fun fromRow(r: List<Any?>) = Place(
            id = r.cell(0).str(), name = r.cell(1).str(), kind = r.cell(2).str().ifBlank { "OTHER" },
            lat = r.cell(3).dbl(), lon = r.cell(4).dbl(), visits = r.cell(5).lng().toInt(),
            osmName = r.cell(6).str(), address = r.cell(7).str(),
            confirmed = true, lookedUp = r.cell(6).str().isNotBlank() || r.cell(7).str().isNotBlank(),
            updatedAt = r.cell(8).lng(), deleted = r.cell(9).bool(), dirty = false
        )
    }
}

/** Nombre que se muestra: el personalizado o uno automático según el tipo. */
fun Place.label(ctx: Context): String = when {
    name.isNotBlank() -> name
    kind == "HOME" -> ctx.getString(R.string.place_home)
    kind == "WORK" -> ctx.getString(R.string.place_work)
    kind == "GAS" ->
        if (osmName.isNotBlank()) ctx.getString(R.string.place_gas_named, osmName) else ctx.getString(R.string.place_gas)
    kind == "SHOP" -> if (osmName.isNotBlank()) osmName else ctx.getString(R.string.place_shop)
    osmName.isNotBlank() -> osmName
    address.isNotBlank() -> address
    else -> ctx.getString(R.string.place_other)
}

// ---------------------------------------------------------------- Base de datos (aparte, sin migraciones)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places")
    suspend fun all(): List<Place>

    @Query("SELECT * FROM places WHERE deleted = 0 ORDER BY confirmed DESC, visits DESC")
    fun observe(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE dirty = 1")
    suspend fun dirty(): List<Place>

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: Place)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(p: List<Place>)
}

@Database(entities = [Place::class], version = 1, exportSchema = false)
abstract class PlaceDb : RoomDatabase() {
    abstract fun places(): PlaceDao

    companion object {
        @Volatile private var instance: PlaceDb? = null
        fun get(ctx: Context): PlaceDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, PlaceDb::class.java, "rodalog_places.db")
                .build().also { instance = it }
        }
    }
}

// ---------------------------------------------------------------- Lógica

data class Arrival(val lat: Double, val lon: Double, val time: Long)

object PlaceManager {
    const val RADIUS_M = 120.0

    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val NON_PLACE = setOf(
        "parking", "parking_space", "parking_entrance", "bench", "waste_basket", "toilets",
        "bicycle_parking", "motorcycle_parking", "shelter", "bus_station"
    )
    private val SHOP_CATS = setOf("shop", "amenity", "tourism", "leisure", "office", "craft", "healthcare")

    /** Registra llegadas (final del viaje y paradas largas). Confirma lugares que alcanzan el mínimo de visitas. */
    suspend fun onArrivals(ctx: Context, arrivals: List<Arrival>) {
        if (arrivals.isEmpty()) return
        val app = ctx.applicationContext
        mutex.withLock {
            val dao = PlaceDb.get(app).places()
            val minVisits = Prefs(app).get().placeMinVisits
            var changed = false
            for (a in arrivals) {
                if (register(dao, a, minVisits)) changed = true
            }
            lookupPending(dao)
            if (changed) SyncWorker.enqueue(app)
        }
    }

    /** Primer arranque: reconstruye lugares con los viajes ya guardados. Luego reintenta consultas pendientes. */
    suspend fun maintenance(ctx: Context) {
        val app = ctx.applicationContext
        mutex.withLock {
            val dao = PlaceDb.get(app).places()
            if (dao.count() == 0) {
                val trips = RodaDb.get(app).trips().everything()
                    .filter { !it.deleted && it.route.isNotBlank() }
                    .sortedBy { it.startTime }
                if (trips.isNotEmpty()) {
                    val minVisits = Prefs(app).get().placeMinVisits
                    for (t in trips) {
                        val last = parseRoute(t.route).lastOrNull() ?: continue
                        register(dao, Arrival(last.lat, last.lon, t.endTime), minVisits)
                    }
                    SyncWorker.enqueue(app)
                }
            }
            lookupPending(dao)
        }
    }

    suspend fun retryLookups(ctx: Context) {
        val app = ctx.applicationContext
        mutex.withLock { lookupPending(PlaceDb.get(app).places()) }
    }

    private fun bucket(time: Long): Pair<Int, Int> {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        val h = c.get(Calendar.HOUR_OF_DAY)
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val weekday = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
        val morning = if (weekday && h in 6..10) 1 else 0
        val evening = if (h >= 17 || h < 6) 1 else 0
        return Pair(morning, evening)
    }

    /** Devuelve true si este registro confirmó un lugar nuevo. */
    private suspend fun register(dao: PlaceDao, a: Arrival, minVisits: Int): Boolean {
        val all = dao.all()
        var near: Place? = null
        var best = Double.MAX_VALUE
        for (p in all) {
            val d = distanceM(p.lat, p.lon, a.lat, a.lon)
            if (d <= RADIUS_M && d < best) {
                best = d
                near = p
            }
        }
        val (m, e) = bucket(a.time)
        val n = near
        if (n == null) {
            dao.upsert(Place(lat = a.lat, lon = a.lon, visits = 1, morning = m, evening = e, lastVisit = a.time))
            return false
        }
        if (n.deleted) return false // el usuario lo descartó: no se vuelve a crear

        val v = n.visits + 1
        var p = n.copy(
            visits = v,
            morning = n.morning + m,
            evening = n.evening + e,
            lastVisit = maxOf(n.lastVisit, a.time),
        )
        var confirmedNow = false
        if (!p.confirmed) {
            p = p.copy(
                lat = (n.lat * n.visits + a.lat) / v,
                lon = (n.lon * n.visits + a.lon) / v,
            )
            if (v >= minVisits) {
                p = confirm(p, all)
                confirmedNow = true
            }
        }
        dao.upsert(p)
        return confirmedNow
    }

    /** Casa = llegadas sobre todo de noche; trabajo = llegadas sobre todo entre semana en la mañana. */
    private fun confirm(p: Place, all: List<Place>): Place {
        val tot = p.visits.coerceAtLeast(1).toDouble()
        val hasHome = all.any { it.confirmed && !it.deleted && it.kind == "HOME" }
        val hasWork = all.any { it.confirmed && !it.deleted && it.kind == "WORK" }
        val kind = when {
            !hasHome && p.evening / tot >= 0.6 -> "HOME"
            !hasWork && p.morning / tot >= 0.6 -> "WORK"
            else -> "OTHER"
        }
        return p.copy(confirmed = true, kind = kind, updatedAt = System.currentTimeMillis(), dirty = true)
    }

    private suspend fun lookupPending(dao: PlaceDao) {
        val pending = dao.all().filter { it.confirmed && !it.deleted && !it.lookedUp }.take(5)
        var first = true
        for (p in pending) {
            if (!first) delay(1100) // política de uso de Nominatim: 1 consulta por segundo
            first = false
            val r = lookup(p) ?: continue
            dao.upsert(r)
        }
    }

    /** Consulta OpenStreetMap (Nominatim) para saber si es gasolinera, comercio y su dirección. */
    private suspend fun lookup(p: Place): Place? = withContext(Dispatchers.IO) {
        try {
            val lang = Locale.getDefault().toLanguageTag()
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${p.lat}&lon=${p.lon}" +
                "&zoom=18&addressdetails=1&accept-language=$lang"
            val req = Request.Builder().url(url).header("User-Agent", "RodaLog/1.2 (app.rodalog)").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                val cat = j.optString("category")
                val type = j.optString("type")
                val name = j.optString("name")
                val addr = j.optJSONObject("address")
                val road = addr?.optString("road") ?: ""
                val num = addr?.optString("house_number") ?: ""
                var address = listOf(road, num).filter { it.isNotBlank() }.joinToString(" ")
                if (address.isBlank()) {
                    address = j.optString("display_name").split(",").take(2).joinToString(",").trim()
                }
                val osmKind = when {
                    cat == "amenity" && type == "fuel" -> "GAS"
                    name.isNotBlank() && cat in SHOP_CATS && !(cat == "amenity" && type in NON_PLACE) -> "SHOP"
                    else -> "OTHER"
                }
                val kind = when {
                    osmKind == "GAS" -> "GAS"
                    osmKind == "SHOP" && p.kind == "HOME" -> "SHOP"
                    p.kind == "OTHER" -> osmKind
                    else -> p.kind
                }
                p.copy(
                    kind = kind,
                    osmName = name,
                    address = address,
                    lookedUp = true,
                    updatedAt = System.currentTimeMillis(),
                    dirty = true,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ Títulos de viaje

    private fun nearest(list: List<Place>, lat: Double, lon: Double): Place? {
        var best: Place? = null
        var bestD = Double.MAX_VALUE
        for (p in list) {
            val d = distanceM(p.lat, p.lon, lat, lon)
            if (d <= RADIUS_M && d < bestD) {
                bestD = d
                best = p
            }
        }
        return best
    }

    /** "Casa → Trabajo", "Viaje a Gasolinera Pemex"… o null si el viaje no toca ningún lugar guardado. */
    fun tripTitle(ctx: Context, trip: Trip, places: List<Place>): String? {
        val confirmed = places.filter { it.confirmed && !it.deleted }
        if (confirmed.isEmpty()) return null
        val pts = parseRoute(trip.route)
        if (pts.size < 2) return null
        val a = nearest(confirmed, pts.first().lat, pts.first().lon)
        val b = nearest(confirmed, pts.last().lat, pts.last().lon)
        return when {
            a != null && b != null ->
                if (a.id == b.id) ctx.getString(R.string.trip_title_loop, a.label(ctx))
                else ctx.getString(R.string.trip_title_route, a.label(ctx), b.label(ctx))
            b != null -> ctx.getString(R.string.trip_title_to, b.label(ctx))
            a != null -> ctx.getString(R.string.trip_title_from, a.label(ctx))
            else -> null
        }
    }
}
