package ph.edu.batstateu.ecoconnect.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CollectionRequestEntity::class, CollectionReceiptEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EcoConnectDatabase : RoomDatabase() {

    abstract fun collectionRequestDao(): CollectionRequestDao
    abstract fun collectionReceiptDao(): CollectionReceiptDao

    companion object {
        private const val DB_NAME = "ecoconnect.db"

        @Volatile
        private var instance: EcoConnectDatabase? = null

        fun getInstance(context: Context): EcoConnectDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EcoConnectDatabase::class.java,
                    DB_NAME,
                )
                    // TODO: replace with real migrations before release; this wipes local data on schema change.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
