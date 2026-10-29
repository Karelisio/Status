package com.karelisio.status

import android.app.Application

class StatusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
