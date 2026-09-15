package com.daniell.controller

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import java.util.Locale

class ReceiverActivity : Activity() {
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var lastCommandText: TextView
    private lateinit var statsText: TextView
    private lateinit var buttonsText: TextView
    private lateinit var sticksText: TextView
    private lateinit var sequenceText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val uiTick = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_receiver)
        startButton = findViewById(R.id.receiverStartButton)
        statusText = findViewById(R.id.receiverStatusText)
        connectionText = findViewById(R.id.receiverConnectionText)
        lastCommandText = findViewById(R.id.receiverLastCommandText)
        statsText = findViewById(R.id.receiverStatsText)
        buttonsText = findViewById(R.id.receiverButtonsText)
        sticksText = findViewById(R.id.receiverSticksText)
        sequenceText = findViewById(R.id.receiverSequenceText)
        startButton.setOnClickListener { toggleReceiver() }
        startButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(uiTick)
        handler.post(uiTick)
    }

    override fun onPause() {
        handler.removeCallbacks(uiTick)
        super.onPause()
    }

    private fun toggleReceiver() {
        if (ReceiverService.isRunning) {
            stopService(Intent(this, ReceiverService::class.java))
            updateUi()
            return
        }
        val intent = Intent(this, ReceiverService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            handler.postDelayed({ updateUi() }, 250L)
        } catch (e: Exception) {
            statusText.text = "ERRO AO INICIAR RECEPTOR"
            statsText.text = e.message ?: "Erro desconhecido"
        }
    }

    private fun updateUi() {
        val running = ReceiverService.isRunning
        val packet = ReceiverService.lastPacket
        val age = if (ReceiverService.lastPacketAtMs == 0L) Long.MAX_VALUE
        else System.currentTimeMillis() - ReceiverService.lastPacketAtMs
        val phoneConnected = running && age < 1200L

        if (running) {
            startButton.text = "PARAR RECEPÇÃO"
            statusText.text = if (phoneConnected) "● RECEPTOR ATIVO  •  CELULAR CONECTADO" else "● RECEPTOR ATIVO  •  AGUARDANDO CELULAR"
            statusText.setTextColor(if (phoneConnected) 0xFF7CFF8A.toInt() else 0xFFFFD166.toInt())
        } else {
            startButton.text = "RECEBER CONTROLE"
            statusText.text = "○ RECEPTOR PARADO"
            statusText.setTextColor(0xFFBDBDBD.toInt())
        }

        connectionText.text = when {
            !running -> "CELULAR  •  receptor desligado"
            phoneConnected -> "CELULAR  •  CONECTADO ✓\nIP: ${packet?.senderAddress ?: "—"}"
            else -> "CELULAR  •  aguardando pacote\nAbra o envio no celular"
        }

        val error = ReceiverService.lastError
        statsText.text = when {
            error != null -> "ERRO: $error"
            !running -> "Pronto • porta ${TvDiscovery.DEFAULT_PORT}"
            packet == null -> "Recepção ativa em segundo plano • porta ${TvDiscovery.DEFAULT_PORT}"
            else -> "Pacotes recebidos: ${ReceiverService.packetCount} • atraso: ${age.coerceAtLeast(0)} ms"
        }

        if (packet == null) {
            lastCommandText.text = "Último comando: —"
            buttonsText.text = "BOTÕES\nNenhum comando recebido ainda"
            sticksText.text = "L  X: 0.00  Y: 0.00\nR  X: 0.00  Y: 0.00\nL2: 0.00     R2: 0.00"
            sequenceText.text = "SEQ: —"
        } else {
            val command = commandText(packet)
            lastCommandText.text = "Último comando: $command"
            buttonsText.text = "BOTÕES\n${buttonText(packet.buttons)}\nD-pad: ${dpadText(packet.dpad)}"
            sticksText.text = String.format(Locale.US,
                "L  X: % .2f  Y: % .2f\nR  X: % .2f  Y: % .2f\nL2: %.2f     R2: %.2f",
                packet.lx, packet.ly, packet.rx, packet.ry, packet.l2, packet.r2)
            sequenceText.text = "SEQ: ${packet.sequence}    •    IP: ${packet.senderAddress}"
        }
    }

    private fun commandText(p: UdpReceiver.Packet): String {
        val buttons = buttonNames(p.buttons)
        val dpad = dpadText(p.dpad)
        return when {
            buttons.isNotEmpty() -> buttons.joinToString(" + ")
            dpad != "—" -> "D-pad $dpad"
            kotlin.math.abs(p.lx) > .15f || kotlin.math.abs(p.ly) > .15f -> "Analógico L"
            kotlin.math.abs(p.rx) > .15f || kotlin.math.abs(p.ry) > .15f -> "Analógico R"
            p.l2 > .1f -> "L2"
            p.r2 > .1f -> "R2"
            else -> "Estado do controle"
        }
    }

    private fun buttonNames(mask: Int): List<String> {
        val names = listOf("X", "Círculo", "Quadrado", "Triângulo", "L1", "R1", "L2", "R2", "L3", "R3", "Options", "Share", "PS")
        return names.mapIndexedNotNull { i, n -> if ((mask and (1 shl i)) != 0) n else null }
    }

    private fun buttonText(mask: Int): String {
        val names = buttonNames(mask)
        return if (names.isEmpty()) "Nenhum" else names.joinToString("  •  ")
    }

    private fun dpadText(mask: Int): String {
        val list = mutableListOf<String>()
        if ((mask and 1) != 0) list.add("↑")
        if ((mask and 2) != 0) list.add("↓")
        if ((mask and 4) != 0) list.add("←")
        if ((mask and 8) != 0) list.add("→")
        return list.ifEmpty { listOf("—") }.joinToString(" ")
    }
}
