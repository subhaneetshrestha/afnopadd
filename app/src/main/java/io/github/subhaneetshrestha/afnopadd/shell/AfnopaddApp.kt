package io.github.subhaneetshrestha.afnopadd.shell

import android.app.Application
import kotlinx.coroutines.launch

class AfnopaddApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext = this)
        // Repair the index from the sheet directories and purge old trash before the menu shows.
        container.appScope.launch { container.library.reconcile() }
    }
}
