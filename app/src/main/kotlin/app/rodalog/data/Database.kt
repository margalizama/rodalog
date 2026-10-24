package app.rodalog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE deleted = 0 ORDER BY name")
    suspend fun getAll(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: String): Vehicle?

    @Query("SELECT * FROM vehicles WHERE deleted = 0 AND btAddress = :mac LIMIT 1")
    suspend fun byBluetooth(mac: String): Vehicle?

    @Query("SELECT * FROM vehicles")
    suspend fun everything(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE dirty = 1")
    suspend fun dirty(): List<Vehicle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: Vehicle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(v: List<Vehicle>)
}

@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel WHERE deleted = 0 AND vehicleId = :vid ORDER BY date DESC")
    fun observe(vid: String): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel WHERE deleted = 0 AND vehicleId = :vid ORDER BY odometerKm ASC, date ASC")
    suspend fun listAsc(vid: String): List<FuelEntry>

    @Query("SELECT * FROM fuel")
    suspend fun everything(): List<FuelEntry>

    @Query("SELECT * FROM fuel WHERE dirty = 1")
    suspend fun dirty(): List<FuelEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: FuelEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(e: List<FuelEntry>)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE deleted = 0 AND vehicleId = :vid ORDER BY date DESC")
    fun observe(vid: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses")
    suspend fun everything(): List<Expense>

    @Query("SELECT * FROM expenses WHERE dirty = 1")
    suspend fun dirty(): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(e: List<Expense>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE deleted = 0 AND vehicleId = :vid ORDER BY done ASC, dueDate ASC")
    fun observe(vid: String): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE deleted = 0 AND done = 0")
    suspend fun active(): List<Reminder>

    @Query("SELECT * FROM reminders")
    suspend fun everything(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE dirty = 1")
    suspend fun dirty(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: Reminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(e: List<Reminder>)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE deleted = 0 AND vehicleId = :vid ORDER BY startTime DESC")
    fun observe(vid: String): Flow<List<Trip>>

    @Query("SELECT * FROM trips")
    suspend fun everything(): List<Trip>

    @Query("SELECT * FROM trips WHERE dirty = 1")
    suspend fun dirty(): List<Trip>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: Trip)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(e: List<Trip>)
}

@Database(
    entities = [Vehicle::class, FuelEntry::class, Expense::class, Reminder::class, Trip::class],
    version = 1,
    exportSchema = false
)
abstract class RodaDb : RoomDatabase() {
    abstract fun vehicles(): VehicleDao
    abstract fun fuel(): FuelDao
    abstract fun expenses(): ExpenseDao
    abstract fun reminders(): ReminderDao
    abstract fun trips(): TripDao

    companion object {
        @Volatile private var instance: RodaDb? = null
        fun get(ctx: Context): RodaDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, RodaDb::class.java, "rodalog.db")
                .build().also { instance = it }
        }
    }
}
