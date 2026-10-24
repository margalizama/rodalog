package app.rodalog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.rodalog.R
import app.rodalog.data.Expense
import app.rodalog.data.FuelEntry
import app.rodalog.data.Reminder
import app.rodalog.data.Repo
import app.rodalog.data.Settings
import app.rodalog.data.Trip
import app.rodalog.data.Vehicle
import app.rodalog.sync.Auth
import app.rodalog.sync.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)

    val settings: StateFlow<Settings> =
        repo.prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val vehicles: StateFlow<List<Vehicle>> =
        repo.db.vehicles().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val current: StateFlow<Vehicle?> =
        combine(vehicles, settings) { vs, s -> vs.firstOrNull { it.id == s.selectedVehicle } ?: vs.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val fuel: StateFlow<List<FuelEntry>> =
        current.flatMapLatest { v -> if (v == null) flowOf(emptyList()) else repo.db.fuel().observe(v.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val expenses: StateFlow<List<Expense>> =
        current.flatMapLatest { v -> if (v == null) flowOf(emptyList()) else repo.db.expenses().observe(v.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reminders: StateFlow<List<Reminder>> =
        current.flatMapLatest { v -> if (v == null) flowOf(emptyList()) else repo.db.reminders().observe(v.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trips: StateFlow<List<Trip>> =
        current.flatMapLatest { v -> if (v == null) flowOf(emptyList()) else repo.db.trips().observe(v.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val syncStatus = SyncEngine.status
    val authed = MutableStateFlow(false)
    val message = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private fun str(id: Int) = getApplication<Application>().getString(id)

    fun select(id: String) {
        viewModelScope.launch { repo.prefs.setVehicle(id) }
    }

    fun saveVehicle(v: Vehicle) {
        viewModelScope.launch {
            repo.saveVehicle(v)
            if (settings.value.selectedVehicle.isBlank() || vehicles.value.isEmpty()) repo.prefs.setVehicle(v.id)
        }
    }

    fun deleteVehicle(v: Vehicle) {
        viewModelScope.launch { repo.deleteVehicle(v) }
    }

    fun saveFuel(e: FuelEntry, isNew: Boolean) {
        viewModelScope.launch { repo.saveFuel(e, isNew) }
    }

    fun deleteFuel(e: FuelEntry) {
        viewModelScope.launch { repo.deleteFuel(e) }
    }

    fun saveExpense(e: Expense, isNew: Boolean) {
        viewModelScope.launch { repo.saveExpense(e, isNew) }
    }

    fun deleteExpense(e: Expense) {
        viewModelScope.launch { repo.deleteExpense(e) }
    }

    fun saveReminder(r: Reminder) {
        viewModelScope.launch { repo.saveReminder(r) }
    }

    fun deleteReminder(r: Reminder) {
        viewModelScope.launch { repo.deleteReminder(r) }
    }

    fun completeReminder(r: Reminder) {
        viewModelScope.launch { repo.completeReminder(r) }
    }

    fun saveTrip(t: Trip) {
        viewModelScope.launch { repo.saveTrip(t) }
    }

    fun deleteTrip(t: Trip) {
        viewModelScope.launch { repo.deleteTrip(t) }
    }

    fun setUnits(dist: String, vol: String, cons: String, currency: String) {
        viewModelScope.launch { repo.prefs.setUnits(dist, vol, cons, currency) }
    }

    fun setAutoTrips(on: Boolean) {
        viewModelScope.launch { repo.prefs.setAutoTrips(on) }
    }

    fun syncNow() {
        viewModelScope.launch { SyncEngine.run(getApplication()) }
    }

    fun refreshAuth() {
        viewModelScope.launch { authed.value = Auth.silentToken(getApplication()) != null }
    }

    private fun report(e: Exception) {
        val m = if (e is IllegalStateException && e.message == "auth") str(R.string.err_need_google)
        else e.message ?: e.javaClass.simpleName
        message.tryEmit(m)
    }

    fun createSheet() {
        viewModelScope.launch {
            try {
                SyncEngine.createSheet(getApplication(), "RodaLog")
                SyncEngine.run(getApplication())
                message.tryEmit(str(R.string.sheet_created))
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun connectSheet(input: String) {
        viewModelScope.launch {
            try {
                SyncEngine.connectSheet(getApplication(), input)
                SyncEngine.run(getApplication())
                message.tryEmit(str(R.string.sheet_connected))
            } catch (e: Exception) {
                report(e)
            }
        }
    }
}
