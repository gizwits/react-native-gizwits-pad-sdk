package com.gizwitspadsdk

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.google.gson.annotations.SerializedName
import com.zhongguang.pad.sdk.SdkManager

data class GizGetHomeSensorData(
  @SerializedName("id")
  val id: String,
)

class GizwitsPadSdkModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return NAME
  }

  private var sdkHandler = SdkManager

  init {
    sdkHandler.initSdk(reactContext)
  }

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  @ReactMethod
  fun getHomeSensorData(options: ReadableMap, promise: Callback) {
//    var config = RNGizParamsChecker.check(options, promise, GizGetHomeSensorData::class.java)
//    config?.let {
//    }
    print("getHomeSensorData")
  }

  companion object {
    const val NAME = "GizwitsPadSdk"
  }
}
