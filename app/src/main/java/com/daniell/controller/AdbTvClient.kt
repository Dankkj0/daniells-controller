package com.daniell.controller

import android.content.Context
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** ADB-over-TCP client used to send global key events to the TV. */
class AdbTvClient(private val context: Context) {
    private var socket: Socket? = null
    private var connection: AdbConnection? = null
    private var crypto: AdbCrypto? = null
    private val reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "adb-tv-reconnect").apply { isDaemon = true }
    }
    private var reconnectEnabled = false
    private var reconnectScheduled = false
    private var host: String? = null
    private var port: Int = 5555
    private var listener: Listener? = null

    interface Listener {
        fun onConnectionChanged(connected: Boolean, message: String)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    @Synchronized
    fun connect(host: String, port: Int = 5555): Result<String> {
        this.host = host.trim()
        this.port = port
        reconnectEnabled = true
        cancelReconnectLocked()
        return connectInternalLocked(this.host!!, this.port, notify = true)
    }

    @Synchronized
    private fun connectInternalLocked(host: String, port: Int, notify: Boolean): Result<String> {
        closeConnectionLocked()
        return try {
            val keys = loadOrCreateKeys()
            crypto = keys
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 4000)
            val c = AdbConnection.create(s, keys)
            val ok = c.connect(5, TimeUnit.SECONDS, false)
            if (!ok) throw IllegalStateException("A TV não aceitou a conexão ADB ou está aguardando autorização.")
            socket = s
            connection = c
            reconnectScheduled = false
            if (notify) notifyState(true, "Conectado à TV em $host:$port")
            Result.success("Conectado à TV em $host:$port")
        } catch (e: Exception) {
            closeConnectionLocked()
            val message = e.message ?: "Falha na conexão ADB"
            if (notify) notifyState(false, message)
            if (reconnectEnabled) scheduleReconnectLocked()
            Result.failure(e)
        }
    }

    @Synchronized
    fun sendKeyEvent(keyCode: Int): Result<String> {
        val c = connection
        if (c == null) {
            if (reconnectEnabled) scheduleReconnectLocked()
            return Result.failure(IllegalStateException("ADB não conectado; tentando reconectar automaticamente"))
        }
        return try {
            val stream = c.open("shell:input keyevent $keyCode")
            stream.read()
            stream.close()
            Result.success("Comando enviado: keyevent $keyCode")
        } catch (e: Exception) {
            markConnectionLostLocked(e.message ?: "Conexão ADB perdida")
            Result.failure(e)
        }
    }

    @Synchronized
    fun disconnect() {
        reconnectEnabled = false
        cancelReconnectLocked()
        closeConnectionLocked()
        notifyState(false, "ADB desconectado")
    }

    fun isConnected(): Boolean = synchronized(this) { connection != null }

    fun isAutoReconnectEnabled(): Boolean = synchronized(this) { reconnectEnabled }

    @Synchronized
    private fun markConnectionLostLocked(message: String) {
        closeConnectionLocked()
        notifyState(false, "TV desconectada • reconectando automaticamente")
        if (reconnectEnabled) scheduleReconnectLocked()
    }

    private fun scheduleReconnectLocked() {
        if (!reconnectEnabled || reconnectScheduled || host.isNullOrBlank()) return
        reconnectScheduled = true
        reconnectExecutor.schedule({
            synchronized(this) {
                reconnectScheduled = false
                if (!reconnectEnabled || connection != null) return@synchronized
                val targetHost = host ?: return@synchronized
                val result = connectInternalLocked(targetHost, port, notify = true)
                if (result.isFailure && reconnectEnabled) {
                    scheduleReconnectLocked()
                }
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun cancelReconnectLocked() {
        reconnectScheduled = false
    }

    @Synchronized
    private fun closeConnectionLocked() {
        try { connection?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        connection = null
        socket = null
    }

    private fun notifyState(connected: Boolean, message: String) {
        listener?.onConnectionChanged(connected, message)
    }

    private fun loadOrCreateKeys(): AdbCrypto {
        val keyDir = File(context.filesDir, "adb")
        keyDir.mkdirs()
        val privateFile = File(keyDir, "adbkey")
        val publicFile = File(keyDir, "adbkey.pub")
        val base64 = object : com.tananaev.adblib.AdbBase64 {
            override fun encodeToString(data: ByteArray): String =
                android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        }
        return if (privateFile.exists() && publicFile.exists()) {
            AdbCrypto.loadAdbKeyPair(privateFile, publicFile, base64)
        } else {
            AdbCrypto.generateAdbKeyPair(base64).also { it.saveAdbKeyPair(privateFile, publicFile) }
        }
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 2000L
    }
}
