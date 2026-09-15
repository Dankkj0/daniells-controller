package com.daniell.controller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UdpReceiver(private val port: Int, private val onPacket: (Packet) -> Unit) {
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

    fun start() {
        stop()
        running = true
        thread = Thread {
            try {
                val s = DatagramSocket(port)
                socket = s
                val buffer = ByteArray(2048)
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    if (!running) break
                    parse(packet)?.let(onPacket)
                }
                s.close()
            } catch (_: Exception) {
                // Normal shutdown or unavailable port.
            } finally {
                socket?.close()
                socket = null
            }
        }.apply { name = "controller-udp-receiver" }
        thread!!.start()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        socket?.close()
        socket = null
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
        return Packet(sequence, timestampMs, lx, ly, rx, ry, l2, r2, buttons, dpad, packet.address.hostAddress ?: "?")
    }
}
