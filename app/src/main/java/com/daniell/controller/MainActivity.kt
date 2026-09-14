package com.daniell.controller

import android.app.Activity
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var leftStick: StickView
    private lateinit var rightStick: StickView
    private lateinit var leftStickText: TextView
    private lateinit var rightStickText: TextView
    private lateinit var triggersText: TextView
    private lateinit var buttonsText: TextView
    private lateinit var deviceText: TextView
    private lateinit var rawAxesText: TextView
    private lateinit var eventText: TextView
    private lateinit var logText: TextView
    private lateinit var udpStatusText: TextView
    private lateinit var ipEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var udpButton: Button

    private val pressed = linkedSetOf<String>()
    private val hatPressed = linkedSetOf<String>()
    private val logLines = ArrayDeque<String>()
    private val udpSender = UdpSender()
    private var lx = 0f
    private var ly = 0f
    private var rx = 0f
    private var ry = 0f
    private var l2 = 0f
    private var r2 = 0f

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        leftStick = findViewById(R.id.leftStick)
        rightStick = findViewById(R.id.rightStick)
        leftStickText = findViewById(R.id.leftStickText)
        rightStickText = findViewById(R.id.rightStickText)
        triggersText = findViewById(R.id.triggersText)
        buttonsText = findViewById(R.id.buttonsText)
        deviceText = findViewById(R.id.deviceText)
        rawAxesText = findViewById(R.id.rawAxesText)
        eventText = findViewById(R.id.eventText)
        logText = findViewById(R.id.logText)
        udpStatusText = findViewById(R.id.udpStatusText)
        ipEdit = findViewById(R.id.ipEdit)
        portEdit = findViewById(R.id.portEdit)
        udpButton = findViewById(R.id.udpButton)

        udpButton.setOnClickListener { toggleUdp() }
        refreshDevices()
    }

    override fun onResume() {
        super.onResume()
        refreshDevices()
    }

    override fun onDestroy() {
        udpSender.stop()
        super.onDestroy()
    }

    private fun isController(d: InputDevice): Boolean {
        return (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun refreshDevices() {
        var found: InputDevice? = null
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id)
            if (device != null && isController(device)) {
                found = device
                break
            }
        }

        if (found == null) {
            statusText.text = "Nenhum controle físico detectado"
            deviceText.text = "Conecte o DualSense e pressione algum botão."
        } else {
            statusText.text = "Controle detectado: ${found.name}"
            updateDevice(found)
        }
    }

    private fun toggleUdp() {
        if (udpSender.isRunning()) {
            udpSender.stop()
            udpButton.text = "INICIAR TRANSMISSÃO"
            udpStatusText.text = "UDP: parado"
            return
        }

        val host = ipEdit.text.toString().trim()
        val port = portEdit.text.toString().trim().toIntOrNull()
        if (host.isEmpty() || port == null || port !in 1..65535) {
            udpStatusText.text = "UDP: IP ou porta inválidos"
            return
        }

        try {
            udpSender.start(host, port) { sequence ->
                UdpSender.buildPacket(
                    sequence = sequence,
                    timeMs = System.currentTimeMillis(),
                    lx = lx, ly = ly, rx = rx, ry = ry, l2 = l2, r2 = r2,
                    buttons = buttonMask(),
                    dpad = dpadMask()
                )
            }
            udpButton.text = "PARAR TRANSMISSÃO"
            udpStatusText.text = "UDP: enviando para $host:$port (~120 Hz)"
        } catch (e: Exception) {
            udpStatusText.text = "UDP: erro ao iniciar (${e.message ?: "desconhecido"})"
            udpSender.stop()
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val d = e.device
        if (d != null && isController(d)) {
            val name = keyName(e.keyCode)
            when (e.action) {
                KeyEvent.ACTION_DOWN -> pressed.add(name)
                KeyEvent.ACTION_UP -> pressed.remove(name)
            }

            val isRepeat = e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0
            if (!isRepeat) {
                eventText.text = "KEY ${actionName(e.action)} code=${e.keyCode} " +
                        "${KeyEvent.keyCodeToString(e.keyCode)} src=0x${e.source.toString(16)} dev=${e.deviceId}"
                addLog("KEY ${actionName(e.action)} code=${e.keyCode} name=$name src=0x${e.source.toString(16)}")
            }

            updateDevice(d)
            updateButtonsText()
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        val d = e.device
        if (d != null && isController(d) &&
            (e.source and InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK) {
            lx = centered(e, d, MotionEvent.AXIS_X)
            ly = centered(e, d, MotionEvent.AXIS_Y)
            rx = firstAxis(e, d, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX)
            ry = firstAxis(e, d, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)
            l2 = trigger(e, d, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE)
            r2 = trigger(e, d, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS)

            leftStick.setPosition(lx, ly)
            rightStick.setPosition(rx, ry)
            leftStickText.text = "LX ${f(lx)}\nLY ${f(ly)}"
            rightStickText.text = "RX ${f(rx)}\nRY ${f(ry)}"
            triggersText.text = "L2 ${f(l2)}    R2 ${f(r2)}"
            rawAxes(e, d)
            updateHatDpad(e, d)
            updateDevice(d)
            eventText.text = "MOTION action=${e.action} src=0x${e.source.toString(16)} dev=${d.id}"
            return true
        }
        return super.dispatchGenericMotionEvent(e)
    }

    private fun updateHatDpad(e: MotionEvent, d: InputDevice) {
        if (d.getMotionRange(MotionEvent.AXIS_HAT_X, e.source) == null &&
            d.getMotionRange(MotionEvent.AXIS_HAT_Y, e.source) == null) return

        val newHat = linkedSetOf<String>()
        val hatX = e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = e.getAxisValue(MotionEvent.AXIS_HAT_Y)

        if (hatX < -0.5f) newHat.add("D-pad ←")
        if (hatX > 0.5f) newHat.add("D-pad →")
        if (hatY < -0.5f) newHat.add("D-pad ↑")
        if (hatY > 0.5f) newHat.add("D-pad ↓")

        if (newHat != hatPressed) {
            val old = hatPressed.toSet()
            hatPressed.clear()
            hatPressed.addAll(newHat)
            updateButtonsText()

            val pressedNow = newHat.joinToString(" ").ifEmpty { "nenhum" }
            val released = old - newHat
            if (newHat.isNotEmpty()) addLog("HAT D-pad: $pressedNow")
            else if (released.isNotEmpty()) addLog("HAT D-pad: soltou")
        }
    }

    private fun buttonMask(): Int {
        var mask = 0
        val names = listOf(
            "A / X", "B / Círculo", "X / Quadrado", "Y / Triângulo",
            "L1", "R1", "L2", "R2", "L3", "R3",
            "Options / Start", "Share / Select", "PS / Mode"
        )
        for ((i, name) in names.withIndex()) if (pressed.contains(name)) mask = mask or (1 shl i)
        return mask
    }

    private fun dpadMask(): Int {
        var mask = 0
        if (hatPressed.contains("D-pad ↑") || pressed.contains("D-pad ↑")) mask = mask or 1
        if (hatPressed.contains("D-pad ↓") || pressed.contains("D-pad ↓")) mask = mask or 2
        if (hatPressed.contains("D-pad ←") || pressed.contains("D-pad ←")) mask = mask or 4
        if (hatPressed.contains("D-pad →") || pressed.contains("D-pad →")) mask = mask or 8
        return mask
    }

    private fun updateButtonsText() {
        val all = (pressed + hatPressed).distinct()
        buttonsText.text = "BOTÕES PRESSIONADOS\n" +
                if (all.isEmpty()) "Nenhum" else all.joinToString("  •  ")
    }

    private fun centered(e: MotionEvent, d: InputDevice, axis: Int): Float {
        val r = d.getMotionRange(axis, e.source) ?: return 0f
        return ((e.getAxisValue(axis) - r.min) / (r.max - r.min) * 2f - 1f).coerceIn(-1f, 1f)
    }

    private fun firstAxis(e: MotionEvent, d: InputDevice, a: Int, b: Int): Float {
        return if (d.getMotionRange(a, e.source) != null) centered(e, d, a)
        else if (d.getMotionRange(b, e.source) != null) centered(e, d, b) else 0f
    }

    private fun trigger(e: MotionEvent, d: InputDevice, a: Int, b: Int): Float {
        val axis = if (d.getMotionRange(a, e.source) != null) a
        else if (d.getMotionRange(b, e.source) != null) b else -1
        if (axis < 0) return 0f
        val r = d.getMotionRange(axis, e.source)!!
        return ((e.getAxisValue(axis) - r.min) / (r.max - r.min)).coerceIn(0f, 1f)
    }

    private fun rawAxes(e: MotionEvent, d: InputDevice) {
        val axes = listOf(
            MotionEvent.AXIS_X to "X", MotionEvent.AXIS_Y to "Y",
            MotionEvent.AXIS_Z to "Z", MotionEvent.AXIS_RX to "RX",
            MotionEvent.AXIS_RY to "RY", MotionEvent.AXIS_RZ to "RZ",
            MotionEvent.AXIS_LTRIGGER to "LTRIGGER", MotionEvent.AXIS_RTRIGGER to "RTRIGGER",
            MotionEvent.AXIS_BRAKE to "BRAKE", MotionEvent.AXIS_GAS to "GAS",
            MotionEvent.AXIS_HAT_X to "HAT_X", MotionEvent.AXIS_HAT_Y to "HAT_Y"
        )
        rawAxesText.text = buildString {
            append("EIXOS DISPONÍVEIS / RAW\n")
            for ((id, n) in axes) {
                val r = d.getMotionRange(id, e.source)
                if (r != null) append(String.format(Locale.US,
                    "%-9s id=%-2d raw=% .4f min=% .2f max=% .2f flat=% .3f\n",
                    n, id, e.getAxisValue(id), r.min, r.max, r.flat))
            }
        }
    }

    private fun updateDevice(d: InputDevice) {
        deviceText.text = "DISPOSITIVO\nNome: ${d.name}\nID: ${d.id}\n" +
                "Vendor ID: ${d.vendorId}\nProduct ID: ${d.productId}\n" +
                "Sources: 0x${String.format("%08X", d.sources)}\nDescriptor: ${d.descriptor ?: "n/d"}"
    }

    private fun addLog(s: String) {
        if (logLines.size >= 12) logLines.removeFirst()
        logLines.addLast(s)
        logText.text = "LOG DE EVENTOS\n" + logLines.joinToString("\n")
    }

    private fun keyName(c: Int) = when (c) {
        KeyEvent.KEYCODE_BUTTON_A -> "A / X"
        KeyEvent.KEYCODE_BUTTON_B -> "B / Círculo"
        KeyEvent.KEYCODE_BUTTON_X -> "X / Quadrado"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y / Triângulo"
        KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
        KeyEvent.KEYCODE_BUTTON_START -> "Options / Start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Share / Select"
        KeyEvent.KEYCODE_DPAD_UP -> "D-pad ↑"
        KeyEvent.KEYCODE_DPAD_DOWN -> "D-pad ↓"
        KeyEvent.KEYCODE_DPAD_LEFT -> "D-pad ←"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "D-pad →"
        KeyEvent.KEYCODE_DPAD_CENTER -> "D-pad Center"
        KeyEvent.KEYCODE_BUTTON_MODE -> "PS / Mode"
        else -> KeyEvent.keyCodeToString(c)
    }

    private fun actionName(a: Int) = when (a) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
        else -> a.toString()
    }

    private fun f(v: Float) = String.format(Locale.US, "% .3f", v)
}
