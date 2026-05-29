package com.nethunter

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScanService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "ScanService"

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
    }

    override fun onCreate() { 
        super.onCreate()
        createNotificationChannel() 
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        
        if (intent?.action == ACTION_STOP) { 
            stopScan()
            return START_NOT_STICKY 
        }
        
        val domains = intent?.getStringArrayListExtra("domains")
        if (domains.isNullOrEmpty()) {
            Log.e(TAG, "No domains to scan")
            stopSelf()
            return START_NOT_STICKY
        }
        
        Log.d(TAG, "Starting scan of ${domains.size} domains")
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
            try {
                for ((index, domain) in domains.withIndex()) {
                    if (!isRunning) break
                    
                    try {
                        Log.d(TAG, "Checking domain $index: $domain")
                        val info = RdapFetcher.fetch(domain)
                        
                        if (info != null && info.regDate != null) { 
                            registered++ 
                            results.add(DomainResult(domain, info.regDate, info.expDate))
                            Log.d(TAG, "✅ $domain is registered")
                        } else { 
                            failed++ 
                            Log.d(TAG, "❌ $domain is available or error")
                        }
                    } catch (e: Exception) { 
                        Log.e(TAG, "Error checking $domain: ${e.message}")
                        failed++ 
                    }
                    
                    progress = index + 1
                    updateNotification()
                    
                    // تأخير بسيط بين الطلبات
                    delay(200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in scan: ${e.message}")
            } finally {
                isRunning = false
                showCompletionNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Scan completed")
            }
        }
    }

    private fun stopScan() { 
        isRunning = false
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Scan stopped by user")
    }
    
    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("NetHunter - فحص نطاقات .net")
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
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}
