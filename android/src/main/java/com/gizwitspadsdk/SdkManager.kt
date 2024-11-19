package com.gizwitspadsdk
import android.app.AispeechManager
import android.app.IAISStatusCallback
import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.internal.toHexString
import kotlinx.coroutines.sync.withLock


public interface MessageListener {
    fun onMessageReceived(message: String)
}

public object SdkManager {
    lateinit var mgr: AispeechManager;
    var readySendCmdIndex: MutableList<Int> = mutableListOf()
    var cacheString = ""
    private val mutex = Mutex() 
    var modbusData = StringBuilder(0)
    fun createInitModbusData () {
        val length = 70000 * 4
        val stringBuilder = StringBuilder(length)
        repeat(length) {
            stringBuilder.append('0')
        }
        // TODO 从本地存储读取
        modbusData = stringBuilder

        setPowerUp();
    }

    fun getBitInHexString(index: Int, bitLocation: Int): Int {
        require(index >= 0 && index * 4 + 3 < modbusData.length) { "Index out of bounds for modbusData" }
        require(bitLocation in 0..15) { "bitLocation must be between 0 and 15 for a 16-bit value" }

        // Get the 4-character hex group (16-bit value) at the specified index
        val byteHex = modbusData.substring(index * 4, index * 4 + 4)
        // Convert the hex group to an integer (16-bit value)
        val byteValue = byteHex.toInt(16)

        // Extract the specific bit at bitLocation
        return (byteValue shr bitLocation) and 0x01
    }

    fun setBitInHexString(index: Int, bitLocation: Int, bitValue: Int) {
        require(bitValue == 0 || bitValue == 1) { "bitValue must be 0 or 1" }
        require(index >= 0 && index * 4 + 3 < modbusData.length) { "Index out of bounds for modbusData" }
        require(bitLocation in 0..15) { "bitLocation must be between 0 and 15 for a 16-bit value" }

        // Get the 4-character hex group (16-bit value) at the specified index
        val byteHex = modbusData.substring(index * 4, index * 4 + 4)
        // Convert the hex group to an integer (16-bit value)
        var byteValue = byteHex.toInt(16)

        // Modify the specific bit at bitLocation
        if (bitValue == 1) {
            byteValue = byteValue or (1 shl bitLocation)
        } else {
            byteValue = byteValue and (1 shl bitLocation).inv()
        }

        // Convert the modified 16-bit value back to a 4-character hex string
        val modifiedHex = byteValue.toString(16).padStart(4, '0').uppercase()

        // Replace the old hex group in the StringBuilder
        modbusData.replace(index * 4, index * 4 + 4, modifiedHex)
    }

