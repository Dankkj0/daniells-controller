package com.daniell.controller

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class ReceiverActivity : Activity() {
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_receiver)

        startButton = findViewById(R.id.receiverStartButton)
        statusText = findViewById(R.id.receiverStatusText)
        statsText = findViewById(R.id.receiverStatsText)

        startButton.setOnClickListener { toggleReceiver() }
        updateUi()
        startButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun toggleReceiver() {
        if (ReceiverService.isRunning) {
            stopService(Intent(this, ReceiverService::class.java))
            updateUi(false)
            return
        }

        val intent = Intent(this, ReceiverService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            // The service binds the UDP socket during onCreate. Give Android a
            // moment to start it, then refresh the screen without blocking the UI.
            window.decorView.postDelayed({ updateUi() }, 250L)
        } catch (e: Exception) {
            statusText.text = "Não foi possível iniciar"
            statsText.text = e.message ?: "Erro desconhecido"
        }
    }

    private fun updateUi(forceStopped: Boolean? = null) {
        val running = forceStopped?.let { !it } ?: ReceiverService.isRunning
        if (running) {
            startButton.text = "PARAR RECEPÇÃO"
            statusText.text = "TV disponível • aguardando controle"
            statsText.text = "Recepção ativa em segundo plano • porta 4242"
        } else {
            startButton.text = "RECEBER CONTROLE"
            statusText.text = "TV pronta para receber"
            statsText.text = "Pronto • rede Wi-Fi local"
        }
    }
}
