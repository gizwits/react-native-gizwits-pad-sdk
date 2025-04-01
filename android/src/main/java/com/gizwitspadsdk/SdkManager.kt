package com.gizwitspadsdk
import android.annotation.SuppressLint
import android.app.AispeechManager
import android.app.IAISStatusCallback
import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.facebook.react.bridge.Promise
import com.outes.wuheng.SerialPortManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.internal.toHexString
import kotlinx.coroutines.sync.withLock
import okio.Utf8
import java.io.File

public interface MessageListener {
    fun onMessageReceived(message: String)
}

// 用于日志记录Tag
private const val TAG = "GizwitsPadSDK - SdkManager"
public object SdkManager {
//    lateinit var mgr: AispeechManager;
    private var firmwareBytes: ByteArray? = null
    private var softVersion: String? = null
    private val mutex = Mutex()
    var readySendCmdIndex: MutableList<Int> = mutableListOf()
    var cacheString = ""
    var modbusData = StringBuilder(70000 * 4)
    var androidId = ""
    fun createInitModbusData () {
        modbusData.clear()
        modbusData.append("0".repeat(70000 * 4))
        setPowerUp()
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
            // throw IndexOutOfBoundsException("Address out of bounds or replacement string too long.")
            return false
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
                // println("忽略更新 ${currentAddress} 的数据")
            }
        }
        return hasUpdate;
    }

    // filePath 是本地文件路径
    fun startOtaUpdate(filePath: String, softVersion: String) {
        val currentSoftVersion = this.softVersion;
        Log.d(TAG, "StartOtaUpdate filePath: $filePath, softVersion: $softVersion, currentSoftVersion: $currentSoftVersion")
        if (softVersion == this.softVersion) {
            Log.w(TAG, "当前固件版本与请求的固件版本相同，跳过更新")
            return
        }
        try {
            // 1. 读取固件文件
            val firmwareFile = File(filePath)
            if (!firmwareFile.exists()) {
                throw Exception("Firmware file not found")
            }

            // 2. 读取文件内容到内存
            val bytes = firmwareFile.readBytes()
            this.firmwareBytes = bytes
            this.softVersion = softVersion

            Log.d(TAG, "OTA update started with firmware size: ${bytes.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting OTA update: ${e.message}", e)
            throw e
        }
    }

