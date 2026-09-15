package com.daniell.controller

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

class ReceiverActivity : Activity() {
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView

    private var receiver: UdpReceiver? = null
    private lateinit var tvDiscovery: TvDiscovery
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_receiver)
        tvDiscovery = TvDiscovery(this)
        startButton = findViewById(R.id.receiverStartButton)
        statusText = findViewById(R.id.receiverStatusText)
        statsText = findViewById(R.id.receiverStatsText)
        startButton.setOnClickListener { toggleReceiver() }
        startButton.requestFocus()
    }

    override fun onDestroy() {
        tvDiscovery.unregisterReceiver()
        receiver?.stop()
        receiver = null
        super.onDestroy()
    }

    private fun toggleReceiver() {
        if (receiver?.isRunning() == true) {
            tvDiscovery.unregisterReceiver()
            receiver?.stop()
            receiver = null
            startButton.text = "RECEBER CONTROLE"
            statusText.text = "TV pronta para receber"
            statsText.text = "Pronto • rede Wi-Fi local"
            return
        }

        val port = TvDiscovery.DEFAULT_PORT
        statusText.text = "Iniciando receptor..."
        statsText.text = "Preparando conexão local..."

        val newReceiver = UdpReceiver(
            port = port,
            onPacket = { _ ->
                // Input is handled by the receiver; the TV interface intentionally
                // stays clean and does not expose packet diagnostics.
            },
            onError = { message ->
                mainHandler.post {
                    tvDiscovery.unregisterReceiver()
                    statusText.text = "Não foi possível receber o controle"
                    statsText.text = message
                    startButton.text = "RECEBER CONTROLE"
                }
            }
        )

        try {
            newReceiver.start()
            receiver = newReceiver
            startButton.text = "PARAR RECEPÇÃO"
            statusText.text = "Aguardando celular..."
            statsText.text = "TV disponível na rede Wi-Fi"

            tvDiscovery.registerReceiver(
                port = port,
                onReady = {
                    mainHandler.post {
                        statusText.text = "TV disponível • aguardando controle"
                        statsText.text = "Conecte o controle no celular e selecione esta TV"
                    }
                },
                onError = { message ->
                    mainHandler.post {
                        statusText.text = "Receptor ativo • descoberta indisponível"
                        statsText.text = message
                    }
                }
            )
        } catch (e: Exception) {
            newReceiver.stop()
            tvDiscovery.unregisterReceiver()
            statusText.text = "Não foi possível iniciar"
            statsText.text = e.message ?: "Erro desconhecido"
        }
    }
}
