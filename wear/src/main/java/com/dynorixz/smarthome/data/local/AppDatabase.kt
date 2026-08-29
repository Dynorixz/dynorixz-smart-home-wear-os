package com.dynorixz.smarthome.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "smart_home_cache")
data class SmartHomeCacheEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val updatedAtMillis: Long,
)

@Dao
interface SmartHomeCacheDao {
    @Query("SELECT * FROM smart_home_cache WHERE id = 1")
    fun observe(): Flow<SmartHomeCacheEntity?>

    @Query("SELECT * FROM smart_home_cache WHERE id = 1")
    suspend fun get(): SmartHomeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SmartHomeCacheEntity)

    @Query("DELETE FROM smart_home_cache")
    suspend fun clear()
}

@Database(entities = [SmartHomeCacheEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smartHomeCacheDao(): SmartHomeCacheDao
}

