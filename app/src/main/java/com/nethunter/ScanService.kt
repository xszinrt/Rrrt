package com.nethunter

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScanService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "ScanService"
    private var currentDelay = 500L
    private var currentTimeout = 5000L
    @Volatile private var paused = false

    companion object {
        const val ACTION_STOP = "STOP"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val CHANNEL_ID = "scan_channel"
        const val NOTIF_ID = 1
        var isRunning = false
        var progress = 0
        var total = 0
        var registered = 0
        var failed = 0
        val results = mutableListOf<DomainResult>()
    }

    override fun onCreate() { 
        super.onCreate()
        createNotificationChannel() 
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopScan()
            ACTION_PAUSE -> paused = true
            ACTION_RESUME -> paused = false
            else -> {
                val domains = intent?.getStringArrayListExtra("domains") ?: return START_NOT_STICKY
                currentDelay = intent.getLongExtra("delay", 500L)
                currentTimeout = intent.getLongExtra("timeout", 5000L)
                startForeground(NOTIF_ID, buildNotification())
                startScan(domains)
            }
        }
        return START_STICKY
    }

    private fun startScan(domains: List<String>) {
        isRunning = true
        progress = 0
        total = domains.size
        registered = 0
        failed = 0
        results.clear()
        
        scope.launch {
            for ((index, domain) in domains.withIndex()) {
                while (paused && isRunning) delay(300)
                if (!isRunning) break
                
                try {
                    val baseName = domain.substring(0, domain.length - 4)
                    val netDomain = "${baseName}.net"
                    val info = RdapFetcher.fetch(netDomain, currentTimeout)
                    if (info != null && info.regDate != null) { 
                        registered++ 
                        results.add(DomainResult(netDomain, info.regDate, info.expDate))
                    } else { 
                        failed++ 
                    }
                } catch (e: Exception) {
                    failed++
                }
                progress = index + 1
                updateNotification()
                delay(currentDelay)
            }
            isRunning = false
            showCompletionNotification()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopScan() { 
        isRunning = false
        paused = false
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("NetHunter")
        .setContentText("$progress / $total - محجوز: $registered")
        .setSmallIcon(android.R.drawable.ic_menu_search)
        .setProgress(total, progress, false)
        .build()

    private fun updateNotification() { 
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification())
    }

    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("✅ اكتمل الفحص!")
            .setContentText("تم فحص $total نطاق - وجدنا $registered نطاق محجوز")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }

    private fun createNotificationChannel() { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "فحص النطاقات", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?) = null
}
