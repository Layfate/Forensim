package com.forensim.app

import android.app.Application

class ForensimApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Daily "keep your streak" reminder (does nothing unless a student is signed in and allowed it).
        com.forensim.app.feature.student.StreakReminder.createChannel(this)
        com.forensim.app.feature.student.StreakReminder.schedule(this)
    }
}
