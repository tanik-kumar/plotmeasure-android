package com.tanik.biharmapmeasure

import android.app.Application
import com.tanik.biharmapmeasure.plotmeasure.data.CrashReportStore
import kotlin.system.exitProcess

class PlotMeasureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val crashReportStore = CrashReportStore(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashReportStore.persistCrashReport(thread.name, throwable)
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                exitProcess(10)
            }
        }
    }
}
