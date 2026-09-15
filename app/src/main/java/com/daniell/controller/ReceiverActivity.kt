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
    private lateinit var statsText: TextView
    private lateinit var logText: TextView

    private var receiver: UdpReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var packets = 0L
    private var lastSequence: Int? = null
    private var lost = 0L
    @Volatile private var lastPacket: UdpReceiver.Packet? = null
    @Volatile private var lastPacketUiMs = 0L
    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_receiver)
        portEdit = findViewById(R.id.receiverPortEdit)
        startButton = findViewById(R.id.receiverStartButton)
        statusText = findViewById(R.id.receiverStatusText)
        packetText = findViewById(R.id.receiverPacketText)
        statsText = findViewById(R.id.receiverStatsText)
        logText = findViewById(R.id.receiverLogText)
        startButton.setOnClickListener { toggleReceiver() }
        startButton.requestFocus()
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
            startButton.text = "RECEBER CONTROLE"
            statusText.text = "PRONTO • aguardando início"
            statsText.text = "Porta UDP: ${portEdit.text} • receptor parado"
            return
        }

        val port = portEdit.text.toString().trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            statusText.text = "ERRO • porta inválida"
            return
        }

        packets = 0L
        lost = 0L
        lastSequence = null
        lastPacket = null
        lastPacketUiMs = 0L
        logLines.clear()
        packetText.text = "ÚLTIMO PACOTE\nAguardando dados do celular..."
        logText.text = "LOG DE PACOTES\nNenhum pacote recebido."
        statsText.text = "Porta UDP: $port • abrindo..."

        val newReceiver = UdpReceiver(
            port = port,
            onPacket = { p -> onPacketReceived(p) },
            onError = { message ->
                mainHandler.post {
                    statusText.text = "ERRO NO RECEPTOR • $message"
                    startButton.text = "RECEBER CONTROLE"
                }
            }
        )

        try {
            // start() binds synchronously, so a port/bind failure is caught here.
            newReceiver.start()
            receiver = newReceiver
            startButton.text = "PARAR RECEPÇÃO"
            statusText.text = "RECEPTOR ATIVO • aguardando celular..."
            statsText.text = "Porta UDP: $port • 0 pacotes"
        } catch (e: Exception) {
            newReceiver.stop()
            statusText.text = "NÃO FOI POSSÍVEL ABRIR A PORTA\n${e.message ?: "erro desconhecido"}"
            statsText.text = "Escolha outra porta e tente novamente."
        }
    }

    private fun onPacketReceived(p: UdpReceiver.Packet) {
        packets++
        val previous = lastSequence
        if (previous != null) {
            val delta = p.sequence - previous
            if (delta > 1) lost += delta - 1
        }
        lastSequence = p.sequence
        lastPacket = p

        val now = System.currentTimeMillis()
        if (now - lastPacketUiMs >= 100L) {
            lastPacketUiMs = now
            mainHandler.post {
                val latest = lastPacket ?: return@post
                showPacket(latest)
            }
        }
    }

    private fun showPacket(p: UdpReceiver.Packet) {
        val now = System.currentTimeMillis()
        statusText.text = "RECEBENDO CONTROLE • ${p.senderAddress}"
        statsText.text = "Porta UDP: ${portEdit.text}   •   ${packets} pacotes   •   perdidos: $lost   •   origem: ${p.senderAddress}"

        packetText.text = buildString {
            append("SEQ ${p.sequence}     TIMESTAMP ${p.timestampMs}\n")
            append("Recebido: ${timeFormat.format(Date(now))}\n\n")
            append("ANALÓGICOS\n")
            append("LX ${f(p.lx)}     LY ${f(p.ly)}\n")
            append("RX ${f(p.rx)}     RY ${f(p.ry)}\n\n")
            append("GATILHOS\n")
            append("L2 ${f(p.l2)}     R2 ${f(p.r2)}\n\n")
            append("BOTÕES\n${buttonNames(p.buttons)}\n\n")
            append("D-PAD\n${dpadNames(p.dpad)}")
        }

        if (logLines.size >= 8) logLines.removeFirst()
        logLines.addLast(String.format(Locale.US,
            "%s  seq=%d  LX=% .2f LY=% .2f RX=% .2f RY=% .2f",
            timeFormat.format(Date(now)), p.sequence, p.lx, p.ly, p.rx, p.ry))
        logText.text = "LOG RECENTE\n" + logLines.joinToString("\n")
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
