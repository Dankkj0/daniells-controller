package com.daniell.controller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.daniell.controller.databinding.ActivityMainBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var activeDeviceId = -1

    companion object {
        private const val REQUEST_BLUETOOTH = 100

        private val BUTTONS = listOf(
            KeyEvent.KEYCODE_BUTTON_A to "X",
            KeyEvent.KEYCODE_BUTTON_B to "CÍRCULO",
            KeyEvent.KEYCODE_BUTTON_X to "QUADRADO",
            KeyEvent.KEYCODE_BUTTON_Y to "TRIÂNGULO",
            KeyEvent.KEYCODE_BUTTON_L1 to "L1",
            KeyEvent.KEYCODE_BUTTON_R1 to "R1",
            KeyEvent.KEYCODE_BUTTON_L2 to "L2",
            KeyEvent.KEYCODE_BUTTON_R2 to "R2",
            KeyEvent.KEYCODE_BUTTON_THUMBL to "L3",
            KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
            KeyEvent.KEYCODE_BUTTON_START to "OPTIONS",
            KeyEvent.KEYCODE_BUTTON_SELECT to "SHARE"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestBluetoothPermissionIfNeeded()
        refreshController()

        binding.scanButton.setOnClickListener {
            refreshController()
        }
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missing.toTypedArray(),
                    REQUEST_BLUETOOTH
                )
            }
        }
    }

    private fun refreshController() {
        val devices = InputDevice.getDeviceIds()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { device ->
                val s = device.sources
                (s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                 s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) &&
                    !device.isVirtual
            }

        if (devices.isEmpty()) {
            activeDeviceId = -1
            binding.statusText.text = "🔴 Nenhum controle detectado"
            binding.deviceText.text = "Conecte o DualSense por Bluetooth."
            return
        }

        val device = devices.first()
        activeDeviceId = device.id

        binding.statusText.text = "🟢 CONTROLE DETECTADO"
        binding.deviceText.text =
            "${device.name}\nID ${device.id}  •  Vendor ${device.vendorId}  •  Product ${device.productId}"

        resetVisualState()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.deviceId == activeDeviceId) {
            handleKey(event)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleKey(event: KeyEvent) {
        val pressed = event.action == KeyEvent.ACTION_DOWN

        updateButton(event.keyCode, pressed)

        binding.lastEvent.text =
            "Último evento: ${keyName(event.keyCode)}  •  ${if (pressed) "PRESSIONADO" else "SOLTO"}"
    }

    private fun updateButton(keyCode: Int, pressed: Boolean) {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> setButton(binding.btnX, pressed)
            KeyEvent.KEYCODE_BUTTON_B -> setButton(binding.btnCircle, pressed)
            KeyEvent.KEYCODE_BUTTON_X -> setButton(binding.btnSquare, pressed)
            KeyEvent.KEYCODE_BUTTON_Y -> setButton(binding.btnTriangle, pressed)
            KeyEvent.KEYCODE_BUTTON_L1 -> setButton(binding.btnL1, pressed)
            KeyEvent.KEYCODE_BUTTON_R1 -> setButton(binding.btnR1, pressed)
            KeyEvent.KEYCODE_BUTTON_L2 -> setButton(binding.btnL2, pressed)
            KeyEvent.KEYCODE_BUTTON_R2 -> setButton(binding.btnR2, pressed)
            KeyEvent.KEYCODE_BUTTON_THUMBL -> setButton(binding.btnL3, pressed)
            KeyEvent.KEYCODE_BUTTON_THUMBR -> setButton(binding.btnR3, pressed)
            KeyEvent.KEYCODE_BUTTON_START -> setButton(binding.btnOptions, pressed)
            KeyEvent.KEYCODE_BUTTON_SELECT -> setButton(binding.btnShare, pressed)

            KeyEvent.KEYCODE_DPAD_UP -> setButton(binding.dpadUp, pressed)
            KeyEvent.KEYCODE_DPAD_DOWN -> setButton(binding.dpadDown, pressed)
            KeyEvent.KEYCODE_DPAD_LEFT -> setButton(binding.dpadLeft, pressed)
            KeyEvent.KEYCODE_DPAD_RIGHT -> setButton(binding.dpadRight, pressed)
            KeyEvent.KEYCODE_DPAD_CENTER -> setButton(binding.dpadCenter, pressed)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.deviceId == activeDeviceId &&
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            updateAxes(event)
        }

        return super.dispatchGenericMotionEvent(event)
    }

    private fun updateAxes(event: MotionEvent) {
        val device = event.device ?: return

        val lx = axis(event, device, MotionEvent.AXIS_X)
        val ly = axis(event, device, MotionEvent.AXIS_Y)
        val rx = axis(event, device, MotionEvent.AXIS_Z)
            .takeUnless { it == 0f && device.getMotionRange(MotionEvent.AXIS_RZ, event.source) != null }
            ?: axis(event, device, MotionEvent.AXIS_RZ)
        val ry = axis(event, device, MotionEvent.AXIS_RZ)

        val l2 = trigger(event, device, MotionEvent.AXIS_LTRIGGER)
        val r2 = trigger(event, device, MotionEvent.AXIS_RTRIGGER)

        binding.lxValue.text = fmt(lx)
        binding.lyValue.text = fmt(ly)
        binding.rxValue.text = fmt(rx)
        binding.ryValue.text = fmt(ry)
        binding.l2Value.text = "${(l2 * 100).toInt().coerceIn(0, 100)}%"
        binding.r2Value.text = "${(r2 * 100).toInt().coerceIn(0, 100)}%"

        binding.lStick.position = lx to ly
        binding.rStick.position = rx to ry

        binding.l2Bar.progress = (l2 * 100).toInt().coerceIn(0, 100)
        binding.r2Bar.progress = (r2 * 100).toInt().coerceIn(0, 100)
    }

    private fun axis(
        event: MotionEvent,
        device: InputDevice,
        axis: Int
    ): Float {
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat) value else 0f
    }

    private fun trigger(
        event: MotionEvent,
        device: InputDevice,
        axis: Int
    ): Float {
        if (device.getMotionRange(axis, event.source) == null) return 0f
        val raw = event.getAxisValue(axis)
        return ((raw + 1f) / 2f).coerceIn(0f, 1f)
    }

    private fun setButton(view: android.widget.TextView, pressed: Boolean) {
        view.isSelected = pressed
        view.alpha = if (pressed) 1f else 0.55f
    }

    private fun resetVisualState() {
        val buttons = listOf(
            binding.btnX, binding.btnCircle, binding.btnSquare, binding.btnTriangle,
            binding.btnL1, binding.btnR1, binding.btnL2, binding.btnR2,
            binding.btnL3, binding.btnR3, binding.btnOptions, binding.btnShare,
            binding.dpadUp, binding.dpadDown, binding.dpadLeft,
            binding.dpadRight, binding.dpadCenter
        )
        buttons.forEach { setButton(it, false) }

        binding.lxValue.text = "0.000"
        binding.lyValue.text = "0.000"
        binding.rxValue.text = "0.000"
        binding.ryValue.text = "0.000"
        binding.l2Value.text = "0%"
        binding.r2Value.text = "0%"
        binding.l2Bar.progress = 0
        binding.r2Bar.progress = 0
        binding.lStick.position = 0f to 0f
        binding.rStick.position = 0f to 0f
    }

    private fun keyName(code: Int): String =
        BUTTONS.firstOrNull { it.first == code }?.second ?: when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> "D-pad ↑"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-pad ↓"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-pad ←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-pad →"
            KeyEvent.KEYCODE_DPAD_CENTER -> "D-pad •"
            else -> "KEYCODE $code"
        }

    private fun fmt(value: Float): String = "%.3f".format(value)
}
