package com.daniell.controller

import android.content.Context
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import com.tananaev.adblib.AdbStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class AdbTvClient(private val context: Context) {
    private var socket: Socket? = null
    private var connection: AdbConnection? = null
    private var shellStream: AdbStream? = null
    private var shellReader: Thread? = null
    private var listener: Listener? = null

    interface Listener {
        fun onConnectionChanged(connected: Boolean, message: String)
    }

    fun setListener(listener: Listener?) { this.listener = listener }

    @Synchronized
    fun isConnected(): Boolean =
        connection != null && socket?.isConnected == true && socket?.isClosed == false &&
            shellStream?.isClosed == false

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

            // Keep ONE shell process alive for the whole controller session.
            // The loop reads one command at a time from stdin and executes it.
            // This avoids opening/closing an ADB stream for every button press.
            val stream = c.open("shell:sh -c 'while read cmd; do eval \"$cmd\"; done'")
            shellStream = stream
            shellReader = Thread {
                try {
                    while (!stream.isClosed) stream.read()
                } catch (_: Exception) {
                    // Stream ended.
                }
            }.apply {
                name = "adb-shell-reader"
                isDaemon = true
                start()
            }

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
        val stream = shellStream
            ?: return Result.failure(IllegalStateException("ADB não conectado"))

        return try {
            if (stream.isClosed) throw IllegalStateException("Stream ADB fechado")

            // No waiting for a new ADB stream or for the input process to exit.
            // The persistent shell receives the command immediately.
            stream.write("input keyevent $keyCode\n")
            Result.success("OK")
        } catch (e: Exception) {
            close()
            listener?.onConnectionChanged(false, "Conexão perdida: ${e.message ?: "Stream ADB desconectado"}")
            Result.failure(e)
        }
    }

    @Synchronized
    fun disconnect() {
        close()
        listener?.onConnectionChanged(false, "ADB desconectado")
    }

    private fun close() {
        try { shellStream?.close() } catch (_: Exception) {}
        shellStream = null
        shellReader = null
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
