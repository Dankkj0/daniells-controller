package com.daniell.controller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Low-latency controller state sender for the local network. */
class UdpSender {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var socket: DatagramSocket? = null

    fun start(host: String, port: Int, stateProvider: (Int) -> ByteArray) {
        stop()
        val address = InetAddress.getByName(host)
        val s = DatagramSocket()
        s.broadcast = false
        socket = s
        running = true

        thread = Thread {
            var sequence = 0
            var previousState: ByteArray? = null
            try {
                while (running) {
                    val payload = stateProvider(sequence)

                    // The sequence identifies controller-state changes, not the
                    // 120 Hz transport heartbeat. This keeps it stable while
                    // the controller is idle or a button is being held.
                    val stateStart = 18 // after magic/version/reserved/seq/timestamp
                    val state = payload.copyOfRange(stateStart, payload.size)
                    if (previousState == null || !state.contentEquals(previousState)) {
                        sequence++
                        val updated = rebuildSequence(payload, sequence)
                        previousState = state
                        s.send(DatagramPacket(updated, updated.size, address, port))
                    } else {
                        // Keep the current state alive without advancing SEQ.
                        s.send(DatagramPacket(payload, payload.size, address, port))
                    }
                    Thread.sleep(8L) // ~120 Hz
                }
            } catch (_: InterruptedException) {
                // Normal shutdown.
            } catch (_: Exception) {
                // Network errors must not crash the app.
            } finally {
                if (socket === s) socket = null
                s.close()
            }
        }.apply { name = "controller-udp-sender" }
        thread!!.start()
    }

    private fun rebuildSequence(payload: ByteArray, sequence: Int): ByteArray {
        val copy = payload.copyOf()
        ByteBuffer.wrap(copy, 6, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sequence)
        return copy
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        socket?.close()
        socket = null
    }

    fun isRunning(): Boolean = running

    companion object {
        const val PACKET_VERSION = 1

        /**
         * Binary packet, little-endian:
         * magic(4) version(1) reserved(1) sequence(4) timeMs(8)
         * lx ly rx ry l2 r2 (6 x float) buttons(4) dpad(1) = 47 bytes.
         */
        fun buildPacket(
            sequence: Int,
            timeMs: Long,
            lx: Float, ly: Float, rx: Float, ry: Float, l2: Float, r2: Float,
            buttons: Int,
            dpad: Int
        ): ByteArray {
            val b = ByteBuffer.allocate(47).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(0x44434F4E) // "DCON"
            b.put(PACKET_VERSION.toByte())
            b.put(0)
            b.putInt(sequence)
            b.putLong(timeMs)
            b.putFloat(lx)
            b.putFloat(ly)
            b.putFloat(rx)
            b.putFloat(ry)
            b.putFloat(l2)
            b.putFloat(r2)
            b.putInt(buttons)
            b.put(dpad.toByte())
            return b.array()
        }
    }
}