//    fun calculateCRC(hexString: String): String {
//        var crc = 0xFFFF
//
//        for (i in 0 until hexString.length step 2) {
//            val byteStr = hexString.substring(i, i + 2)
//            val byte = byteStr.toIntOrNull(16) ?: 0
//            crc = crc xor byte
//
//            for (j in 0 until 8) {
//                val flag = crc and 0x0001
//                crc = crc ushr 1
//                if (flag == 1) {
//                    crc = crc xor 0xA001
//                }
//            }
//        }
//
//        // 将 CRC 转换为十六进制字符串，长度为4，并将字节顺序反转
//        return crc.toString(16).padStart(4, '0').chunked(2).reversed().joinToString("")
//    }

    /**
     * 计算 Modbus CRC-16 校验码
     * @param hexString 十六进制字符串
     * @return 校验码的十六进制字符串（低字节在前，高字节在后）
     * @throws IllegalArgumentException 当输入字符串格式不正确时
     */
    fun calculateCRC(hexString: String): String {
        // 输入验证
        if (hexString.isEmpty()) {
            throw IllegalArgumentException("Input hexString cannot be empty")
        }

        if (hexString.length % 2 != 0) {
            throw IllegalArgumentException("Input hexString must have even length")
        }

        if (!hexString.matches(Regex("^[0-9A-Fa-f]+$"))) {
            throw IllegalArgumentException("Input hexString contains invalid characters")
        }

        try {
            // CRC-16/MODBUS 计算
            var crc = 0xFFFF

            // 按字节处理数据
            for (i in 0 until hexString.length step 2) {
                val byteStr = hexString.substring(i, i + 2)
                val byte = byteStr.toInt(16)
                crc = crc xor byte

                // 对每个位进行处理
                repeat(8) {
                    val flag = crc and 0x0001
                    crc = crc ushr 1
                    if (flag == 1) {
                        crc = crc xor 0xA001  // MODBUS 多项式 0xA001 (反转的 0x8005)
                    }
                }
            }

            // 格式化结果
            // 低字节在前，高字节在后（Little-Endian）
            val lowByte = (crc and 0xFF).toString(16).padStart(2, '0')
            val highByte = (crc ushr 8).toString(16).padStart(2, '0')

            return "$lowByte$highByte"
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to calculate CRC: ${e.message}", e)
        }
    }

    /**
     * 验证带CRC的Modbus消息
     * @param message 完整的Modbus消息（包含CRC）
     * @return 是否验证通过
     */
    fun validateModbusMessage(message: String): Boolean {
        if (message.length < 4) {
            return false
        }

        try {
            // 分离消息体和CRC
            val data = message.substring(0, message.length - 4)
            val receivedCrc = message.substring(message.length - 4)

            // 计算CRC并比较
            return receivedCrc.equals(calculateCRC(data), ignoreCase = true)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 为Modbus消息添加CRC
     * @param message Modbus消息（不含CRC）
     * @return 带CRC的完整消息
     */
    fun appendCRC(message: String): String {
        val crc = calculateCRC(message)
        return message + crc
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
        if (s.length < 4) return false
        val functionCode = s.substring(2,4);
        var isEnd = false;
        if (functionCode == "03") {
            if (s.length < 8) return false
            isEnd = true
        }
        if (functionCode == "10") {
            if (s.length < 14) return false
            val size = s.substring(12,14).toInt(16)
            val dataSize = (s.length - 14 - 4) / 2
            if (dataSize == size){
                isEnd = true
            } else {
                isEnd = false
            }
        }
        if (functionCode == "14") {
            if (s.length < 6) return false
            val size = s.substring(4,6).toInt(16);
            val dataSize = (s.length - 6 - 4) / 2
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
            return ""
            // throw IndexOutOfBoundsException("Invalid address or length.")
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

    fun handlePortData(s: String) {
        var isEnd = false

        // 检查接收到的数据长度
        if (s.length < 2) {
            Log.w(TAG, "接收到的数据长度不足，忽略该数据")
            val extraData = mapOf(
                "errorMessage" to "接收到的数据长度不足，忽略该数据",
                "event" to "len error",
                "data" to cacheString
            )
            sendSentryError(Error("长度错误"), extraData)
            return // 忽略数据
        }

        if (s.substring(0, 2).equals("80")) {
            cacheString = s
            isEnd = checkIsEnd(cacheString)
        } else {
            cacheString += s
            isEnd = checkIsEnd(cacheString)
        }

        // 检查缓存数据的长度
        if (cacheString.length < 12) { // 根据需要设置最小长度
            val message = "缓存数据长度不足，当前长度: ${cacheString.length}，忽略该数据"
            Log.w(TAG, message)
            val extraData = mapOf(
                "errorMessage" to message,
                "event" to "len error",
                "data" to cacheString
            )
            sendSentryError(Error("长度错误"), extraData)
            return // 忽略数据
        }

        Log.d(TAG, "接收到的数据: isEnd: $isEnd, cacheString:$cacheString")
        if (isEnd) {
            if (!cacheString.substring(0, 2).equals("80")) {
                cacheString = "";
                Log.w(TAG, "非本从机地址, 忽略该数据，接收到的数据: $cacheString")
                val extraData = mapOf(
                    "errorMessage" to "非本从机地址数据",
                    "event" to "从机地址校验失败",
                    "data" to cacheString
                )
                sendSentryError(Error("从机地址校验失败"), extraData)
                return
            }
            if (!validateModbusMessage(cacheString)) {
                cacheString = ""
                Log.w(TAG, "CRC校验失败, 忽略该数据，接收到的数据: $cacheString")
                val extraData = mapOf(
                    "errorMessage" to "CRC校验失败",
                    "event" to "CRC校验失败",
                    "data" to cacheString
                )
                sendSentryError(Error("CRC校验失败"), extraData)
                return
            }
            val functionCode = cacheString.substring(2, 4)
            val address = cacheString.substring(4, 8).toInt(16)
            Log.d(TAG, "接收到的数据: $cacheString, functionCode: $functionCode")
            when (functionCode) {
                "14" -> {  // 读文件记录
                    try {
                        // 解析请求
                        val bytes = firmwareBytes
                        if (bytes == null) {
                            Log.w(TAG, "No firmware data available")
                            return
                        }

                        // 解析子请求参数
                        val subReqRefType = cacheString.substring(6, 8).toInt(16)  // 应该是 0x06
                        val fileNumber = cacheString.substring(8, 12).toInt(16)    // 文件号
                        val recordNumber = cacheString.substring(12, 16).toInt(16)  // 记录号（起始地址）
                        val recordLength = cacheString.substring(16, 20).toInt(16)  // 记录长度

                        Log.i(TAG, "Read file record request: fileNumber=$fileNumber, recordNumber=$recordNumber, recordLength=$recordLength")

                        // 计算需要读取的数据长度
                        val startAddress = recordNumber
                        val dataLength = recordLength * 2 // 每个字节对应两个十六进制字符

                        val startIndex = startAddress * dataLength
                        val endIndex = (startAddress + 1) * dataLength
                        if (startIndex < 0 || endIndex > bytes.size) {
                            Log.w(TAG, "Request range exceeds firmware size")
                            return
                        }

                        // 提取固件数据
                        val firmwareData = bytes.copyOfRange(startIndex, endIndex)
                        val dataHex = firmwareData.joinToString("") { "%02X".format(it) }

                        // 构建响应
                        val response = buildString {
                            append("80")  // 从机地址
                            append("14")  // 功能码

                            // 响应数据总长度（不包含CRC） = 数据内容长度 + 1(参考类型字节长度) + 1(数据记录字节长度)
                            append((dataLength + 1 + 1).toString(16).padStart(2, '0'))
                            append((dataLength + 1).toString(16).padStart(2, '0'))  // 数据记录长度 = 数据内容长度 + 1(参考类型字节长度)
                            append("06")  // 参考类型
                            append(dataHex)  // 记录数据
                        }

                        // 添加CRC校验
                        val finalResponse = appendCRC(response)

                        // 发送响应
                        send485PortMessage(finalResponse, true)

                        // 客户需求，需要把读取文件信息上报应用层
                        receiveMessage(cacheString)

                        Log.i(TAG, "功能码14，接收数据: ${cacheString}, 回复: ${finalResponse}")
                        Log.i(TAG, "Sent firmware data: startAddress=$startAddress, length=$dataLength")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling file record read request: ${e.message}", e)
                    }
                }
                "10" -> {
                    try {
                        val modebusData = cacheString.substring(14, cacheString.length)
                        // 替换本地缓存
                        val hasUpdate = replaceStringAtAddress(address, modebusData)
                        if (hasUpdate) {
                            receiveMessage(cacheString)
                        } else {
                            Log.i(TAG, "设备上报数据，但是没有变更: ${address} ${modebusData}")
                        }
                        val hexString = cacheString.substring(0, 12)
                        val rawData = appendCRC(hexString)
                        send485PortMessage(rawData, true)
                        Log.i(TAG, "功能码10，起始地址：${address}，接收数据: ${cacheString}, 回复: ${rawData}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling function code 10: ${e.message}", e)
                    }
                }
                "03" -> {
                    try {
                        // 不需要上报，本地处理
                        // receiveMessage(cacheString)
                        // 如果当前上电状态是1 先不回复
                        val powerUp = getBitInHexString(7, 0)
                        if (powerUp == 1) {
                            Log.w(TAG, "当前处于上电状态，先不回复")
                            return
                        }

                        val len = cacheString.substring(8, 12).toInt(16)
                        var hexString = getSubstringFromAddress(address, len)

                        hexString = "8003${(len * 2).toHexString().padStart(2, '0')}${hexString}"
                        hexString = appendCRC(hexString)
                        send485PortMessage(hexString, true)

                        Log.i(TAG, "功能码03，起始地址：${address}，寄存器数目：${len}，接收数据: ${cacheString}, 回复: ${hexString}")

                        // 中控读走了 address 开始 len 长度的数据
                        // 把 address 到 len的index 删除
                        GlobalScope.launch {
                            removeReadySendCmdIndex(address, address + len)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling function code 03: ${e.message}", e)
                    }
                }
            }

            cacheString = ""
        }
    }

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    public fun initSdk(context: Context) {
        createInitModbusData();
        androidId = getAndroidId(context)

        GlobalScope.launch {
            initSerial();
        }


        // 测试串口重连
//        CoroutineScope(Dispatchers.Main).launch {
//            while (true) {
//                // 执行您的任务
//                SerialPortManager.closePort()
//
//                delay(30 * 1000)
//            }
//        }
//        mgr = getSystemService(context, AispeechManager::class.java) as AispeechManager
//
//        mgr.set485PortMessageListener(9600, object : IAISStatusCallback.Stub() {
//            @Throws(RemoteException::class)
//            override fun getStatus(s: String) {
//                handlePortData(s)
//            }
//        })

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
            // println("updateModbusData ${it.index} ${it.text}")
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
        try {
            SerialPortManager.sendData(data.hexStringToByteArray())
            Log.d(TAG, "Send485PortMessage: $data")
        } catch (e: DeadObjectException) {
            // 记录异常日志
            Log.e(TAG, "SdkManager DeadObjectException: Service might be down", e)
            // 尝试恢复或重新连接逻辑
        }

    }
    public fun stop485Port() {
        GlobalScope.launch {
            SerialPortManager.closePort()
        }
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

    suspend fun initSerial() {

        if (SerialPortManager.openPort()) {
            SerialPortManager.setListener { data ->
                // 处理接收到的数据
                val dataString = data.toHexString()
                println("Received data: $dataString")
                handlePortData(dataString)
            }
            SerialPortManager.startReading()
        }
    }
}

fun ByteArray.toHexString(): String {
    return joinToString("") { String.format("%02x", it) }.toUpperCase()
}
fun String.hexStringToByteArray(): ByteArray {
    val len = this.length
    val data = ByteArray(len / 2)
    for (i in 0 until len step 2) {
        data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
    }
    return data
}