package fr.heroverlook

import android.app.Application
import fr.heroverlook.data.AppLog

class HerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
    }
}
