package app.rodalog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import app.rodalog.sync.Auth
import app.rodalog.ui.AppViewModel
import app.rodalog.ui.Host
import app.rodalog.ui.RodaRoot
import app.rodalog.ui.RodaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val permTick = mutableIntStateOf(0)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick.intValue++ }

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                try {
                    Auth.fromIntent(this, res.data)
                } catch (e: Exception) {
                }
                vm.refreshAuth()
                vm.syncNow()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RodaTheme {
                RodaRoot(
                    vm,
                    Host(
                        connectGoogle = { connectGoogle() },
                        requestBase = { requestBase() },
                        requestBackground = { requestBackground() },
                        requestBattery = { requestBattery() },
                        openUrl = { openUrl(it) },
                        share = { share(it) },
                        permTick = permTick.intValue,
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permTick.intValue++
    }

    private fun connectGoogle() {
        lifecycleScope.launch {
            try {
                val r = Auth.authorize(this@MainActivity)
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    vm.refreshAuth()
                    vm.syncNow()
                }
            } catch (e: Exception) {
                vm.message.tryEmit(getString(R.string.err_google, e.message ?: ""))
            }
        }
    }

    private fun requestBase() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) list.add(Manifest.permission.BLUETOOTH_CONNECT)
        permLauncher.launch(list.toTypedArray())
    }

    private fun requestBackground() {
        if (!app.rodalog.ui.Perms.fine(this)) {
            requestBase()
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // En Android 11+ debe elegirse "Permitir todo el tiempo" en los ajustes de la app.
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(i)
        } else if (Build.VERSION.SDK_INT >= 29) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
        }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }
}
