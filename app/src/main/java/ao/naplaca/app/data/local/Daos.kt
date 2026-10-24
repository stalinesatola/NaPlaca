package ao.naplaca.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite_teams ORDER BY name")
    fun observeTeams(): Flow<List<FavoriteTeamEntity>>

    @Query("SELECT * FROM favorite_teams")
    suspend fun teams(): List<FavoriteTeamEntity>

    @Upsert
    suspend fun upsertTeam(team: FavoriteTeamEntity)

    @Upsert
    suspend fun upsertTeams(teams: List<FavoriteTeamEntity>)

    @Query("DELETE FROM favorite_teams WHERE `key` = :key")
    suspend fun deleteTeam(key: String)

    @Query("DELETE FROM favorite_teams")
    suspend fun clearTeams()

    @Query("SELECT * FROM followed_matches ORDER BY startEpochMillis")
    fun observeFollowed(): Flow<List<FollowedMatchEntity>>

    @Query("SELECT * FROM followed_matches")
    suspend fun followed(): List<FollowedMatchEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFollowedIfAbsent(match: FollowedMatchEntity)

    @Upsert
    suspend fun upsertFollowed(match: FollowedMatchEntity)

    @Upsert
    suspend fun upsertFollowedAll(matches: List<FollowedMatchEntity>)

    @Query("DELETE FROM followed_matches WHERE matchId = :matchId")
    suspend fun deleteFollowed(matchId: String)

    @Query("DELETE FROM followed_matches WHERE startEpochMillis < :before")
    suspend fun deleteFollowedBefore(before: Long)

    @Query("DELETE FROM followed_matches")
    suspend fun clearFollowed()
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM match_snapshots WHERE matchId = :matchId")
    suspend fun get(matchId: String): MatchSnapshotEntity?

    @Upsert
    suspend fun upsert(snapshot: MatchSnapshotEntity)

    @Query("DELETE FROM match_snapshots WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key")
    suspend fun get(key: String): CacheEntity?

    @Upsert
    suspend fun put(entry: CacheEntity)

    @Query("DELETE FROM cache WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AlertLogDao {
    @Query("SELECT * FROM alert_log ORDER BY createdAt DESC LIMIT 200")
    fun observe(): Flow<List<AlertLogEntity>>

    @Insert
    suspend fun insert(entry: AlertLogEntity)

    @Query("DELETE FROM alert_log")
    suspend fun clear()
}
