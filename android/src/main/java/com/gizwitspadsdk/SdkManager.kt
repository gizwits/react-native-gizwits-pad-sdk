package com.gizwitspadsdk
import android.app.AispeechManager
import android.app.IAISStatusCallback
import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import androidx.core.content.ContextCompat.getSystemService
import com.gizwitspadsdk.GizException


public interface MessageListener {
    fun onMessageReceived(message: String)
}

public object SdkManager {
    lateinit var mgr: AispeechManager;
    var cacheString = ""
    private val lock = Any()

    fun calculateCRC(hexString: String): String {
        var crc = 0xFFFF

        for (i in 0 until hexString.length step 2) {
            val byteStr = hexString.substring(i, i + 2)
            val byte = byteStr.toIntOrNull(16) ?: 0
            crc = crc xor byte

            for (j in 0 until 8) {
                val flag = crc and 0x0001
                crc = crc ushr 1
                if (flag == 1) {
                    crc = crc xor 0xA001
                }
            }
        }

        // 将 CRC 转换为十六进制字符串，长度为4，并将字节顺序反转
        return crc.toString(16).padStart(4, '0').chunked(2).reversed().joinToString("")
    }
    fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val byteArray = ByteArray(len / 2)

        for (i in 0 until len step 2) {
            val byte = hexString.substring(i, i + 2).toInt(16)
            byteArray[i / 2] = byte.toByte()
        }

        return byteArray
    }

    fun checkIsEnd (s:String):Boolean {
        val size = s.substring(12,14).toInt(16)
        val dataSize = (s.length - 14 - 4) / 2
        println("checkIsEnd ${s} ${size} ${dataSize}")
        if (dataSize == size){
            return true
        } else {
            return false
        }

    }

    public fun initSdk(context: Context) {
        mgr = getSystemService(context, AispeechManager::class.java) as AispeechManager
        mgr.stop485Port()
        mgr.set485PortMessageListener(9600, object : IAISStatusCallback.Stub() {
            @Throws(RemoteException::class)
            override fun getStatus(s: String) {
                println("485receive: 收到数据----${s}")
                var isEnd = false
                if (s.substring(0, 2).equals("80")) {
                    val functionCode = s.substring(2, 4)
                    if (functionCode.equals("10")) {
                        cacheString = s
                        isEnd = checkIsEnd(cacheString)
                    }
                }else {
                    cacheString += s
                    isEnd = checkIsEnd(cacheString)
                }

                if (isEnd) {
                    val hexString = cacheString.substring(0,12)
                    val crc = calculateCRC(hexString)
                    val rawData = hexString + crc
                    println("485receive rawData: $rawData")
                    send485PortMessage(rawData, true)
                }
                receiveMessage(s)
            }
        })
    }

    // 485
    private var listeners: MessageListener? = null
    public fun addMessageListener(listener: MessageListener) {
//        listeners.add(listener)
        listeners = listener
    }

//    public fun removeMessageListener(listener: MessageListener) {
//        listeners.remove(listener)
//    }
    // 模拟接收消息并通知监听器
    fun receiveMessage(message: String) {
        listeners!!.onMessageReceived(message)
    }
    public fun set485Port(index: Int, status: Boolean) {
        mgr.set485Port(index.toString(), status)
    }
    public fun get485Port(index: Int): Boolean {
        return mgr.get485Port(index.toString())
    }
    public fun send485PortMessage(data: String, isHex: Boolean) {
        mgr.set485Port("1", true)
        Thread.sleep(50)
        mgr.send485PortMessage(data, 9600, isHex)
        Thread.sleep(50)
        mgr.set485Port("1", false)
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