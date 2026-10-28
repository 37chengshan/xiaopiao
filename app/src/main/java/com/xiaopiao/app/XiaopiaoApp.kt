package com.xiaopiao.app

import android.app.Application
import com.xiaopiao.app.work.ReminderScheduler

class XiaopiaoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.ensureScheduled(this)
    }
}
