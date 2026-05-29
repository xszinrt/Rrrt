package com.nethunter

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScanService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_STOP = "STOP"
        const val CHANNEL_ID = "scan_channel"
        const val NOTIF_ID = 1
        var isRunning = false
        var progress = 0
        var total = 0
        var registered = 0
        var failed = 0
        val results = mutableListOf<DomainResult>()
        data class DomainResult(val name: String, val regDate: String?, val expDate: String?)
    }

    override fun onCreate() { createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopScan(); return START_NOT_STICKY }
        val domains = intent?.getStringArrayListExtra("domains") ?: return START_NOT_STICKY
        startForeground(NOTIF_ID, buildNotification())
        startScan(domains)
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
                if (!isRunning) break
                try {
                    val info = RdapFetcher.fetch(domain)
                    if (info != null) { registered++; results.add(DomainResult(domain, info.regDate, info.expDate)) }
                    else { failed++; results.add(DomainResult(domain, null, null)) }
                } catch (e: Exception) { failed++; results.add(DomainResult(domain, null, null)) }
                progress = index + 1
                updateNotification()
                delay(200)
            }
            isRunning = false
            showCompletionNotification()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopScan() { isRunning = false; scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    
    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("NetHunter")
        .setContentText("$progress / $total")
        .setSmallIcon(android.R.drawable.ic_menu_search)
        .setProgress(total, progress, false)
        .build()
    
    private fun updateNotification() { 
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification()) 
    }
    
    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("✅ اكتمل الفحص!")
            .setContentText("تم فحص $total نطاق .net")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2, notification)
    }
    
    private fun createNotificationChannel() { 
        NotificationChannel(CHANNEL_ID, "Scan", NotificationManager.IMPORTANCE_LOW).also { 
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(it) 
        } 
    }
    
    override fun onBind(intent: Intent?) = null
}
