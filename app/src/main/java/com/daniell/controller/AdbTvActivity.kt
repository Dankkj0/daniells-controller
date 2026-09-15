package com.daniell.controller

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.Executors

class AdbTvActivity : Activity() {
    private lateinit var ipEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var connectButton: Button
    private lateinit var homeButton: Button
    private lateinit var backButton: Button

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var client: AdbTvClient

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_adb_tv)
        client = AdbTvClient(this)
        ipEdit = findViewById(R.id.adbIpEdit)
        portEdit = findViewById(R.id.adbPortEdit)
        statusText = findViewById(R.id.adbStatusText)
        resultText = findViewById(R.id.adbResultText)
        connectButton = findViewById(R.id.adbConnectButton)
        homeButton = findViewById(R.id.adbHomeButton)
        backButton = findViewById(R.id.adbBackButton)

        portEdit.setText("5555")
        connectButton.setOnClickListener { connect() }
        homeButton.setOnClickListener { sendKey(3, "HOME") }
        backButton.setOnClickListener { sendKey(4, "VOLTAR") }
        setButtons(false)
    }

    private fun connect() {
        val host = ipEdit.text.toString().trim()
        val port = portEdit.text.toString().toIntOrNull() ?: 5555
        if (host.isBlank()) {
            statusText.text = "Informe o IP da TV"
            return
        }
        statusText.text = "CONECTANDO ADB…"
        setButtons(false)
        executor.execute {
            val result = client.connect(host, port)
            runOnUiThread {
                if (result.isSuccess) {
                    statusText.text = "● ADB CONECTADO ✓\n$host:$port"
                    statusText.setTextColor(0xFF7CFF8A.toInt())
                    resultText.text = "Conexão ADB estabelecida.\nAgora teste HOME e VOLTAR."
                    connectButton.text = "DESCONECTAR"
                    setButtons(true)
                } else {
                    statusText.text = "○ ADB DESCONECTADO"
                    statusText.setTextColor(0xFFFF6B6B.toInt())
                    resultText.text = "ERRO: ${result.exceptionOrNull()?.message ?: "falha na conexão"}"
                    connectButton.text = "CONECTAR ADB"
                    setButtons(false)
                }
            }
        }
    }

    private fun sendKey(code: Int, label: String) {
        resultText.text = "Enviando $label…"
        executor.execute {
            val result = client.sendKeyEvent(code)
            runOnUiThread {
                resultText.text = if (result.isSuccess) "✓ $label enviado para a TV" else "✗ $label falhou: ${result.exceptionOrNull()?.message ?: "erro"}"
            }
        }
    }

    private fun setButtons(enabled: Boolean) {
        homeButton.isEnabled = enabled
        backButton.isEnabled = enabled
    }

    override fun onDestroy() {
        client.disconnect()
        executor.shutdownNow()
        super.onDestroy()
    }
}
