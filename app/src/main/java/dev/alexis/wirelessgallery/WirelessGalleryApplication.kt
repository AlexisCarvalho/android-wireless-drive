package dev.alexis.wirelessgallery

import android.app.Application

class WirelessGalleryApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
