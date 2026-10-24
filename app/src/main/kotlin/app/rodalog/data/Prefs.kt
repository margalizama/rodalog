package app.rodalog.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Currency
import java.util.Locale

private val Context.store by preferencesDataStore("rodalog_prefs")

/** Preferencias de unidades y sincronización. Internamente todo se guarda en km / litros. */
data class Settings(
    val distanceUnit: String = defaultDistance(),   // KM | MI
    val volumeUnit: String = defaultVolume(),       // L | GAL
    val consumptionUnit: String = defaultConsumption(), // L100 | KML | MPG
    val currency: String = defaultCurrency(),
    val sheetId: String = "",
    val accountEmail: String = "",
    val selectedVehicle: String = "",
    val autoTrips: Boolean = true,
    val lastSync: Long = 0L,
) {
    companion object {
        private val imperial = setOf("US", "GB", "LR", "MM")
        fun defaultDistance() = if (Locale.getDefault().country in imperial) "MI" else "KM"
        fun defaultVolume() = if (Locale.getDefault().country == "US") "GAL" else "L"
        fun defaultConsumption() = if (Locale.getDefault().country == "US") "MPG" else "L100"
        fun defaultCurrency(): String = try {
            Currency.getInstance(Locale.getDefault()).symbol
        } catch (e: Exception) { "$" }
    }
}

class Prefs(private val ctx: Context) {
    private object K {
        val dist = stringPreferencesKey("dist")
        val vol = stringPreferencesKey("vol")
        val cons = stringPreferencesKey("cons")
        val cur = stringPreferencesKey("cur")
        val sheet = stringPreferencesKey("sheet")
        val email = stringPreferencesKey("email")
        val vehicle = stringPreferencesKey("vehicle")
        val auto = booleanPreferencesKey("auto")
        val last = longPreferencesKey("last")
    }

    private fun Preferences.toSettings() = Settings(
        distanceUnit = this[K.dist] ?: Settings.defaultDistance(),
        volumeUnit = this[K.vol] ?: Settings.defaultVolume(),
        consumptionUnit = this[K.cons] ?: Settings.defaultConsumption(),
        currency = this[K.cur] ?: Settings.defaultCurrency(),
        sheetId = this[K.sheet] ?: "",
        accountEmail = this[K.email] ?: "",
        selectedVehicle = this[K.vehicle] ?: "",
        autoTrips = this[K.auto] ?: true,
        lastSync = this[K.last] ?: 0L,
    )

    val flow: Flow<Settings> = ctx.store.data.map { it.toSettings() }
    suspend fun get(): Settings = flow.first()

    suspend fun setUnits(dist: String, vol: String, cons: String, currency: String) = ctx.store.edit {
        it[K.dist] = dist; it[K.vol] = vol; it[K.cons] = cons; it[K.cur] = currency
    }
    suspend fun setSheet(id: String) = ctx.store.edit { it[K.sheet] = id }
    suspend fun setEmail(email: String) = ctx.store.edit { it[K.email] = email }
    suspend fun setVehicle(id: String) = ctx.store.edit { it[K.vehicle] = id }
    suspend fun setAutoTrips(on: Boolean) = ctx.store.edit { it[K.auto] = on }
    suspend fun setLastSync(t: Long) = ctx.store.edit { it[K.last] = t }
}
