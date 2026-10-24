package app.rodalog.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.rodalog.R

object Notifs {
    const val CH_TRIP = "trip"
    const val CH_REMINDER = "reminder"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TRIP, ctx.getString(R.string.ch_trip), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, ctx.getString(R.string.ch_reminder), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
