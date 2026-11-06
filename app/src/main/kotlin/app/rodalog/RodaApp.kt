package app.rodalog

import android.app.Application
import app.rodalog.data.PlaceManager
import app.rodalog.reminders.ReminderWorker
import app.rodalog.sync.SyncWorker
import app.rodalog.trip.TripCheckpoint
import app.rodalog.util.Notifs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RodaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        SyncWorker.schedulePeriodic(this)
        ReminderWorker.schedule(this)
        appScope.launch {
            try {
                // viaje que quedó sin cerrar si el sistema mató la app, y lugares frecuentes
                TripCheckpoint.recover(this@RodaApp, false)
                PlaceManager.maintenance(this@RodaApp)
            } catch (e: Exception) {
            }
        }
    }
}
