package app.rodalog.trip

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.rodalog.MainActivity
import app.rodalog.R
import app.rodalog.data.Arrival
import app.rodalog.data.ConsumptionModel
import app.rodalog.data.PlaceManager
import app.rodalog.data.Repo
import app.rodalog.data.Settings
import app.rodalog.data.Trip
import app.rodalog.data.Vehicle
import app.rodalog.util.Fmt
import app.rodalog.util.Notifs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class Phase { IDLE, ARMED, RECORDING }

/** Datos en vivo de la computadora de viaje. */
data class TripLive(
    val phase: Phase = Phase.IDLE,
    val vehicleName: String = "",
    val speedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val elapsedSec: Long = 0L,
    val movingSec: Long = 0L,
    val maxSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val consumptionL100: Double = 0.0,   // promedio del viaje
    val instantL100: Double = 0.0,       // estimado a la velocidad actual
    val fuelUsedL: Double = 0.0,
    val fuelLeftL: Double = 0.0,
    val tankL: Double = 0.0,
    val rangeKm: Double = 0.0,
    val odometerKm: Double = 0.0,
    val accuracyM: Float = -1f,
)

object TripBus {
    val state = MutableStateFlow(TripLive())
}

/**
 * Servicio en primer plano que registra viajes con GPS.
 *  - ARM: lo dispara el Bluetooth del vehículo; queda "armado" y empieza a grabar al detectar movimiento.
 *  - START: inicio manual, graba de inmediato.
 * Termina al perder el Bluetooth (con gracia de 2 min), tras 10 min detenido, o con el botón.
 * Guarda un checkpoint cada ~20 s para recuperar el viaje si el sistema mata la app.
 */
