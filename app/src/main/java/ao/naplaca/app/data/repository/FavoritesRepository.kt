package ao.naplaca.app.data.repository

import ao.naplaca.app.data.local.FavoriteTeamEntity
import ao.naplaca.app.data.local.FavoritesDao
import ao.naplaca.app.data.local.FollowedMatchEntity
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.TeamSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoritesRepository(private val dao: FavoritesDao) {

    val teams: Flow<List<FavoriteTeamEntity>> = dao.observeTeams()
    val teamKeys: Flow<Set<String>> = teams.map { list -> list.map { it.key }.toSet() }
    val followed: Flow<List<FollowedMatchEntity>> = dao.observeFollowed()
    val followedIds: Flow<Set<String>> = followed.map { list -> list.map { it.matchId }.toSet() }

    suspend fun toggleTeam(source: Source, leagueSlug: String, team: TeamSide) =
        toggleTeam(source, leagueSlug, team.id, team.name, team.logo)

    suspend fun toggleTeam(source: Source, leagueSlug: String, teamId: String, name: String, logo: String?) {
        val key = key(source, teamId)
        if (dao.teams().any { it.key == key }) {
            dao.deleteTeam(key)
        } else {
            dao.upsertTeam(FavoriteTeamEntity(key, source.name, teamId, leagueSlug, name, logo))
        }
    }

    suspend fun setTeamAlerts(team: FavoriteTeamEntity, enabled: Boolean) =
        dao.upsertTeam(team.copy(alertsEnabled = enabled))

    suspend fun toggleFollow(match: Match) {
        if (dao.followed().any { it.matchId == match.id }) dao.deleteFollowed(match.id) else follow(match, auto = false)
    }

    suspend fun follow(match: Match, auto: Boolean) {
        val entity = FollowedMatchEntity(
            matchId = match.id,
            source = match.source.name,
            leagueSlug = match.leagueSlug,
            title = "${match.home.name} x ${match.away.name}",
            startEpochMillis = match.startEpochMillis,
            auto = auto,
        )
        if (auto) dao.insertFollowedIfAbsent(entity) else dao.upsertFollowed(entity)
    }

    suspend fun unfollow(matchId: String) = dao.deleteFollowed(matchId)

    companion object {
        fun key(source: Source, teamId: String) = "${source.name}:$teamId"
    }
}
