package app.rodalog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.rodalog.reminders.ReminderWorker
import app.rodalog.sync.SyncWorker

/** Tras reiniciar el teléfono vuelve a programar la sincronización y los recordatorios. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncWorker.schedulePeriodic(context)
            ReminderWorker.schedule(context)
        }
    }
}
