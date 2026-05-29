package com.nethunter

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RdapInfo(val regDate: String?, val expDate: String?)

object RdapFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetch(domain: String): RdapInfo? {
        return try {
            // التأكد من أن النطاق ينتهي بـ .com
            if (!domain.endsWith(".com")) {
                return null
            }
            
            // استخراج اسم النطاق بدون .com
            val baseName = domain.substring(0, domain.length - 4)
            // بناء نطاق .net للفحص (لأن Verisign RDAP مخصص لـ .net)
            val netDomain = "${baseName}.net"
            
            // استخدام RDAP الرسمي من Verisign (مثل صفحة الويب)
            val url = "https://rdap.verisign.com/net/v1/domain/${netDomain}"
            android.util.Log.d("RdapFetcher", "فحص: $netDomain -> $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/rdap+json")
                .build()
                
            val response = client.newCall(request).execute()
            val statusCode = response.code
            
            when (statusCode) {
                200 -> {
                    // النطاق مسجل - استخراج التاريخ
                    val json = JSONObject(response.body?.string() ?: return null)
                    val events = json.optJSONArray("events")
                    var regDate: String? = null
                    var expDate: String? = null
                    
                    if (events != null) {
                        for (i in 0 until events.length()) {
                            val event = events.getJSONObject(i)
                            val action = event.optString("eventAction")
                            val date = event.optString("eventDate")
                            val formattedDate = date.takeIf { it.isNotEmpty() }?.substring(0, 10)
                            
                            when (action) {
                                "registration" -> regDate = formattedDate
                                "expiration" -> expDate = formattedDate
                            }
                        }
                    }
                    
                    if (regDate != null || expDate != null) {
                        RdapInfo(regDate, expDate)
                    } else {
                        null
                    }
                }
                404 -> {
                    // النطاق غير مسجل (متاح)
                    android.util.Log.d("RdapFetcher", "النطاق $netDomain غير مسجل (متاح)")
                    null
                }
                else -> {
                    android.util.Log.w("RdapFetcher", "استجابة غير متوقعة: $statusCode لـ $netDomain")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RdapFetcher", "فشل فحص ${domain}: ${e.message}")
            null
        }
    }
}
