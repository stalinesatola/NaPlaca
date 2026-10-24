package ao.naplaca.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteTeamEntity::class,
        FollowedMatchEntity::class,
        MatchSnapshotEntity::class,
        CacheEntity::class,
        AlertLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NaPlacaDatabase : RoomDatabase() {
    abstract fun favorites(): FavoritesDao
    abstract fun snapshots(): SnapshotDao
    abstract fun cache(): CacheDao
    abstract fun alertLog(): AlertLogDao

    companion object {
        fun create(context: Context): NaPlacaDatabase =
            Room.databaseBuilder(context, NaPlacaDatabase::class.java, "naplaca.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
