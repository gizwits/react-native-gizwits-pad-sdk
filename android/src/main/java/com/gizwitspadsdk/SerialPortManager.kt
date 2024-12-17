package com.outes.wuheng

import com.gizwitspadsdk.sendSentryError
import jssc.SerialPort
import jssc.SerialPort.PURGE_RXCLEAR
import jssc.SerialPort.PURGE_TXCLEAR
import jssc.SerialPortException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.IOException


object SerialPortManager {
    private var serialPort = SerialPort("/dev/ttyS6")
    private var isRunning = false
    private var listener: ((ByteArray) -> Unit)? = null
    private var trying = false

    fun setListener(listener: (ByteArray) -> Unit) {
        this.listener = listener
    }

    suspend fun openPort(): Boolean {
        return try {
            println("start open port")
            serialPort.openPort()
            serialPort.setParams(SerialPort.BAUDRATE_9600, // 设置波特率
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE)
            serialPort.purgePort(PURGE_RXCLEAR or PURGE_TXCLEAR)
            true
        } catch (e: SerialPortException) {
            println("open port error")
            e.printStackTrace()
            reconnect()
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "open port error"
            )
            sendSentryError(e, extraData)
            false
        }
    }

    suspend fun closePort() {
        try {
            serialPort.closePort()
        } catch (e: SerialPortException) {
            e.printStackTrace()
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "close port error"
            )
            sendSentryError(e, extraData)
        }

        restartSerialPortModule();
    }

    suspend private fun restartSerialPortModule() {
        try {
            val process = Runtime.getRuntime().exec("su") // 获取 root 权限
            val os = DataOutputStream(process.outputStream)
            println("运行重启串口")
            // 执行重启串口的命令
            os.writeBytes("echo 0 > /sys/class/tty/ttyS6/device/enable\n") // 禁用串口
            os.flush()
            delay(100)
            os.writeBytes("echo 1 > /sys/class/tty/ttyS6/device/enable\n") // 启用串口
            os.flush()
            os.close()
            process.waitFor()
            println("运行重启串口结束")

        } catch (e: IOException) {
            e.printStackTrace()
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "restartSerialPortModule error"
            )
            sendSentryError(e, extraData)
        } catch (e: InterruptedException) {
            e.printStackTrace()
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "restartSerialPortModule error"
            )
            sendSentryError(e, extraData)
        }
    }
    fun sendData(data: ByteArray) {
        try {
            serialPort.writeBytes(data)
        } catch (e: SerialPortException) {
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "send data error"
            )
            sendSentryError(e, extraData)
        }
    }

    fun startReading() {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    val data = serialPort.readBytes()
                    data?.let {
                        listener?.invoke(it)
                    }
                    delay(10) // 控制读取频率
                } catch (e: SerialPortException) {
                    e.printStackTrace()
                    // 尝试重新连接
                    reconnect()
                    val extraData = mapOf(
                        "errorMessage" to e.toString(),
                        "event" to "read error, try reconnect"
                    )
                    println("read error, try reconnect")
                    sendSentryError(e, extraData)
                    delay(3 * 1000)
                }
            }
        }
    }

    private suspend fun reconnect() {
        if (trying) {
            println("is trying reconnect")
            return;
        }
        trying = true
        while (trying) { // 尝试重新打开串口
            closePort() // 先关闭串口
            delay(100)
            val res = openPort()
            if (res) {
                // 连接成功
                trying = false
            } else {
                delay(1000) // 等待一秒后重试
            }
        }
    }
}