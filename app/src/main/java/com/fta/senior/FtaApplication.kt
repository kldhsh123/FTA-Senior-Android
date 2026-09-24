package com.fta.senior

import android.app.Application
import com.fta.senior.core.ActionController
import com.fta.senior.data.OperationHistory
import com.fta.senior.data.SettingsStore
import com.fta.senior.privileged.ShizukuConnection

class FtaApplication : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var history: OperationHistory
        private set
    lateinit var shizuku: ShizukuConnection
        private set
    lateinit var actions: ActionController
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        history = OperationHistory(this)
        shizuku = ShizukuConnection()
        actions = ActionController(this)
    }
}
