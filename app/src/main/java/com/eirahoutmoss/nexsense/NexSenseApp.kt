package com.eirahoutmoss.nexsense

import android.app.Application

class NexSenseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
