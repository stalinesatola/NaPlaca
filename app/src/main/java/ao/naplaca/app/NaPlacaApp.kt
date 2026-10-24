package ao.naplaca.app

import android.app.Application
import ao.naplaca.app.alerts.AlertScheduler

class NaPlacaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        AlertScheduler.schedulePeriodic(this)
    }
}
