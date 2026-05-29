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
            // domain هنا هو النطاق الأصلي من الملف (ينتهي بـ .com)
            // نستخرج الاسم ونضيف .net
            val baseName = domain.substring(0, domain.length - 4)
            val netDomain = "${baseName}.net"
            
            // استخدام RDAP الرسمي من Verisign (مثل صفحة الويب تماماً)
            val url = "https://rdap.verisign.com/net/v1/domain/${netDomain}"
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/rdap+json")
                .build()
                
            val response = client.newCall(request).execute()
            
            if (response.code == 200) {
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
                
                // نعيد النتيجة فقط إذا كان النطاق مسجلاً (له تاريخ)
                if (regDate != null || expDate != null) {
                    RdapInfo(regDate, expDate)
                } else {
                    null
                }
            } else {
                // النطاق غير مسجل (متاح) - نتجاهله
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
