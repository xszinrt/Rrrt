package com.nethunter

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RdapInfo(val regDate: String?, val expDate: String?)

object RdapFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetch(domain: String): RdapInfo? {
        return try {
            // نفحص النطاق كما هو تماماً دون تغيير
            val url = "https://rdap.org/domain/$domain"
            android.util.Log.d("RdapFetcher", "فحص: $domain -> $url")
            
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(response.body?.string() ?: return null)
            val events = json.optJSONArray("events")
            var regDate: String? = null
            var expDate: String? = null
            for (i in 0 until (events?.length() ?: 0)) {
                val event = events!!.getJSONObject(i)
                when (event.optString("eventAction")) {
                    "registration" -> regDate = event.optString("eventDate").takeIf { it.isNotEmpty() }?.substring(0, 10)
                    "expiration" -> expDate = event.optString("eventDate").takeIf { it.isNotEmpty() }?.substring(0, 10)
                }
            }
            if (regDate == null && expDate == null) null else RdapInfo(regDate, expDate)
        } catch (e: Exception) { 
            android.util.Log.e("RdapFetcher", "فشل فحص $domain: ${e.message}")
            null 
        }
    }
}