class TripService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var repo: Repo

    private var vehicle: Vehicle? = null
    private var fmt = Fmt(Settings())
    private var consL100 = 8.0
    private var norm = 1.1
    private var phase = Phase.IDLE
    private var starting = false
    private var finishing = false

    private var startTime = 0L
    private var startOdo = 0.0
    private var distanceM = 0.0
    private var fuelUsedL = 0.0
    private var movingSec = 0L
    private var maxKmh = 0.0
    private var lastKmh = 0.0
    private var lastFixAt = 0L
    private var lastMovingAt = 0L
    private var armedAt = 0L
    private var btLostAt = 0L
    private var lastNotifAt = 0L
    private var lastCpAt = 0L
    private var accuracy = -1f
    private var lastLoc: Location? = null
    private var lastRouteLoc: Location? = null
    private val route = ArrayList<String>()
    private var routeStep = 1
    private var ticker: Job? = null

    // paradas largas (gasolineras, tiendas...) para detectar lugares frecuentes
    private var stopSince = 0L
    private var stopLat = 0.0
    private var stopLon = 0.0
    private val stops = ArrayList<String>()

    // alerta de reserva
    private var alertOn = true
    private var lowPct = 15
    private var lowAlerted = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (l in result.locations) onLocation(l)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Notifs.createChannels(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        repo = Repo(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (phase == Phase.IDLE && !starting) stopSelf() else finish()
            }
            ACTION_BT_LOST -> {
                if (phase == Phase.IDLE && !starting) stopSelf() else btLostAt = System.currentTimeMillis()
            }
            else -> {
                if (!goForeground()) {
                    stopSelf()
                } else {
                    val vid = intent?.getStringExtra(EXTRA_VEHICLE) ?: ""
                    val manual = intent?.action == ACTION_START
                    scope.launch { begin(vid, manual) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun goForeground(): Boolean = try {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
        true
    } catch (e: Exception) {
        false
    }

    private suspend fun begin(vid: String, manual: Boolean) {
        if (finishing || starting) return
        if (phase != Phase.IDLE) {
            btLostAt = 0L
            if (manual && phase == Phase.ARMED) startRecording(null)
            return
        }
        starting = true
        try {
            // Si quedó un viaje sin cerrar (la app fue cerrada por el sistema) se guarda primero
            TripCheckpoint.recover(applicationContext, true)

            val byId = if (vid.isNotBlank()) repo.db.vehicles().get(vid) else null
            val v = byId ?: repo.db.vehicles().getAll().firstOrNull()
            if (v == null || !hasLocationPermission()) {
                starting = false
                stopSelf()
                return
            }
            vehicle = v
            val s = repo.prefs.get()
            fmt = Fmt(s)
            alertOn = s.lowFuelAlert
            lowPct = s.lowFuelPct
            lowAlerted = false
            consL100 = repo.consumptionOf(v)
            norm = ConsumptionModel.normFrom(repo.db.trips().recent(v.id, 30))
            startOdo = v.odometerKm
            resetCounters()
            btLostAt = 0L
            armedAt = System.currentTimeMillis()
            if (manual) {
                phase = Phase.RECORDING
                startTime = armedAt
                lastMovingAt = armedAt
            } else {
                phase = Phase.ARMED
            }
            requestLocation(if (manual) 1000L else 3000L)
            ticker = scope.launch {
                while (isActive) {
                    delay(1000)
                    tick()
                }
            }
            publish()
            updateNotification()
        } finally {
            starting = false
        }
    }

    private fun resetCounters() {
        distanceM = 0.0
        fuelUsedL = 0.0
        movingSec = 0L
        maxKmh = 0.0
        lastKmh = 0.0
        lastLoc = null
        lastRouteLoc = null
        route.clear()
        routeStep = 1
        stops.clear()
        stopSince = 0L
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation(intervalMs: Long) {
        fused.removeLocationUpdates(callback)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
    }

    private fun startRecording(first: Location?) {
        resetCounters()
        phase = Phase.RECORDING
        startTime = System.currentTimeMillis()
        lastMovingAt = startTime
        lastLoc = first
        if (first != null) addRoute(first, force = true)
        requestLocation(1000L)
        updateNotification()
    }

    private fun onLocation(l: Location) {
        accuracy = if (l.hasAccuracy()) l.accuracy else -1f
        if (l.hasAccuracy() && l.accuracy > 35f) {
            publish()
            return
        }
        val now = System.currentTimeMillis()
        val prev = lastLoc
        var kmh: Double = if (l.hasSpeed()) {
            l.speed * 3.6
        } else if (prev != null) {
            val dt = (l.time - prev.time) / 1000.0
            if (dt > 0) prev.distanceTo(l) / dt * 3.6 else 0.0
        } else {
            0.0
        }
        if (kmh < 0.0 || kmh > 300.0) kmh = 0.0
        lastKmh = kmh
        lastFixAt = now

        when (phase) {
            Phase.ARMED -> {
                if (kmh >= START_KMH) startRecording(l) else lastLoc = l
            }
            Phase.RECORDING -> {
                if (prev != null && kmh >= MOVING_KMH) {
                    val d = prev.distanceTo(l).toDouble()
                    if (d >= 2.0 && d <= 800.0) {
                        distanceM += d
                        // consumo estimado según la velocidad de este tramo
                        fuelUsedL += d / 1000.0 * consL100 / 100.0 * ConsumptionModel.factor(kmh) / norm
                    }
                }
                if (kmh >= MOVING_KMH) lastMovingAt = now
                if (kmh > maxKmh) maxKmh = kmh
                trackStop(l, kmh, now)
                addRoute(l)
                lastLoc = l
            }
            Phase.IDLE -> {}
        }
        publish()
    }

    /** Una parada de 3 min o más cuenta como visita al lugar (gasolinera, tienda, casa...). */
    private fun trackStop(l: Location, kmh: Double, now: Long) {
        if (kmh < MOVING_KMH) {
            if (stopSince == 0L) {
                stopSince = now
                stopLat = l.latitude
                stopLon = l.longitude
            }
        } else {
            if (stopSince > 0L && now - stopSince >= STOP_VISIT_MS) {
                stops.add(String.format(Locale.US, "%.5f,%.5f,%d", stopLat, stopLon, stopSince))
            }
            stopSince = 0L
        }
    }

    private fun addRoute(l: Location, force: Boolean = false) {
        val last = lastRouteLoc
        if (force || last == null || last.distanceTo(l) >= 40f * routeStep) {
            route.add(String.format(Locale.US, "%.5f,%.5f,%d", l.latitude, l.longitude, lastKmh.toInt()))
            lastRouteLoc = l
            if (route.size > 1200) {
                val thinned = route.filterIndexed { i, _ -> i % 2 == 0 }
                route.clear()
                route.addAll(thinned)
                routeStep *= 2
            }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (phase == Phase.RECORDING) {
            if (now - lastFixAt < 5000 && lastKmh >= MOVING_KMH) movingSec++
            if (now - lastMovingAt > STOP_IDLE_MS) {
                finish()
                return
            }
            if (now - lastCpAt > CHECKPOINT_MS) saveCheckpoint(now)
            checkLowFuel()
        }
        if (phase == Phase.ARMED && now - armedAt > ARMED_TIMEOUT_MS) {
            finish()
            return
        }
        if (btLostAt > 0L && now - btLostAt > BT_GRACE_MS) {
            finish()
            return
        }
        publish()
        if (now - lastNotifAt > 5000) updateNotification()
    }

    private fun saveCheckpoint(now: Long) {
        val v = vehicle ?: return
        lastCpAt = now
        TripCheckpoint.save(
            this,
            TripCheckpoint.Cp(
                vehicleId = v.id,
                startTime = startTime,
                startOdo = startOdo,
                distanceM = distanceM,
                movingSec = movingSec,
                maxKmh = maxKmh,
                fuelUsedL = fuelUsedL,
                route = route.joinToString(";"),
                stops = stops.joinToString(";"),
                savedAt = now,
            )
        )
    }

    private fun checkLowFuel() {
        if (lowAlerted || !alertOn) return
        val v = vehicle ?: return
        if (v.tankL <= 0.0) return
        val left = maxOf(0.0, v.fuelLevelL - fuelUsedL)
        val pct = left / v.tankL * 100.0
        if (pct <= lowPct) {
            lowAlerted = true
            val range = if (consL100 > 0) left / consL100 * 100.0 else 0.0
            Notifs.lowFuel(this, v.id, v.name, pct.toInt(), fmt.dist(range, 0))
        }
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        ticker?.cancel()
        fused.removeLocationUpdates(callback)
        val v = vehicle
        val dist = distanceM / 1000.0
        val recording = phase == Phase.RECORDING
        val endLoc = lastLoc
        if (recording && endLoc != null) addRoute(endLoc, force = true) // el último punto exacto
        val endTime = System.currentTimeMillis()
        val trip = if (recording && v != null && dist >= 0.2) {
            Trip(
                vehicleId = v.id,
                startTime = startTime,
                endTime = endTime,
                distanceKm = dist,
                movingSec = movingSec,
                maxSpeedKmh = maxKmh,
                fuelUsedL = fuelUsedL,
                startOdometerKm = startOdo,
                endOdometerKm = startOdo + dist,
                route = route.joinToString(";"),
            )
        } else null
        val stopList = TripCheckpoint.parseStops(stops.joinToString(";"))
        phase = Phase.IDLE
        scope.launch {
            try {
                if (trip != null) {
                    repo.finishTrip(trip)
                    val arrivals = ArrayList<Arrival>(stopList)
                    if (endLoc != null) arrivals.add(Arrival(endLoc.latitude, endLoc.longitude, endTime))
                    PlaceManager.onArrivals(applicationContext, arrivals)
                }
            } catch (e: Exception) {
            } finally {
                TripCheckpoint.clear(applicationContext)
                TripBus.state.value = TripLive()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun publish() {
        val v = vehicle ?: return
        val now = System.currentTimeMillis()
        val dist = distanceM / 1000.0
        val left = maxOf(0.0, v.fuelLevelL - fuelUsedL)
        val fresh = now - lastFixAt < 5000
        val speed = if (fresh) lastKmh else 0.0
        TripBus.state.value = TripLive(
            phase = phase,
            vehicleName = v.name,
            speedKmh = speed,
            distanceKm = dist,
            elapsedSec = if (phase == Phase.RECORDING) (now - startTime) / 1000 else 0L,
            movingSec = movingSec,
            maxSpeedKmh = maxKmh,
            avgSpeedKmh = if (movingSec > 0) dist / (movingSec / 3600.0) else 0.0,
            consumptionL100 = if (dist >= 0.1) fuelUsedL / dist * 100.0 else consL100,
            instantL100 = if (speed >= MOVING_KMH) consL100 * ConsumptionModel.factor(speed) / norm else 0.0,
            fuelUsedL = fuelUsedL,
            fuelLeftL = left,
            tankL = v.tankL,
            rangeKm = if (consL100 > 0) left / consL100 * 100.0 else 0.0,
            odometerKm = startOdo + dist,
            accuracyM = accuracy,
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val st = TripBus.state.value
        val text = when (phase) {
            Phase.ARMED -> getString(R.string.notif_waiting)
            Phase.RECORDING -> "${fmt.dist(st.distanceKm)} · ${fmt.speed(st.speedKmh)}"
            Phase.IDLE -> getString(R.string.notif_starting)
        }
        return NotificationCompat.Builder(this, Notifs.CH_TRIP)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.notif_trip_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.finish), stop)
            .build()
    }

    private fun updateNotification() {
        lastNotifAt = System.currentTimeMillis()
        try {
            getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        running = false
        ticker?.cancel()
        try {
            fused.removeLocationUpdates(callback)
        } catch (e: Exception) {
        }
        TripBus.state.value = TripLive()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ARM = "app.rodalog.ARM"
        const val ACTION_START = "app.rodalog.START"
        const val ACTION_STOP = "app.rodalog.STOP"
        const val ACTION_BT_LOST = "app.rodalog.BT_LOST"
        const val EXTRA_VEHICLE = "vehicle"
        private const val NOTIF_ID = 4101

        private const val START_KMH = 8.0          // velocidad que confirma "movimiento"
        private const val MOVING_KMH = 3.0         // por debajo se considera detenido
        private const val STOP_IDLE_MS = 10 * 60_000L
        private const val STOP_VISIT_MS = 3 * 60_000L
        private const val ARMED_TIMEOUT_MS = 30 * 60_000L
        private const val BT_GRACE_MS = 2 * 60_000L
        private const val CHECKPOINT_MS = 20_000L

        @Volatile var running = false

        private fun intent(ctx: Context, action: String, vehicleId: String = "") =
            Intent(ctx, TripService::class.java).setAction(action).putExtra(EXTRA_VEHICLE, vehicleId)

        /** Bluetooth del vehículo conectado: queda armado, graba al detectar movimiento. */
        fun arm(ctx: Context, vehicleId: String) = launch(ctx, intent(ctx, ACTION_ARM, vehicleId))

        /** Inicio manual. */
        fun start(ctx: Context, vehicleId: String) = launch(ctx, intent(ctx, ACTION_START, vehicleId))

        fun stop(ctx: Context) {
            if (running) ctx.startService(intent(ctx, ACTION_STOP))
        }

        fun btLost(ctx: Context) {
            if (running) ctx.startService(intent(ctx, ACTION_BT_LOST))
        }

        private fun launch(ctx: Context, i: Intent) {
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                // Android bloqueó el inicio en segundo plano (falta permiso "Todo el tiempo" o ahorro de batería)
            }
        }
    }
}
