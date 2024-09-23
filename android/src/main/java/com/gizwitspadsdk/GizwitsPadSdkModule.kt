package com.gizwitspadsdk

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.gizwits.reactnativegizwitssdkv5.GizRNCallbackManager
import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class GizSetLedStatusParams(
  @SerializedName("status")
  val status: Boolean,
)
data class GizSetRelayParams(
  @SerializedName("status")
  val status: Boolean,
  @SerializedName("index")
  val index: Int,
)
data class GizGetRelayParams(
  @SerializedName("index")
  val index: Int,
)
data class GizSetBreathingLightParams(
  @SerializedName("index")
  val index: Int,
  @SerializedName("effect")
  val effect: Int,
  @SerializedName("isBreath")
  val isBreath: Boolean,
)
data class GizSet485PortParams(
  @SerializedName("index")
  val index: Int,
  @SerializedName("status")
  val status: Boolean,
)
data class GizGet485PortParams(
  @SerializedName("index")
  val index: Int,
)
data class GizSend485PortMessageParams(
  @SerializedName("data")
  val data: String,
  @SerializedName("isHex")
  val isHex: Boolean,
)


class GizwitsPadSdkModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  private var mReactContext: ReactContext? = null

  override fun getName(): String {
    return NAME
  }
  enum class EventName(val value: String) {
    DeviceDataListener("DeviceDataListener"),
  }
  public var sdkHandler = SdkManager
  val messageListener = object : MessageListener {
    override fun onMessageReceived(message: String) {
      var jsonData = JSONObject();
      println("rnSDK: 收到数据----$message")

      jsonData.put("data", message)
      sendEvent(EventName.DeviceDataListener.name, GizRNCallbackManager.jsonObject2WriteableMap(jsonData))

    }
  }

  fun sendEvent(name:String, data: WritableArray?) {
    mReactContext!!
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(name, data)
  }
  fun sendEvent(name:String, data: WritableMap?) {
    mReactContext!!
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(name, data)
  }

  init {
    mReactContext = reactContext
    sdkHandler.initSdk(reactContext)
    sdkHandler.addMessageListener(messageListener)
  }

  @ReactMethod
  fun set485Port(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizSet485PortParams::class.java)
    config?.let {
      sdkHandler.set485Port(config.index, config.status);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }
  @ReactMethod
  fun get485Port(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizGet485PortParams::class.java)
    config?.let {
      sdkHandler.get485Port(config.index);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }
  @ReactMethod
  fun send485PortMessage(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizSend485PortMessageParams::class.java)
    config?.let {
      sdkHandler.send485PortMessage(config.data, config.isHex);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }
  @ReactMethod
  fun stop485Port(options: ReadableMap, result: Callback) {
    sdkHandler.stop485Port();
    GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
  }


  @ReactMethod
  fun setRelay(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizSetRelayParams::class.java)
    config?.let {
      sdkHandler.setRelay(config.index, config.status);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }
  @ReactMethod
  fun getRelay(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizGetRelayParams::class.java)
    config?.let {
      val v = sdkHandler.getRelay(config.index);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(v))
    }
  }


  @ReactMethod
  fun setLedStatus(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizSetLedStatusParams::class.java)
    config?.let {
      sdkHandler.setLedStatus(config.status);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }
  @ReactMethod
  fun setBreathingLight(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, GizSetBreathingLightParams::class.java)
    config?.let {
      sdkHandler.setBreathingLight(config.index, config.isBreath, config.effect);
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }


  @ReactMethod
  fun factoryReset(options: ReadableMap, result: Callback) {
    sdkHandler.factoryReset();
    GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
  }


  companion object {
    const val NAME = "GizwitsPadSdk"
  }
}
