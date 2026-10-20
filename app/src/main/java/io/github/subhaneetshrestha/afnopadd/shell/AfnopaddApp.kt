package io.github.subhaneetshrestha.afnopadd.shell

import android.app.Application

class AfnopaddApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext = this)
    }
}
