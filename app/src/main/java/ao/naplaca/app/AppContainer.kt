package ao.naplaca.app

import android.content.Context
import ao.naplaca.app.alerts.Notifier
import ao.naplaca.app.backup.BackupManager
import ao.naplaca.app.data.local.NaPlacaDatabase
import ao.naplaca.app.data.local.ProfileStore
import ao.naplaca.app.data.remote.Network
import ao.naplaca.app.data.repository.FavoritesRepository
import ao.naplaca.app.data.repository.FootballRepository

/** Injeção de dependências manual — simples e sem processadores de anotações extra. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val http = Network.okHttp(appContext.cacheDir)

    val database: NaPlacaDatabase by lazy { NaPlacaDatabase.create(appContext) }
    val profileStore = ProfileStore(appContext)
    val football by lazy { FootballRepository(Network.espn(http), Network.sportsDb(http), database.cache()) }
    val favorites by lazy { FavoritesRepository(database.favorites()) }
    val backup by lazy { BackupManager(appContext, profileStore, database.favorites()) }
    val notifier = Notifier(appContext)
}
