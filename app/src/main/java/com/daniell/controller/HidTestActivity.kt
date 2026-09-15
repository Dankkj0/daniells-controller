package com.daniell.controller

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class HidTestActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var devicesLayout: LinearLayout
    private lateinit var registerButton: Button
    private var hid: BluetoothHidDevice? = null
    private var adapter: BluetoothAdapter? = null
    private var registered = false
    private val executor = Executors.newSingleThreadExecutor()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = proxy as BluetoothHidDevice
                statusText.text = "HID disponível • pronto para registrar"
                registerButton.isEnabled = true
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = null
                registered = false
                statusText.text = "Serviço HID Bluetooth desconectado"
                connectionText.text = "TV: desconectada"
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            runOnUiThread {
                this@HidTestActivity.registered = registered
                statusText.text = if (registered) {
                    "REGISTRO HID ACEITO ✓\nNome: daniell's controller\nTipo: GAMEPAD"
                } else {
                    "Registro HID não ativo"
                }
                registerButton.text = if (registered) "DESREGISTRAR HID" else "REGISTRAR COMO GAMEPAD"
                refreshBondedDevices()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            runOnUiThread {
                val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
                connectionText.text = when (state) {
                    BluetoothProfile.STATE_CONNECTED -> "TV: CONECTADA ✓\nHost: $name"
                    BluetoothProfile.STATE_CONNECTING -> "TV: CONECTANDO...\nHost: $name"
                    BluetoothProfile.STATE_DISCONNECTING -> "TV: DESCONECTANDO..."
                    else -> "TV: desconectada\nÚltimo host: $name"
                }
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_hid_test)
        statusText = findViewById(R.id.hidStatusText)
        connectionText = findViewById(R.id.hidConnectionText)
        devicesLayout = findViewById(R.id.hidDevicesLayout)
        registerButton = findViewById(R.id.hidRegisterButton)

        registerButton.isEnabled = false
        registerButton.setOnClickListener { toggleRegistration() }
        findViewById<Button>(R.id.hidRefreshButton).setOnClickListener { refreshBondedDevices() }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            statusText.text = "HID Device requer Android 9 (API 28) ou superior"
            return
        }

        if (!hasBluetoothConnectPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH)
            }
            return
        }

        setupBluetooth()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_BLUETOOTH && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            setupBluetooth()
        } else if (requestCode == REQUEST_BLUETOOTH) {
            statusText.text = "Permissão Bluetooth necessária para testar HID"
        }
    }

    override fun onDestroy() {
        try {
            if (registered) hid?.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        } catch (_: Exception) { }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun setupBluetooth() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        adapter = manager.adapter
        if (adapter == null) {
            statusText.text = "Este celular não possui Bluetooth compatível"
            return
        }
        if (adapter?.isEnabled != true) {
            statusText.text = "Ative o Bluetooth do celular primeiro"
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        statusText.text = "Conectando ao serviço HID Bluetooth..."
        adapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        refreshBondedDevices()
    }

    private fun toggleRegistration() {
        if (registered) {
            hid?.unregisterApp()
            return
        }
        val profile = hid
        if (profile == null) {
            statusText.text = "Serviço HID ainda não está disponível"
            return
        }

        val descriptor = byteArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xA1.toByte(), 0x01,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10,
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,
            0x05, 0x01, 0x25, 0x07, 0x46, 0x3B, 0x01, 0x75, 0x04, 0x95.toByte(), 0x01,
            0x65, 0x14, 0x09, 0x39, 0x81.toByte(), 0x42, 0x65, 0x00, 0x75, 0x04, 0x81.toByte(), 0x01,
            0x15, 0x00, 0x26, 0xFF.toByte(), 0x00, 0x75, 0x08, 0x95.toByte(), 0x04,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35, 0x81.toByte(), 0x02,
            0x15, 0x00, 0x25, 0xFF.toByte(), 0x75, 0x08, 0x95.toByte(), 0x02,
            0x09, 0x33, 0x09, 0x34, 0x81.toByte(), 0x02,
            0xC0.toByte()
        )

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "daniell's controller",
            "Virtual Bluetooth Gamepad",
            "daniell's controller",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            descriptor
        )

        statusText.text = "Enviando pedido de registro HID..."
        val accepted = try {
            profile.registerApp(sdp, null, null, executor, hidCallback)
        } catch (e: SecurityException) {
            statusText.text = "Permissão Bluetooth recusada"
            false
        } catch (e: Exception) {
            statusText.text = "Erro ao registrar HID: ${e.message ?: "desconhecido"}"
            false
        }

        if (!accepted) {
            statusText.text = "Registro HID não aceito pelo Bluetooth do celular"
        } else {
            statusText.text = "Pedido de registro enviado • aguardando confirmação..."
        }
    }

    private fun refreshBondedDevices() {
        devicesLayout.removeAllViews()
        val a = adapter ?: return
        val devices = try { a.bondedDevices.toList() } catch (_: SecurityException) { emptyList() }
        if (devices.isEmpty()) {
            val t = TextView(this).apply {
                text = "Nenhum dispositivo pareado.\nPareie a TV pelo Bluetooth antes de tentar CONECTAR."
                setTextColor(0xFFBDBDBD.toInt())
                textSize = 14f
            }
            devicesLayout.addView(t)
            return
        }
        for (device in devices) {
            val b = Button(this).apply {
                val name = try { device.name ?: "Dispositivo" } catch (_: SecurityException) { "Dispositivo" }
                text = "CONECTAR  •  $name"
                setOnClickListener { connectTo(device) }
            }
            devicesLayout.addView(b)
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        val profile = hid
        if (!registered || profile == null) {
            connectionText.text = "Registre o GAMEPAD primeiro"
            return
        }
        connectionText.text = "TV: solicitando conexão..."
        try {
            profile.connect(device)
        } catch (e: SecurityException) {
            connectionText.text = "Sem permissão Bluetooth para conectar"
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_BLUETOOTH = 7001
    }
}
