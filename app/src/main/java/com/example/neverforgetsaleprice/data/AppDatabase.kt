package com.example.neverforgetsaleprice.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.neverforgetsaleprice.domain.CheckStatus

@Database(
    entities = [ProductEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
}

class AppConverters {
    @TypeConverter
    fun checkStatusToString(status: CheckStatus): String = status.name

    @TypeConverter
    fun stringToCheckStatus(value: String): CheckStatus = CheckStatus.valueOf(value)
}

object AppMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE products ADD COLUMN checkIntervalSeconds INTEGER NOT NULL DEFAULT 21600")
        }
    }
}
