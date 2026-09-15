package com.daniell.controller

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiverActivity : Activity() {
    private lateinit var portEdit: EditText
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var packetText: TextView
    private lateinit var logText: TextView

    private var receiver: UdpReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var packets = 0L
    private var lastSequence: Int? = null
    private var lost = 0L
    private var lastUiMs = 0L
    private var packetsSinceUi = 0L
    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_receiver)
        portEdit = findViewById(R.id.receiverPortEdit)
        startButton = findViewById(R.id.receiverStartButton)
        statusText = findViewById(R.id.receiverStatusText)
        packetText = findViewById(R.id.receiverPacketText)
        logText = findViewById(R.id.receiverLogText)
        startButton.setOnClickListener { toggleReceiver() }
    }

    override fun onDestroy() {
        receiver?.stop()
        receiver = null
        super.onDestroy()
    }

    private fun toggleReceiver() {
        if (receiver?.isRunning() == true) {
            receiver?.stop()
            receiver = null
            startButton.text = "INICIAR RECEPTOR"
            statusText.text = "UDP: parado"
            return
        }

        val port = portEdit.text.toString().trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            statusText.text = "UDP: porta inválida"
            return
        }

        packets = 0
        lost = 0
        lastSequence = null
        packetsSinceUi = 0
        logLines.clear()
        logText.text = "LOG DE PACOTES"

        receiver = UdpReceiver(port) { p ->
            packets++
            packetsSinceUi++
            val previous = lastSequence
            if (previous != null) {
                val delta = p.sequence - previous
                if (delta > 1) lost += delta - 1
            }
            lastSequence = p.sequence
            mainHandler.post { showPacket(p) }
        }

        try {
            receiver!!.start()
            startButton.text = "PARAR RECEPTOR"
            statusText.text = "UDP: escutando na porta $port..."
        } catch (e: Exception) {
            receiver?.stop()
            receiver = null
            statusText.text = "UDP: erro ao iniciar (${e.message ?: "desconhecido"})"
        }
    }

    private fun showPacket(p: UdpReceiver.Packet) {
        val now = System.currentTimeMillis()
        val hz = if (lastUiMs == 0L) 0.0 else packetsSinceUi * 1000.0 / (now - lastUiMs).coerceAtLeast(1L)
        if (now - lastUiMs >= 250L) {
            lastUiMs = now
            packetsSinceUi = 0
            statusText.text = "RECEBENDO • ${hz.toInt()} pkt/s • total $packets • perdidos $lost • origem ${p.senderAddress}"
        }

        packetText.text = buildString {
            append("ÚLTIMO PACOTE\n")
            append("Seq: ${p.sequence}\n")
            append("Timestamp: ${p.timestampMs} (${timeFormat.format(Date(p.timestampMs))})\n")
            append("Recebido: ${timeFormat.format(Date(now))}\n")
            append("LX: ${f(p.lx)}    LY: ${f(p.ly)}\n")
            append("RX: ${f(p.rx)}    RY: ${f(p.ry)}\n")
            append("L2: ${f(p.l2)}    R2: ${f(p.r2)}\n")
            append("Botões: ${buttonNames(p.buttons)}\n")
            append("D-pad: ${dpadNames(p.dpad)}\n")
            append("Origem: ${p.senderAddress}")
        }

        if (logLines.size >= 10) logLines.removeFirst()
        logLines.addLast(String.format(Locale.US, "%s  seq=%d  LX=% .2f LY=% .2f RX=% .2f RY=% .2f",
            timeFormat.format(Date(now)), p.sequence, p.lx, p.ly, p.rx, p.ry))
        logText.text = "LOG DE PACOTES\n" + logLines.joinToString("\n")
    }

    private fun buttonNames(mask: Int): String {
        val names = listOf("X", "Círculo", "Quadrado", "Triângulo", "L1", "R1", "L2", "R2", "L3", "R3", "Options", "Share", "PS")
        val active = names.mapIndexedNotNull { i, n -> if ((mask and (1 shl i)) != 0) n else null }
        return if (active.isEmpty()) "Nenhum" else active.joinToString("  •  ")
    }

    private fun dpadNames(mask: Int): String {
        val active = mutableListOf<String>()
        if ((mask and 1) != 0) active.add("↑")
        if ((mask and 2) != 0) active.add("↓")
        if ((mask and 4) != 0) active.add("←")
        if ((mask and 8) != 0) active.add("→")
        return if (active.isEmpty()) "Nenhum" else active.joinToString(" ")
    }

    private fun f(v: Float) = String.format(Locale.US, "% .3f", v)
}
