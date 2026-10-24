package app.rodalog.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.rodalog.MainActivity
import app.rodalog.R
import app.rodalog.data.RodaDb
import app.rodalog.util.Notifs
import java.util.concurrent.TimeUnit

/** Revisa cada 12 h los recordatorios por fecha o por kilometraje y avisa con una notificación. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = RodaDb.get(ctx)
        val vehicles = db.vehicles().getAll().associateBy { it.id }
        val now = System.currentTimeMillis()
        val day = 24 * 3600_000L
        val nm = ctx.getSystemService(NotificationManager::class.java)
        Notifs.createChannels(ctx)

        for (r in db.reminders().active()) {
            val v = vehicles[r.vehicleId] ?: continue
            val byDate = r.dueDate > 0 && r.dueDate - now < 3 * day
            val byKm = r.dueOdometerKm > 0 && r.dueOdometerKm - v.odometerKm < 300.0
            if ((byDate || byKm) && now - r.notifiedAt > day) {
                val open = PendingIntent.getActivity(
                    ctx, r.id.hashCode(), Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val n = NotificationCompat.Builder(ctx, Notifs.CH_REMINDER)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(r.title)
                    .setContentText(v.name)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                try {
                    nm.notify(r.id.hashCode(), n)
                } catch (e: Exception) {
                }
                db.reminders().upsert(r.copy(notifiedAt = now))
            }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
