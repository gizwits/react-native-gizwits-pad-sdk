package com.gizwitspadsdk
import android.app.AispeechManager
import android.app.IAISStatusCallback
import android.content.Context
import android.os.RemoteException
import androidx.core.content.ContextCompat.getSystemService

public interface MessageListener {
    fun onMessageReceived(message: String)
}

public object SdkManager {
    lateinit var mgr: AispeechManager;
    public fun initSdk(context: Context) {
        mgr = getSystemService(context, AispeechManager::class.java) as AispeechManager
        mgr.set485PortMessageListener(115200, object : IAISStatusCallback.Stub() {
            @Throws(RemoteException::class)
            override fun getStatus(s: String) {
                println("485receive: 收到数据----$s")
                receiveMessage(s)
            }
        })
    }

    // 485
    private val listeners = mutableListOf<MessageListener>()
    public fun addMessageListener(listener: MessageListener) {
        listeners.add(listener)
    }

    public fun removeMessageListener(listener: MessageListener) {
        listeners.remove(listener)
    }
    // 模拟接收消息并通知监听器
    fun receiveMessage(message: String) {
        for (listener in listeners) {
            listener.onMessageReceived(message)
        }
    }
    public fun set485Port(index: Int, status: Boolean) {
        mgr.set485Port(index.toString(), status)
    }
    public fun get485Port(index: Int): Boolean {
        return mgr.get485Port(index.toString())
    }
    public fun send485PortMessage(data: String, index: Int, isHex: Boolean) {
        mgr.send485PortMessage(index.toString(), index, isHex)
    }
    public fun stop485Port() {
        mgr.stop485Port()
    }

    // 485


    // 灯光
    public fun setLedStatus(status: Boolean) {
        var v = "1"
        if (status) {
            v = "0"
        }
        mgr.setLedStatus(v)
    }
    public fun setBreathingLight(index: Int, isBreath: Boolean, effect: Int) {
        mgr.setBreathingLight(index.toString(),isBreath, effect)
    }

    // 继电器
    public fun setRelay(index: Int, status: Boolean) {
        mgr.setRelay(index.toString(), status);
    }
    public fun getRelay(index: Int): Boolean {
        return mgr.getRelay(index.toString());
    }

    // 重置
    public fun factoryReset() {
        mgr.factoryReset()
    }
}