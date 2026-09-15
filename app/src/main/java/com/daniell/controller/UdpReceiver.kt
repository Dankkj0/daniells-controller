package com.daniell.controller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Receives the controller packets on the local network. */
class UdpReceiver(
    private val port: Int,
    private val onPacket: (Packet) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    data class Packet(
        val sequence: Int,
        val timestampMs: Long,
        val lx: Float,
        val ly: Float,
        val rx: Float,
        val ry: Float,
        val l2: Float,
        val r2: Float,
        val buttons: Int,
        val dpad: Int,
        val senderAddress: String
    )

    @Volatile private var running = false
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    /** Binds the UDP port before returning, so bind errors are visible to the TV UI. */
    @Synchronized
    fun start() {
        stop()
        val s = DatagramSocket(null)
        s.reuseAddress = true
        s.bind(java.net.InetSocketAddress("0.0.0.0", port))
        socket = s
        running = true

        thread = Thread {
            val buffer = ByteArray(2048)
            try {
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    if (!running) break
                    parse(packet)?.let(onPacket)
                }
            } catch (e: Exception) {
                if (running) onError(e.message ?: "erro de rede")
            } finally {
                running = false
                if (socket === s) socket = null
                s.close()
            }
        }.apply {
            name = "controller-udp-receiver"
            isDaemon = true
        }
        thread!!.start()
    }

    @Synchronized
    fun stop() {
        running = false
        socket?.close()
        socket = null
        thread?.interrupt()
        thread = null
    }

    fun isRunning() = running

    private fun parse(packet: DatagramPacket): Packet? {
        if (packet.length < 47) return null
        val b = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
            .order(ByteOrder.LITTLE_ENDIAN)
        if (b.int != 0x44434F4E) return null
        if (b.get().toInt() != UdpSender.PACKET_VERSION) return null
        b.get() // reserved
        val sequence = b.int
        val timestampMs = b.long
        val lx = b.float
        val ly = b.float
        val rx = b.float
        val ry = b.float
        val l2 = b.float
        val r2 = b.float
        val buttons = b.int
        val dpad = b.get().toInt() and 0xFF
        return Packet(
            sequence, timestampMs, lx, ly, rx, ry, l2, r2,
            buttons, dpad, packet.address.hostAddress ?: "?"
        )
    }
}
