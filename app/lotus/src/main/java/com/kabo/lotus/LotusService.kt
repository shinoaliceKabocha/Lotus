package com.kabo.lotus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/***
 * sample service class
 */
class LotusService : Service() {

    /***
     * intent.action.
     */
    enum class Action(val value: String) {
        START("com.kabo.lotus.START"),
        STOP("com.kabo.lotus.STOP")
    }

    private companion object {
        const val TAG = "LotusService"
    }

    private val isServiceRunning = AtomicBoolean(false)
    private var logCollect: LogCollect? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action ?: return START_STICKY
        when (action) {
            Action.START.value -> handleStart()
            Action.STOP.value -> handleStop()
            else -> Log.e(TAG, "unknown action: $action")
        }

        return START_STICKY
    }

    private fun handleStart() {
        Log.v(TAG, "handleStart.")
        if (isServiceRunning.get()) {
            Log.e(TAG, "service is already running.")
            return
        }
        isServiceRunning.set(true)
        if (isServiceRunning.get()) {
            foreground()
        }

        if (logCollect?.hasCollecting() == true) {
            return
        }

        logCollect = LogCollect(
            this,
            object : LogCollect.ILogCollect {
                override fun onReceivedZipFile(zipFile: File) {
                    Log.i(TAG, zipFile.name)
                }
            },
            commandWithOption = arrayOf("logcat", "-b default", "*:D"),
            linePerFile = 25000,
            excludedWords = arrayOf("LocSvc_SystemStatus:"),
            totalOfFiles =  5
        )
        logCollect?.start()
    }

    private fun handleStop() {
        Log.v(TAG, "handleStop.")
        if (!isServiceRunning.get()) {
            Log.e(TAG, "service is not started.")
            return
        }

        logCollect?.stop()
        isServiceRunning.set(false)
        stopSelf()
    }


    private fun foreground() {
        val channelId = this.packageName
        val channelName = this.packageName
        val notificationId = 236329372

        val channel = NotificationChannel(
            channelId, channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("Lotus Service")
            .setContentText("collecting log...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        startForeground(notificationId, notificationBuilder.build())
    }
}
