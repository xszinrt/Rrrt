package com.nethunter

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class ScanService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "ScanService"
    
    // عميل HTTP للفحص السريع
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

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
                        // استخراج اسم النطاق الأساسي من .com لإضافة .net
                        val baseName = domain.substring(0, domain.length - 4)
                        val netDomain = "${baseName}.net"
                        
                        Log.d(TAG, "Checking domain ${index + 1}/${total}: $netDomain")
                        
                        // الخطوة 1: فحص الحجز عبر طلب HEAD (سريع)
                        val isRegistered = checkRegistrationViaHead(netDomain)
                        
                        if (isRegistered) {
                            // الخطوة 2: النطاق محجوز -> جلب التاريخ من Verisign RDAP
                            Log.d(TAG, "✅ $netDomain is registered, fetching expiry date...")
                            val rdapInfo = fetchRdapInfo(netDomain)
                            
                            registered++
                            results.add(DomainResult(
                                name = netDomain,
                                regDate = rdapInfo?.regDate,
                                expDate = rdapInfo?.expDate
                            ))
                            Log.d(TAG, "✅ Added $netDomain to results")
                        } else {
                            // النطاق متاح -> نتجاهله
                            failed++
                            Log.d(TAG, "❌ $netDomain is available (ignored)")
                        }
                    } catch (e: ConnectException) {
                        Log.e(TAG, "Connection error for $domain: ${e.message}")
                        failed++
                    } catch (e: SocketTimeoutException) {
                        Log.e(TAG, "Timeout for $domain: ${e.message}")
                        failed++
                    } catch (e: UnknownHostException) {
                        Log.e(TAG, "Unknown host for $domain: ${e.message}")
                        failed++
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error for $domain: ${e.message}")
                        failed++
                    }
                    
                    progress = index + 1
                    updateNotification()
                    
                    // تأخير بين الطلبات لتجنب الحظر
                    delay(300)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in scan: ${e.message}")
            } finally {
                isRunning = false
                showCompletionNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Scan completed - Registered: $registered, Available/Ignored: $failed")
            }
        }
    }
    
    /**
     * فحص حجز النطاق عبر طلب HEAD
     * يعيد true إذا كان النطاق محجوزاً (يستجيب الخادم)
     */
    private fun checkRegistrationViaHead(domain: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://$domain")
                .head()
                .build()
            val response = httpClient.newCall(request).execute()
            val isRegistered = response.code < 400 // 200-399 يعني النطاق محجوز
            response.close()
            isRegistered
        } catch (e: Exception) {
            // إذا فشل الاتصال، غالباً النطاق غير محجوز أو لا يستجيب
            false
        }
    }
    
    /**
     * جلب معلومات RDAP من Verisign (التاريخ فقط)
     */
    private fun fetchRdapInfo(domain: String): RdapInfo? {
        return try {
            RdapFetcher.fetch(domain)
        } catch (e: Exception) {
            Log.e(TAG, "RDAP fetch failed for $domain: ${e.message}")
            null
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
        .setOngoing(true)
        .build()
    
    private fun updateNotification() { 
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification())
    }
    
    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("✅ اكتمل الفحص!")
            .setContentText("تم فحص $total نطاق - وجدنا $registered نطاق .net محجوز")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }
    
    private fun createNotificationChannel() { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "فحص النطاقات", NotificationManager.IMPORTANCE_LOW)
            channel.description = "إشعارات تقدم فحص نطاقات .net"
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
