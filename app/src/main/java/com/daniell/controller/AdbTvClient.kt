package com.daniell.controller

import android.content.Context
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class AdbTvClient(private val context: Context) {
    private var socket: Socket? = null
    private var connection: AdbConnection? = null
    private var listener: Listener? = null

    interface Listener {
        fun onConnectionChanged(connected: Boolean, message: String)
    }

    fun setListener(listener: Listener?) { this.listener = listener }

    @Synchronized
    fun isConnected(): Boolean = connection != null && socket?.isConnected == true && socket?.isClosed == false

    @Synchronized
    fun connect(host: String, port: Int = 5555): Result<String> {
        close()
        return try {
            val keys = loadOrCreateKeys()
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 4000)
            val c = AdbConnection.create(s, keys)
            if (!c.connect(5, TimeUnit.SECONDS, false)) {
                throw IllegalStateException("A TV não aceitou a conexão ADB. Verifique a depuração ADB e a autorização.")
            }
            socket = s
            connection = c
            val message = "Conectado à TV em $host:$port"
            listener?.onConnectionChanged(true, message)
            Result.success(message)
        } catch (e: Exception) {
            close()
            listener?.onConnectionChanged(false, e.message ?: "Falha na conexão ADB")
            Result.failure(e)
        }
    }

    @Synchronized
    fun sendKeyEvent(keyCode: Int): Result<String> {
        val c = connection ?: return Result.failure(IllegalStateException("ADB não conectado"))
        return try {
            c.open("shell:input keyevent $keyCode").use { it.read() }
            Result.success("OK")
        } catch (e: Exception) {
            close()
            listener?.onConnectionChanged(false, "Conexão perdida")
            Result.failure(e)
        }
    }

    @Synchronized
    fun disconnect() {
        close()
        listener?.onConnectionChanged(false, "ADB desconectado")
    }

    private fun close() {
        try { connection?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        connection = null
        socket = null
    }

    private fun loadOrCreateKeys(): AdbCrypto {
        val dir = File(context.filesDir, "adb")
        dir.mkdirs()
        val privateFile = File(dir, "adbkey")
        val publicFile = File(dir, "adbkey.pub")
        val base64 = object : com.tananaev.adblib.AdbBase64 {
            override fun encodeToString(data: ByteArray): String =
                android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        }
        return if (privateFile.exists() && publicFile.exists()) {
            AdbCrypto.loadAdbKeyPair(base64, privateFile, publicFile)
        } else {
            AdbCrypto.generateAdbKeyPair(base64).also {
                it.saveAdbKeyPair(privateFile, publicFile)
            }
        }
    }
}
