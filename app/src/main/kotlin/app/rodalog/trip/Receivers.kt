package app.rodalog.trip

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.rodalog.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Escucha conexión/desconexión de Bluetooth y arranca o cierra el viaje del vehículo asociado. */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repo(app)
                val vehicle = repo.db.vehicles().byBluetooth(address)
                if (vehicle != null) {
                    when (action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED ->
                            if (repo.prefs.get().autoTrips) TripService.arm(app, vehicle.id)
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> TripService.btLost(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
