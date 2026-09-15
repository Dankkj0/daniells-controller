package com.daniell.controller

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var client: AdbTvClient
    private lateinit var status: TextView
    private lateinit var ipInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        ipInput = findViewById(R.id.ip_input)
        client = AdbTvClient(this)

        client.setListener(object : AdbTvClient.Listener {
            override fun onConnectionChanged(connected: Boolean, message: String) {
                runOnUiThread {
                    status.text = message
                    findViewById<Button>(R.id.home_button).isEnabled = connected
                    findViewById<Button>(R.id.back_button).isEnabled = connected
                }
            }
        })

        findViewById<Button>(R.id.connect_button).setOnClickListener {
            val host = ipInput.text.toString().trim()
            if (host.isBlank()) {
                status.text = "Digite o IP da TV"
                return@setOnClickListener
            }
            status.text = "Conectando..."
            Thread {
                client.connect(host, 5555)
            }.start()
        }

        findViewById<Button>(R.id.home_button).setOnClickListener {
            sendKey(3)
        }

        findViewById<Button>(R.id.back_button).setOnClickListener {
            sendKey(4)
        }
    }

    private fun sendKey(keyCode: Int) {
        Thread {
            val result = client.sendKeyEvent(keyCode)
            runOnUiThread {
                if (result.isFailure) status.text = result.exceptionOrNull()?.message ?: "Falha ao enviar"
            }
        }.start()
    }

    override fun onDestroy() {
        client.disconnect()
        super.onDestroy()
    }
}
