package com.nethunter

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RdapInfo(val regDate: String?, val expDate: String?)

object RdapFetcher {
    fun fetch(domain: String, timeoutMs: Long): RdapInfo? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val url = "https://rdap.verisign.com/net/v1/domain/$domain"
            val request = Request.Builder().url(url).header("Accept", "application/rdap+json").build()
            val response = client.newCall(request).execute()
            if (response.code == 200) {
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
                if (regDate != null || expDate != null) RdapInfo(regDate, expDate) else null
            } else null
        } catch (e: Exception) { null }
    }
}
