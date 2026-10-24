package app.rodalog

import android.app.Application
import app.rodalog.reminders.ReminderWorker
import app.rodalog.sync.SyncWorker
import app.rodalog.util.Notifs

class RodaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        SyncWorker.schedulePeriodic(this)
        ReminderWorker.schedule(this)
    }
}
