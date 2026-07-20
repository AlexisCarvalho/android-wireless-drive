package dev.alexis.mediagallery

import android.app.Application

class MediaGalleryApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
