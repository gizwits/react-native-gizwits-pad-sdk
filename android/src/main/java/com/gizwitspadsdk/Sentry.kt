package com.gizwitspadsdk

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import android.provider.Settings
import java.security.MessageDigest
import java.util.Date

private fun convertThrowableToStackFrames(throwable: Throwable): Map<String, Any> {
    val stackTraceElements = throwable.stackTrace
    val frams = stackTraceElements.reversed().map { stackTraceElement ->
        mapOf(
            "in_app" to true,
            "package" to stackTraceElement.className,
            "filename" to stackTraceElement.fileName,
            "function" to stackTraceElement.methodName,
            "vars" to emptyArray<String>(),
            "lineno" to stackTraceElement.lineNumber,
        )
    }
    return mapOf<String, Any>(
        "frames" to frams
    )
}

public fun sendSentryError(err: Throwable, extra: Map<String, String?>) {
    runCatching {
        val countryCode = "CN"


        val sdkVersion = "1.0.0"
        val uid = SdkManager.androidId
        val exceptionType = err.javaClass.simpleName

        val defaultExtra = mapOf(
            "message" to err.message,
            "sdk" to sdkVersion,
        )

        val errorData = ErrorData(
            exception = mapOf(
                "values" to listOf(
                    mapOf(
                        "type" to exceptionType,
                        "value" to err.localizedMessage,
                        "stacktrace" to convertThrowableToStackFrames(err),
                        "mechanism" to mapOf(
                            "type" to "generic",
                            "handled" to false
                        )
                    )
                )
            ),
            level = "error",
            event_id = "${Date().time}".md5(),
            platform = "java",
            release = sdkVersion,
            sdk = mapOf<String, Any>(
                "name" to "android",
                "version" to "1.0.0"
            ),
            user = mapOf<String, Any>(
                "id" to uid,
                "ip_address" to "{{auto}}",
                "country_code" to countryCode
            ),
            timestamp = Date().time / 1000,
            environment = "production",
            contexts = mapOf(
                "device" to mapOf<String, Any>(
                    "model" to Build.MODEL,
                ),
                "os" to mapOf(
                    "name" to "android",
                    "version" to Build.VERSION.RELEASE
                ),
                "extra" to mapOf<String, Any>()
            ),
            extra = defaultExtra + extra
        )

        val retrofit = Retrofit.Builder()
            .baseUrl("https://appmonitor.gizwits.com/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(ApiService::class.java)
        service.sendError(errorData).enqueue(object : Callback<Any> {
            override fun onResponse(call: Call<Any>, response: Response<Any>) {
                // Handle success response
                println("Upload Sentry success")
            }

            override fun onFailure(call: Call<Any>, t: Throwable) {
                // Handle failure response
                println("Upload Sentry error")
            }
        })
    }
}


internal data class ErrorData(
    val exception: Map<String, List<Map<String, Any>>>,
    val level: String,
    val event_id: String,
    val release: String,
    val platform: String,
    val sdk: Map<String, Any>,
    val user: Map<String, Any>,
    val timestamp: Long,
    val environment: String,
    val contexts: Map<String, Map<String, Any>>,
    val extra: Map<String, String?>
)

internal interface ApiService {
    @POST("76/store/?sentry_key=9c02e4a3657e4f30a69ca3b64ad1e0a1&sentry_version=7")
    fun sendError(@Body options: ErrorData): Call<Any>
}
private val hexArray = "0123456789ABCDEF".toCharArray()

internal fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(toByteArray())
    val hexChars = CharArray(digest.size * 2)
    for (i in digest.indices) {
        val v = digest[i].toInt() and 0xFF
        hexChars[i * 2] = hexArray[v ushr 4]
        hexChars[i * 2 + 1] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

