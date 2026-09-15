package com.daniell.controller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Keeps the TV receiver alive after the receiver screen is closed. */
class ReceiverService : Service() {
    private var receiver: UdpReceiver? = null
    private var tvDiscovery: TvDiscovery? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()

        val discovery = TvDiscovery(this)
        tvDiscovery = discovery

        val newReceiver = UdpReceiver(
            port = TvDiscovery.DEFAULT_PORT,
            onPacket = { packet ->
                lastPacket = packet
                lastPacketAtMs = System.currentTimeMillis()
                packetCount++
            },
            onError = {
                isRunning = false
                lastError = it
                stopSelf()
            }
        )

        try {
            newReceiver.start()
            receiver = newReceiver
            isRunning = true
            lastError = null
            discovery.registerReceiver(
                port = TvDiscovery.DEFAULT_PORT,
                onReady = { },
                onError = { lastError = it }
            )
        } catch (e: Exception) {
            newReceiver.stop()
            receiver = null
            discovery.unregisterReceiver()
            isRunning = false
            lastError = e.message ?: "erro ao abrir a porta 4242"
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        tvDiscovery?.unregisterReceiver()
        tvDiscovery = null
        receiver?.stop()
        receiver = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("daniell's controller")
            .setContentText("Recebendo controle na TV")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Controle da TV",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantém o receptor de controle ativo na TV" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var lastPacket: UdpReceiver.Packet? = null
            private set
        @Volatile var lastPacketAtMs: Long = 0L
            private set
        @Volatile var packetCount: Long = 0L
            private set
        @Volatile var lastError: String? = null
            private set

        private const val CHANNEL_ID = "controller_receiver"
        private const val NOTIFICATION_ID = 4242
    }
}
