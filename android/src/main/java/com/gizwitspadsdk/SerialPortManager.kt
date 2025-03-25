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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

object SerialPortManager {
    private var serialPort = SerialPort("/dev/ttyS6")
    private var isRunning = false
    private var listener: ((ByteArray) -> Unit)? = null
    private var trying = false
    
    // 添加写入队列和写入线程控制，设置队列容量为10000
    private val writeQueue = LinkedBlockingQueue<ByteArray>(10000)
    private val isWriteThreadRunning = AtomicBoolean(false)
    private var writeThread: Thread? = null

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
            startWriteThreadIfNeeded()
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
            // 停止写入线程
            isWriteThreadRunning.set(false)
            writeThread?.interrupt()
            writeThread = null
            
            // 清空写入队列
            writeQueue.clear()
            
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

    // fun sendData(data: ByteArray) {
    //     try {
    //         serialPort.writeBytes(data)
    //     } catch (e: SerialPortException) {
    //         val extraData = mapOf(
    //             "errorMessage" to e.toString(),
    //             "event" to "send data error"
    //         )
    //         sendSentryError(e, extraData)
    //     }
    // }

    fun sendData(data: ByteArray) {
        try {
            // 如果队列满了，先移除最旧的数据
            if (writeQueue.remainingCapacity() == 0) {
                writeQueue.poll() // 移除最旧的数据
                val extraData = mapOf(
                    "errorMessage" to "Write queue is full, dropping oldest data",
                    "event" to "queue full warning",
                    "queueSize" to writeQueue.size.toString()
                )
                sendSentryError(Exception("Write queue is full"), extraData)
                println("Write queue is full")
            }
            // 添加新数据
            writeQueue.offer(data)
        } catch (e: Exception) {
            val extraData = mapOf(
                "errorMessage" to e.toString(),
                "event" to "queue data error"
            )
            sendSentryError(e, extraData)
        }
    }

    private fun startWriteThreadIfNeeded() {
        if (!isWriteThreadRunning.get()) {
            isWriteThreadRunning.set(true)
            writeThread = Thread {
                while (isWriteThreadRunning.get()) {
                    try {
                        val data = writeQueue.poll()
                        if (data != null) {
                            serialPort.writeBytes(data)
                        } else {
                            Thread.sleep(10) // 避免空转
                        }
                    } catch (e: SerialPortException) {
                        val extraData = mapOf(
                            "errorMessage" to e.toString(),
                            "event" to "write thread error"
                        )
                        sendSentryError(e, extraData)
                        // 发生错误时暂停写入
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }.apply {
                name = "SerialPortWriteThread"
                isDaemon = true
                start()
            }
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
                    delay(20) // 控制读取频率
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