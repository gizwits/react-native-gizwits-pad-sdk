package com.outes.wuheng

import com.gizwitspadsdk.sendSentryError
import jssc.SerialPort
import jssc.SerialPortException
import kotlinx.coroutines.*

class SerialPortManager(private val portName: String) {
    private val serialPort = SerialPort(portName)
    private var isRunning = false
    private var listener: ((ByteArray) -> Unit)? = null

    fun setListener(listener: (ByteArray) -> Unit) {
        this.listener = listener
    }

    fun openPort(): Boolean {
        return try {
            serialPort.openPort()
            serialPort.setParams(SerialPort.BAUDRATE_9600, // 设置波特率
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE)
            true
        } catch (e: SerialPortException) {
            e.printStackTrace()
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "open port error"
            )
            sendSentryError(e, extraData)
            false
        }
    }

    fun closePort() {
        isRunning = false
        try {
            if (serialPort.isOpened) {
                serialPort.closePort()
            }
        } catch (e: SerialPortException) {
            e.printStackTrace()
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "close port error"
            )
            sendSentryError(e, extraData)
        }
    }

    fun sendData(data: ByteArray) {
        try {
            if (serialPort.isOpened) {
                serialPort.writeBytes(data)
            }
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
                    if (serialPort.isOpened) {
                        val data = serialPort.readBytes()
                        data?.let {
                            listener?.invoke(it)
                        }
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
                    sendSentryError(e, extraData)
                }
            }
        }
    }

    private suspend fun reconnect() {
        closePort() // 先关闭串口
        while (!openPort()) { // 尝试重新打开串口
            delay(1000) // 等待一秒后重试
        }
    }
}