package com.gizwitspadsdk


import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableNativeMap
import com.gizwits.reactnativegizwitssdkv5.GizRNCallbackManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class RNGizParamsChecker {
  companion object {

    private fun convertReadableMapToJson(readableMap: ReadableMap): String {
      val readableNativeMap = readableMap as ReadableNativeMap
      val hashMap = readableNativeMap.toHashMap()
      val gson = Gson()
      return gson.toJson(hashMap)
    }

    fun <T : Any> check(options: ReadableMap, result: Callback, paramsType: Class<T>): T? {
      val gson = Gson()
      val jsonString = convertReadableMapToJson(options)
      try {
        return gson.fromJson(jsonString, paramsType)
      } catch (e: JsonSyntaxException) {
        // 参数错误
        GizRNCallbackManager.callbackWithResult(
          callback = result,
          result = Result.failure<Number>(GizException.GIZ_SDK_PARAM_INVALID)
        )
        return null
      }
    }

  }
}
