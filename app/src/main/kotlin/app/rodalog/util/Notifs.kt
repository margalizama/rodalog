package app.rodalog.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.rodalog.MainActivity
import app.rodalog.R

object Notifs {
    const val CH_TRIP = "trip"
    const val CH_REMINDER = "reminder"
    const val CH_FUEL = "fuel"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TRIP, ctx.getString(R.string.ch_trip), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, ctx.getString(R.string.ch_reminder), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FUEL, ctx.getString(R.string.ch_fuel), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** Aviso de reserva; como máximo uno cada 3 horas por vehículo. */
    fun lowFuel(ctx: Context, vehicleId: String, vehicleName: String, pct: Int, rangeText: String) {
        val sp = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val key = "low_$vehicleId"
        val now = System.currentTimeMillis()
        if (now - sp.getLong(key, 0L) < 3 * 3600_000L) return
        sp.edit().putLong(key, now).apply()
        createChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 77, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_FUEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(ctx.getString(R.string.low_fuel_notif_title))
            .setContentText(ctx.getString(R.string.low_fuel_notif_text, vehicleName, "$pct%", rangeText))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(5000 + (vehicleId.hashCode() and 0xFF), n)
        } catch (e: Exception) {
        }
    }

    /** Carga detectada sola: pide confirmar litros y precio. */
    fun autoFuel(ctx: Context, vehicleName: String, station: String, litersText: String) {
        createChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 78, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val where = station.ifBlank { ctx.getString(R.string.place_gas) }
        val n = NotificationCompat.Builder(ctx, CH_FUEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(ctx.getString(R.string.auto_fuel_notif_title))
            .setContentText(ctx.getString(R.string.auto_fuel_notif_text, vehicleName, where, litersText))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.auto_fuel_notif_text, vehicleName, where, litersText)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(5300 + (vehicleName.hashCode() and 0xFF), n)
        } catch (e: Exception) {
        }
    }
}
