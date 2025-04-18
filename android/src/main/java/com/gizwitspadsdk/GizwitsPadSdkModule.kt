package com.gizwitspadsdk

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.gizwits.reactnativegizwitssdkv5.GizRNCallbackManager
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

data class NumberStringPair(
  @SerializedName("index")
  val index: Int,
  @SerializedName("text")
  val text: String
)
data class GizSetLedStatusParams(
  @SerializedName("status")
  val status: Boolean,
)
data class  UpdateModbusDataParams(
  @SerializedName("data")
  var data: List<NumberStringPair>
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
data class GizStartOtaUpdateParams(
  @SerializedName("filePath")
  val filePath: String,
  @SerializedName("softVersion")
  val softVersion: String,
)


class GizwitsPadSdkModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext),
  LifecycleEventListener {

  private var mReactContext: ReactContext? = null
  var isInit = false

  override fun getName(): String {
    return NAME
  }
  enum class EventName(val value: String) {
    DeviceDataListener("DeviceDataListener"),
  }

  override fun onHostResume() {
    TODO("Not yet implemented")
  }

  override fun onHostPause() {
    TODO("Not yet implemented")
  }

  override fun onHostDestroy() {
    sdkHandler.stop485Port()
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  fun install(): Boolean {
    try {
      System.loadLibrary("gizwitsjsi") // 确保这里的名称与 CMake 构建的库名称一致

      val context = reactApplicationContext
      nativeInstall(
        context.javaScriptContextHolder!!.get(),
        context.filesDir.absolutePath
      )
      isInit = true
      return true
    } catch (exception: Exception) {
      return false
    }
  }
  fun getVersion_c(): String {
    return "version"
  }
  fun sendData_c(data: String): Boolean {
    sdkHandler.send485PortMessage(data, true)
    return true
  }


  public var sdkHandler = SdkManager
  val messageListener = object : MessageListener {
    override fun onMessageReceived(message: String) {
//      if (isInit){
//        mReactContext!!.getJavaScriptContextHolder()
//          ?.let { emitJSI(it.get(), "DeviceDataListener", message) };
//      }

      var jsonData = JSONObject();

      jsonData.put("data", message)
      sendEvent(EventName.DeviceDataListener.name, GizRNCallbackManager.jsonObject2WriteableMap(jsonData))

    }
  }

  private external fun nativeInstall(jsiPtr: Long, docDir: String)
  private external fun emitJSI(jsiPtr: Long, name: String, data: String)

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

  }
  @ReactMethod
  fun initSdk(options: ReadableMap, result: Callback) {
    sdkHandler.addMessageListener(messageListener)
    mReactContext?.let { sdkHandler.initSdk(it) }
    GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
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
  fun updateModbusData(options: ReadableMap, result: Callback) {
    var config = RNGizParamsChecker.check(options, result, UpdateModbusDataParams::class.java)
    config?.let {
      GlobalScope.launch {
        sdkHandler.updateModbusData(config.data);
      }
      GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
  }

  @ReactMethod
  fun startOtaUpdate(options: ReadableMap, result: Callback) {
    val config = RNGizParamsChecker.check<GizStartOtaUpdateParams>(options, result, GizStartOtaUpdateParams::class.java)
    config?.let {
        sdkHandler.startOtaUpdate(config.filePath, config.softVersion)
        GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
    }
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

  @ReactMethod
  fun enableDebug(options: ReadableMap, result: Callback) {
    LogUtils.enableDebug()
    GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
  }

  @ReactMethod
  fun disableDebug(options: ReadableMap, result: Callback) {
    LogUtils.disableDebug()
    GizRNCallbackManager.callbackWithResult(callback = result, result = Result.success(Unit))
  }

  companion object {
    const val NAME = "GizwitsPadSdk"
  }
}
