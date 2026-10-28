package com.tal.eyedrops

import android.app.Application
import com.tal.eyedrops.data.Store
import com.tal.eyedrops.reminders.Alarms

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Alarms.createChannels(this)
        Alarms.scheduleNext(this)
        com.tal.eyedrops.update.Updater.scheduleDaily(this)
    }
}
