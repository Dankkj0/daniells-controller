package com.daniell.controller

import android.content.Context
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import java.io.File
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Small ADB-over-TCP client used to send global key events to the TV. */
class AdbTvClient(private val context: Context) {
    private var socket: Socket? = null
    private var connection: AdbConnection? = null
    private var crypto: AdbCrypto? = null

    @Synchronized
    fun connect(host: String, port: Int = 5555): Result<String> {
        disconnect()
        return try {
            val keyDir = File(context.filesDir, "adb")
            keyDir.mkdirs()
            val privateFile = File(keyDir, "adbkey")
            val publicFile = File(keyDir, "adbkey.pub")
            val keys = if (privateFile.exists() && publicFile.exists()) {
                AdbCrypto.loadAdbKeyPair(privateFile, publicFile, object : com.tananaev.adblib.AdbBase64 {
                    override fun encodeToString(data: ByteArray): String = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                })
            } else {
                val generated = AdbCrypto.generateAdbKeyPair(object : com.tananaev.adblib.AdbBase64 {
                    override fun encodeToString(data: ByteArray): String = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                })
                generated.saveAdbKeyPair(privateFile, publicFile)
                generated
            }
            crypto = keys
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(java.net.InetSocketAddress(host, port), 4000)
            val c = AdbConnection.create(s, keys)
            val ok = c.connect(5, TimeUnit.SECONDS, false)
            if (!ok) throw IllegalStateException("A TV não aceitou a conexão ADB ou está aguardando autorização.")
            socket = s
            connection = c
            Result.success("Conectado à TV em $host:$port")
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    @Synchronized
    fun sendKeyEvent(keyCode: Int): Result<String> {
        val c = connection ?: return Result.failure(IllegalStateException("ADB não conectado"))
        return try {
            val stream = c.open("shell:input keyevent $keyCode")
            stream.read()
            stream.close()
            Result.success("Comando enviado: keyevent $keyCode")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Synchronized
    fun disconnect() {
        try { connection?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        connection = null
        socket = null
    }

    fun isConnected(): Boolean = connection != null
}
