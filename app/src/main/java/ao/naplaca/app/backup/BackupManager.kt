package ao.naplaca.app.backup

import android.content.Context
import android.net.Uri
import ao.naplaca.app.data.local.FavoriteTeamEntity
import ao.naplaca.app.data.local.FavoritesDao
import ao.naplaca.app.data.local.FollowedMatchEntity
import ao.naplaca.app.data.local.Profile
import ao.naplaca.app.data.local.ProfileStore
import ao.naplaca.app.data.remote.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val app: String = "NaPlaca",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val profile: Profile,
    val favoriteTeams: List<FavoriteTeamEntity>,
    val followedMatches: List<FollowedMatchEntity>,
)

/** Exporta/importa o perfil local para um ficheiro JSON escolhido pelo utilizador (trocar de telemóvel). */
class BackupManager(
    private val context: Context,
    private val profileStore: ProfileStore,
    private val favorites: FavoritesDao,
) {
    suspend fun build(): Backup = Backup(
        profile = profileStore.current(),
        favoriteTeams = favorites.teams(),
        followedMatches = favorites.followed(),
    )

    fun encode(backup: Backup): String = Network.json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup {
        val backup = Network.json.decodeFromString(Backup.serializer(), text)
        require(backup.app == "NaPlaca") { "Ficheiro não é uma cópia de segurança do NaPlaca" }
        return backup
    }

    suspend fun restore(backup: Backup) {
        profileStore.save(backup.profile.copy(onboarded = true))
        favorites.clearTeams()
        favorites.upsertTeams(backup.favoriteTeams)
        favorites.clearFollowed()
        favorites.upsertFollowedAll(backup.followedMatches)
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(build())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Não foi possível escrever o ficheiro")
    }

    suspend fun importFrom(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Não foi possível ler o ficheiro")
        decode(text).also { restore(it) }
    }
}
