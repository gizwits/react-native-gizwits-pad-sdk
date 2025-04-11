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

    // 处理串口数据
    fun handlePortData(s: String) {
        try {
            // 检查数据有效性
            if (s.isEmpty()) {
                Log.w(TAG, "接收到的数据为空")
                return
            }

            // 处理缓存数据
            cacheString += s
            
            // 验证从机地址
            if (!cacheString.startsWith("80")) {
                handleInvalidAddress(s)
                return
            }

            // 检查数据长度是否足够
            if (cacheString.length < 4) {
                return
            }

            // 解析功能码，根据功能码截取数据包
            val functionCode = cacheString.substring(2, 4)
            val packageData = when (functionCode) {
                "03" -> interceptFunction03()
                "10" -> interceptFunction10()
                "14" -> interceptFunction14()
                else -> {
                    handleInvalidFunctionCode(s)
                    return
                }
            }

            // 处理数据包
            if (packageData.isNotEmpty()) {
                // Log.d(TAG, "接收到的数据包: $packageData，缓存数据: $cacheString")
                processPackageData(packageData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理串口数据时发生错误: ${e.message}", e)
            cacheString = ""
            sendErrorToSentry("处理错误", "处理串口数据时发生错误", e.message ?: "未知错误")
        }
    }

    // 发送错误到sentry
    private fun sendErrorToSentry(errorType: String, message: String, data: String) {
        val extraData = mapOf(
            "errorMessage" to message,
            "event" to errorType,
            "data" to data
        )
        sendSentryError(Error(errorType), extraData)
    }

    // 处理无效地址
    private fun handleInvalidAddress(s: String) {
        cacheString = ""
        Log.w(TAG, "从属地址无效，忽略数据: $s")
        sendErrorToSentry("地址错误", "从属地址无效，忽略数据", s)
    }

    // 处理无效功能码
    private fun handleInvalidFunctionCode(s: String) {
        cacheString = ""
        Log.w(TAG, "功能码错误，忽略数据: $s")
        sendErrorToSentry("功能码错误", "功能码错误，忽略数据", s)
    }

    // 截取功能码03包数据
    private fun interceptFunction03(): String {
        if (cacheString.length < 16) return ""
        val packageData = cacheString.substring(0, 16)
        cacheString = cacheString.substring(16)
        return packageData
    }

    // 截取功能码10包数据
    private fun interceptFunction10(): String {
        if (cacheString.length < 14) return ""
        val size = cacheString.substring(12, 14).toInt(16)
        val packageLength = 14 + size * 2 + 4
        if (cacheString.length < packageLength) return ""
        val packageData = cacheString.substring(0, packageLength)
        cacheString = cacheString.substring(packageLength)
        return packageData
    }

    // 截取功能码14包数据
    private fun interceptFunction14(): String {
        if (cacheString.length < 6) return ""
        val size = cacheString.substring(4, 6).toInt(16)
        val packageLength = 6 + size * 2 + 4
        if (cacheString.length < packageLength) return ""
        val packageData = cacheString.substring(0, packageLength)
        cacheString = cacheString.substring(packageLength)
        return packageData
    }

    // 处理数据包
    private fun processPackageData(packageData: String) {
        if (!validateModbusMessage(packageData)) {
            Log.w(TAG, "CRC校验失败，忽略数据: $packageData")
            sendErrorToSentry("CRC校验失败", "CRC校验失败，忽略数据", packageData)
            return
        }

        val functionCode = packageData.substring(2, 4)

        when (functionCode) {
            "10" -> handleWriteMultipleRegisters(packageData)
            "14" -> handleFileRecordRead(packageData)
            "03" -> handleReadHoldingRegisters(packageData)
        }
    }

    // 处理功能码10
    private fun handleWriteMultipleRegisters(packageData: String) {
        try {
            val address = packageData.substring(4, 8).toInt(16)
            val modebusData = packageData.substring(14, packageData.length)
            val hasUpdate = replaceStringAtAddress(address, modebusData)
            
            if (hasUpdate) {
                receiveMessage(packageData)
            } else {
                // Log.d(TAG, "设备上报数据，但是没有变更, 地址: $address, 数据: $modebusData")
            }

            val hexString = packageData.substring(0, 12)
            val rawData = appendCRC(hexString)
            send485PortMessage(rawData, true)
            
            // Log.d(TAG, "功能码10, 起始地址：$address, 接收数据: $packageData, 回复: $rawData")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling function code 10: ${e.message}", e)
        }
    }

    // 处理功能码14
    private fun handleFileRecordRead(packageData: String) {
        try {
            val bytes = firmwareBytes ?: run {
                Log.w(TAG, "No firmware data available")
                return
            }

            // 解析请求参数
            val referenceType = packageData.substring(6, 8).toInt(16)  // 参考类型
            val fileNumber = packageData.substring(8, 12).toInt(16)    // 文件号
            val recordNumber = packageData.substring(12, 16).toInt(16)  // 记录号（起始地址）
            val recordLength = packageData.substring(16, 20).toInt(16)  // 记录长度

            // Log.d(TAG, "读取固件请求, 参考类型: $referenceType, 文件号: $fileNumber, 记录号: $recordNumber, 记录长度: $recordLength")

            // 计算数据范围
            val startAddress = recordNumber
            val dataLength = recordLength * 2
            val startIndex = startAddress * dataLength
            val endIndex = (startAddress + 1) * dataLength

            if (startIndex < 0 || endIndex > bytes.size) {
                Log.w(TAG, "Request range exceeds firmware size")
                return
            }

            // 提取并处理固件数据
            val firmwareData = bytes.copyOfRange(startIndex, endIndex)
            val dataHex = firmwareData.joinToString("") { "%02X".format(it) }

            // 构建响应
            val response = buildString {
                append("80")  // 从机地址
                append("14")  // 功能码
                append((dataLength + 2).toString(16).padStart(2, '0'))  // 总长度
                append((dataLength + 1).toString(16).padStart(2, '0'))  // 数据记录长度
                append("06")  // 参考类型
                append(dataHex)  // 记录数据
            }

            val finalResponse = appendCRC(response)
            send485PortMessage(finalResponse, true)
            receiveMessage(packageData)

            // Log.d(TAG, "功能码14, 接收数据: $packageData, 回复: $finalResponse, 固件开始地址: $startAddress, 固件长度: $dataLength")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling file record read request: ${e.message}", e)
        }
    }

    // 处理功能码03
    private fun handleReadHoldingRegisters(packageData: String) {
        try {
            val powerUp = getBitInHexString(7, 0)
            if (powerUp == 1) {
                // Log.w(TAG, "当前处于上电状态，先不回复")
                return
            }

            val len = packageData.substring(8, 12).toInt(16)
            val address = packageData.substring(4, 8).toInt(16)
            var hexString = getSubstringFromAddress(address, len)

            hexString = "8003${(len * 2).toHexString().padStart(2, '0')}$hexString"
            hexString = appendCRC(hexString)
            send485PortMessage(hexString, true)

            // Log.d(TAG, "功能码03, 起始地址：$address, 寄存器数目：$len, 接收数据: $packageData, 回复: $hexString")

            // 移除已发送的命令索引,  中控读走了 address 开始 len 长度的数据, 把 address 到 len 的 index 删除
            CoroutineScope(Dispatchers.IO).launch {
                removeReadySendCmdIndex(address, address + len)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling function code 03: ${e.message}", e)
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
            // Log.d(TAG, "Send485PortMessage: $data")
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
                // Log.d(TAG, "读取的串口数据: $dataString")
                CoroutineScope(Dispatchers.IO).launch {
                    handlePortData(dataString)
                }
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