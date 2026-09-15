package com.daniell.controller

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var client: AdbTvClient
    private lateinit var status: TextView
    private lateinit var ipInput: EditText
    private lateinit var connectButton: Button
    private lateinit var commandButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        ipInput = findViewById(R.id.ip_input)
        connectButton = findViewById(R.id.connect_button)
        client = AdbTvClient(this)

        val prefs = getSharedPreferences("controller", MODE_PRIVATE)
        ipInput.setText(prefs.getString("tv_ip", ""))

        commandButtons = listOf(
            findViewById(R.id.back_button),
            findViewById(R.id.home_button),
            findViewById(R.id.up_button),
            findViewById(R.id.down_button),
            findViewById(R.id.left_button),
            findViewById(R.id.right_button),
            findViewById(R.id.ok_button),
            findViewById(R.id.volume_down_button),
            findViewById(R.id.volume_up_button),
            findViewById(R.id.play_pause_button)
        )

        client.setListener(object : AdbTvClient.Listener {
            override fun onConnectionChanged(connected: Boolean, message: String) {
                runOnUiThread {
                    status.text = message
                    connectButton.text = if (connected) "DESCONECTAR" else "CONECTAR"
                    commandButtons.forEach { it.isEnabled = connected }
                }
            }
        })

        connectButton.setOnClickListener {
            if (client.isConnected()) {
                client.disconnect()
                return@setOnClickListener
            }

            val host = ipInput.text.toString().trim()
            if (host.isBlank()) {
                status.text = "Digite o IP da TV"
                return@setOnClickListener
            }

            prefs.edit().putString("tv_ip", host).apply()
            status.text = "Conectando a $host:5555..."
            connectButton.isEnabled = false

            Thread {
                val result = client.connect(host, 5555)
                runOnUiThread {
                    connectButton.isEnabled = true
                    if (result.isFailure) {
                        status.text = result.exceptionOrNull()?.message ?: "Falha na conexão ADB"
                    }
                }
            }.start()
        }

        findViewById<Button>(R.id.back_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_BACK) }
        findViewById<Button>(R.id.home_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_HOME) }
        findViewById<Button>(R.id.up_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
        findViewById<Button>(R.id.down_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        findViewById<Button>(R.id.left_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        findViewById<Button>(R.id.right_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        findViewById<Button>(R.id.ok_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_CENTER) }
        findViewById<Button>(R.id.volume_down_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_VOLUME_DOWN) }
        findViewById<Button>(R.id.volume_up_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_VOLUME_UP) }
        findViewById<Button>(R.id.play_pause_button).setOnClickListener { sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isGamepadEvent(event)) {
            val tvKey = mapGamepadKey(event.keyCode)
            if (tvKey != null && client.isConnected()) {
                sendKey(tvKey)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isGamepadEvent(event: KeyEvent): Boolean {
        return KeyEvent.isGamepadKey(event.keyCode)
    }

    private fun mapGamepadKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
        KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_X
        KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_START -> KeyEvent.KEYCODE_HOME
        KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_BACK
        else -> null
    }

    private fun sendKey(keyCode: Int) {
        Thread {
            val result = client.sendKeyEvent(keyCode)
            runOnUiThread {
                if (result.isFailure) {
                    status.text = result.exceptionOrNull()?.message ?: "Falha ao enviar comando"
                } else if (client.isConnected()) {
                    status.text = "Comando enviado"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        client.disconnect()
        super.onDestroy()
    }
}
