package com.drdev.faphousepro

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.drdev.faphousepro.security.NativeSecurityBridge
import java.io.PrintWriter
import java.io.StringWriter

class FapHouseProApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()

        try {
            NativeSecurityBridge.ensureLoaded()
        } catch (t: Throwable) {
            Log.e(TAG, "native lib load failed: ${t.message}")
        }

        try {
            AppGraph.init(this)
        } catch (t: Throwable) {
            Log.e(TAG, "AppGraph init failed: ${t.message}", t)
        }

        Log.i(TAG, "app ready — integrityOk=${AppGraph.isReady && AppGraph.integrityOk}")
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                Log.e(TAG, "uncaught on ${thread.name}:\n${sw}")
            } catch (_: Throwable) { }
            default?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "FHP-App"
    }
}