    fun replaceStringAtAddress(
        address: Int,
        replacement: String,
        force: Boolean = false
    ): Boolean {
        // 确保address和replacement的长度合法
        if (address < 0 || address + replacement.length > modbusData.length) {
            throw IndexOutOfBoundsException("Address out of bounds or replacement string too long.")
        }

        val chunks = replacement.chunked(4)
        var hasUpdate = false

        for ((index, chunk) in chunks.withIndex()) {
            val currentIndex = address + index
            val currentAddress = currentIndex * 4
            // 当前地址不存在于readySendCmdIndex中，并且内容不相同
            // force 是用于强制更新
            val oldString = modbusData.substring(currentAddress, currentAddress + chunk.length)
            if ((!readySendCmdIndex.contains(currentIndex) && oldString != chunk) || force) {
                modbusData.replace(currentAddress, currentAddress + chunk.length, chunk)
                hasUpdate = true
            } else {
                println("忽略更新 ${currentAddress} 的数据")
            }
        }
        return hasUpdate;
    }

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
        val functionCode = s.substring(2,4);
        var isEnd = false;
        if (functionCode == "03") {
            isEnd = true
        }
        if (functionCode == "10") {
            val size = s.substring(12,14).toInt(16)
            val dataSize = (s.length - 14 - 4) / 2
            if (dataSize == size){
                isEnd = true
            } else {
                isEnd = false
            }
        }
        return isEnd;

    }

    fun getSubstringFromAddress(address: Int, len: Int): String {
        // 检查 address 和 len 的合法性，避免越界
        val start = address * 4
        val end = start + len * 4
        if (start < 0 || len < 0 || end > modbusData.length) {
            throw IndexOutOfBoundsException("Invalid address or length.")
        }
        // 返回从 address 开始，长度为 len 的子字符串

        return modbusData.substring(start, end)
    }
    public suspend fun removeReadySendCmdIndex(start: Int, end: Int) {
        mutex.withLock {
            readySendCmdIndex.removeAll { it in start..end }
        }
    }

    fun setPowerUp() {
        setBitInHexString(7, 0, 1)
    }

    public fun initSdk(context: Context) {
        createInitModbusData();
        mgr = getSystemService(context, AispeechManager::class.java) as AispeechManager

        mgr.set485PortMessageListener(9600, object : IAISStatusCallback.Stub() {
            @Throws(RemoteException::class)
            override fun getStatus(s: String) {
                var isEnd = false

                if (s.substring(0, 2).equals("80")) {
                    cacheString = s
                    isEnd = checkIsEnd(cacheString)
                }else {
                    cacheString += s
                    isEnd = checkIsEnd(cacheString)
                }

                if (isEnd) {
                    val functionCode = cacheString.substring(2, 4)
                    val address = cacheString.substring(4, 8).toInt(16)

                    if (functionCode.equals("10")) {
                        val hexString = cacheString.substring(0,12)
                        val modebusData = cacheString.substring(14, cacheString.length)

                        val crc = calculateCRC(hexString)
                        val rawData = hexString + crc
                        // 替换本地缓存
                        val hasUpdate = replaceStringAtAddress(address, modebusData)
                        if (hasUpdate) {
                            receiveMessage(cacheString)
                            println("设备上报数据 原始数据： ${cacheString}")
                            println("设备上报数据 地址： ${address} ${modebusData}")
                        }
                        send485PortMessage(rawData, true)
//                        println("设备上报数据 回复 ${rawData}")

                    }
                    if (functionCode.equals("03")) {
                        receiveMessage(cacheString)
                        // 如果当前上电状态是1 先不回复
                        val powerUp = getBitInHexString(7,0)
                        if (powerUp == 1) {
                            println("当前处于上电状态，先不回复")
                            return;
                        }

                        val len = cacheString.substring(8, 12).toInt(16)
                        println("设备查询数据: $cacheString, len: ${len} address: ${address}")
                        var hexString = getSubstringFromAddress(address, len)
                        hexString = "8003${(len * 2).toHexString().padStart(2, '0')}${hexString}"
//
                        hexString = "${hexString}${calculateCRC(hexString)}"

                        println("设备查询数据 回复: $hexString")

                        send485PortMessage(hexString, true)

                        // 中控读走了 address 开始 len 长度的数据
                        // 把 address 到 len的index 删除
                        GlobalScope.launch {
                            removeReadySendCmdIndex(address, address + len)
                        }
                    }

                    cacheString = "";
                }
            }
        })

    }

    // 485
    private var listeners: MessageListener? = null
    public fun addMessageListener(listener: MessageListener) {
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
//        mgr.set485Port(index.toString(), status)
    }
    public fun get485Port(index: Int): Boolean {
//        return mgr.get485Port(index.toString())
        return true
    }
    public suspend fun updateModbusData(data: List<NumberStringPair>) {
        // 替换数据
        data.map {
            println("updateModbusData ${it.index} ${it.text}")
            mutex.withLock { // 确保线程安全
                replaceStringAtAddress(it.index, it.text, true)
                // 仅在 readySendCmdIndex 中添加唯一的索引
                // 因为7是上电，可以更精细控制
                if (!readySendCmdIndex.contains(it.index)) {
                    readySendCmdIndex.add(it.index) // 记录索引
                }
            }
        }
    }
    public fun send485PortMessage(data: String, isHex: Boolean) {

        println("send485PortMessage run")
        Thread.sleep(40)
        try {
            // 调用服务的代码
            mgr.send485PortMessage(data, 9600, isHex)
        } catch (e: DeadObjectException) {
            // 记录异常日志
            println("SdkManager DeadObjectException: Service might be down")
            // 尝试恢复或重新连接逻辑
        }
//        Thread.sleep(20)

//        Thread {
//            // 在新线程中运行的代码
//            println("send485PortMessage run")
//            Thread.sleep(20)
//            mgr.send485PortMessage(data, 9600, isHex)
//            Thread.sleep(20)
//        }.start()
    }
    public fun stop485Port() {
//        mgr.stop485Port()
    }

    // 485


    // 灯光
    public fun setLedStatus(status: Boolean) {
        var v = "1"
        if (status) {
            v = "0"
        }
//        mgr.setLedStatus(v)
    }
    public fun setBreathingLight(index: Int, isBreath: Boolean, effect: Int) {
//        mgr.setBreathingLight(index.toString(),isBreath, effect)
    }

    // 继电器
    public fun setRelay(index: Int, status: Boolean) {
//        mgr.setRelay(index.toString(), status);
    }
    public fun getRelay(index: Int): Boolean {
//        return mgr.getRelay(index.toString());
        return true
    }

    // 重置
    public fun factoryReset() {
//        mgr.factoryReset()
    }
}