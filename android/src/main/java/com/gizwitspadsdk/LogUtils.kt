package com.gizwitspadsdk

import android.util.Log

/**
 * 日志工具类
 */
object LogUtils {
    private const val TAG = "GizwitsPadSDK"
    private var isDebugEnabled = false

    /**
     * 开启调试日志
     */
    fun enableDebug() {
        isDebugEnabled = true
    }

    /**
     * 关闭调试日志
     */
    fun disableDebug() {
        isDebugEnabled = false
    }

    /**
     * 打印详细日志
     * @param tag 自定义标签
     * @param message 日志内容
     */
    fun v(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.v("$TAG - $tag", message)
        }
    }

    /**
     * 打印调试日志
     * @param tag 自定义标签
     * @param message 日志内容
     */
    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$TAG - $tag", message)
        }
    }

    /**
     * 打印信息日志
     * @param tag 自定义标签
     * @param message 日志内容
     */
    fun i(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.i("$TAG - $tag", message)
        }
    }

    /**
     * 打印警告日志
     * @param tag 自定义标签
     * @param message 日志内容
     * @param throwable 异常信息，可选
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$TAG - $tag", message, throwable)
        } else {
            Log.w("$TAG - $tag", message)
        }
    }

    /**
     * 打印错误日志
     * @param tag 自定义标签
     * @param message 日志内容
     * @param throwable 异常信息，可选
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG - $tag", message, throwable)
        } else {
            Log.e("$TAG - $tag", message)
        }
    }

    /**
     * 打印断言日志
     * @param tag 自定义标签
     * @param message 日志内容
     */
    fun wtf(tag: String, message: String) {
        Log.wtf("$TAG - $tag", message)
    }
} 