package com.gizwits.reactnativegizwitssdkv5

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.gizwitspadsdk.GizException

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class GizResult<T> {
  @SerializedName("success")
  var success = false;
  @SerializedName("data")
  var data: T? = null
  @SerializedName("error")
  var error: Number? = null
  @SerializedName("message")
  var message: String = ""
}

object GizRNCallbackManager {

  fun <T> callbackWithResult(callback: Callback, result: Result<T>) {
    var data = GizResult<T>();
    if (result.isSuccess) {
      data.success = true
      data.data = result.getOrNull()
    } else {
      data.success = false
      result.exceptionOrNull()?.let {
        try {
          data.error = (it as GizException).errorCode
          data.message = (it as GizException).localizedMessage
        } catch (e: Exception) {

        }
      }
    }
    val resultData = convertToMap(data)
    if (result.isSuccess) {
      callback.invoke(null, resultData)
    } else {
      callback.invoke(resultData, null)
    }
  }

  fun jsonArray2WriteableArray(jsonArray: JSONArray): WritableArray? {
    return try {
      val writableArray = Arguments.createArray()
      for (i in 0 until jsonArray.length()) {
        val `object` = jsonArray[i]
        if (`object` is String) {
          writableArray.pushString(jsonArray.getString(i))
        } else if (`object` is Boolean) {
          writableArray.pushBoolean(jsonArray.getBoolean(i))
        } else if (`object` is Int) {
          writableArray.pushInt(jsonArray.getInt(i))
        } else if (`object` is Double) {
          writableArray.pushDouble(jsonArray.getDouble(i))
        } else if (`object` is JSONObject) {
          writableArray.pushMap(jsonObject2WriteableMap(jsonArray.getJSONObject(i)))
        } else if (`object` is JSONArray) {
          writableArray.pushArray(jsonArray2WriteableArray(jsonArray.getJSONArray(i)))
        } else {
          writableArray.pushNull()
        }
      }
      writableArray
    } catch (e: JSONException) {
      e.printStackTrace()
      null
    }
  }
  fun jsonObject2WriteableMap(jsonObject: JSONObject): WritableMap? {
    return try {
      val writableMap = Arguments.createMap()
      val iterator: Iterator<*> = jsonObject.keys()
      while (iterator.hasNext()) {
        val key = iterator.next() as String
        val `object` = jsonObject[key]
        if (`object` is String) {
          writableMap.putString(key, jsonObject.getString(key))
        } else if (`object` is Boolean) {
          writableMap.putBoolean(key, jsonObject.getBoolean(key))
        } else if (`object` is Int) {
          writableMap.putInt(key, jsonObject.getInt(key))
        } else if (`object` is Double) {
          writableMap.putDouble(key, jsonObject.getDouble(key))
        } else if (`object` is JSONObject) {
          writableMap.putMap(key, jsonObject2WriteableMap(jsonObject.getJSONObject(key)))
        } else if (`object` is JSONArray) {
          writableMap.putArray(
            key,
            jsonArray2WriteableArray(jsonObject.getJSONArray(key))
          )
        } else {
          writableMap.putNull(key)
        }
      }
      writableMap
    } catch (e: JSONException) {
      e.printStackTrace()
      null
    }
  }
  public inline fun <reified T : Any> convertToMap(obj: T): WritableMap? {
    val gson = Gson()
    val jsonString = gson.toJson(obj)
    return try {
      jsonObject2WriteableMap(JSONObject(jsonString))
    } catch (e: Exception) {
      null
    }
  }
}
