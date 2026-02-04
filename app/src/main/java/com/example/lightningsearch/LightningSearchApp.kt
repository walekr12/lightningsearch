package com.example.lightningsearch

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlin.system.exitProcess

@HiltAndroidApp
class LightningSearchApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Set up global exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("LightningSearch", "CRASH: ${throwable.message}", throwable)

                // Get stack trace as string
                val stackTrace = Log.getStackTraceString(throwable)

                // Launch crash activity
                val intent = Intent(this, CrashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("error_message", throwable.message ?: "Unknown error")
                    putExtra("error_class", throwable.javaClass.simpleName)
                    putExtra("stack_trace", stackTrace)
                }
                startActivity(intent)

                // Kill the process
                Process.killProcess(Process.myPid())
                exitProcess(1)
            } catch (e: Exception) {
                // If crash activity fails, use default handler
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
