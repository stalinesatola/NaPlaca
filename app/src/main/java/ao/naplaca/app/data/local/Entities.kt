package ao.naplaca.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "favorite_teams")
data class FavoriteTeamEntity(
    /** "SOURCE:id" */
    @PrimaryKey val key: String,
    val source: String,
    val teamId: String,
    val leagueSlug: String,
    val name: String,
    val logo: String?,
    val alertsEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "followed_matches")
data class FollowedMatchEntity(
    @PrimaryKey val matchId: String,
    val source: String,
    val leagueSlug: String,
    val title: String,
    val startEpochMillis: Long,
    /** Seguido automaticamente por ser jogo de uma equipa favorita. */
    val auto: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Último estado conhecido de um jogo seguido — base para detetar golos, cartões, etc. */
@Entity(tableName = "match_snapshots")
data class MatchSnapshotEntity(
    @PrimaryKey val matchId: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val state: String,
    val statusDetail: String,
    val homeRed: Int,
    val awayRed: Int,
    val reminderSent: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Cache "cache-first" das listas de jogos e de outros payloads, em JSON. */
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "alert_log")
data class AlertLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: String,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
)
