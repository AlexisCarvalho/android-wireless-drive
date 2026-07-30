package dev.alexis.wirelessdrive

import android.app.Application

class WirelessDriveApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
