package app.rodalog.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object Perms {
    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun fine(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

    fun background(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 29) granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) else fine(ctx)

    fun notif(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.POST_NOTIFICATIONS) else true

    fun bt(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 31) granted(ctx, Manifest.permission.BLUETOOTH_CONNECT) else true

    fun battery(ctx: Context): Boolean = try {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) {
        false
    }
}
