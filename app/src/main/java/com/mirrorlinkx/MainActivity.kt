package com.mirrorlinkx

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.media.projection.MediaProjectionManager
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var hostInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var audioCheck: CheckBox
    private var pendingShareAfterPermission = false

    companion object {
        private const val CAPTURE_REQUEST = 4201
        private const val AUDIO_PERMISSION_REQUEST = 4202
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(10, 13, 22))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("MIRRORLINK", 14, Color.rgb(70, 220, 255), true))
        root.addView(label("X", 42, Color.WHITE, true))
        root.addView(label("Android-to-Android • Local Wi-Fi", 14, Color.LTGRAY, false))
        root.addView(space(20))

        root.addView(label("RECEIVE SCREEN", 12, Color.rgb(70, 220, 255), true))
        root.addView(button("Start receiver") {
            startActivity(Intent(this, ReceiverActivity::class.java))
        })
        root.addView(space(18))

        root.addView(label("PAIR AND SEND SCREEN", 12, Color.rgb(70, 220, 255), true))
        root.addView(label("On the receiver phone, open Start receiver and scan its QR code using this phone.", 13, Color.LTGRAY, false))
        root.addView(button("Scan receiver QR") {
            IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Scan the QR code shown on the receiver phone")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan()
        })

        hostInput = EditText(this).apply {
            hint = "Receiver IP address (manual fallback)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
        }
        root.addView(hostInput)

        tokenInput = EditText(this).apply {
            hint = "Pairing token (from receiver QR)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
        }
        root.addView(tokenInput)

        audioCheck = CheckBox(this).apply {
            text = "Include supported app audio (no microphone)"
            setTextColor(Color.WHITE)
            isChecked = false
        }
        root.addView(audioCheck)

        root.addView(button("Share my screen") { startShareFlow() })
        root.addView(button("Stop screen sharing") {
            stopService(Intent(this, CaptureService::class.java))
            status.text = "● Sharing stopped"
        })

        root.addView(space(12))
        root.addView(label("REMOTE TOUCH", 12, Color.rgb(70, 220, 255), true))
        root.addView(label("Touch control requires an explicit Accessibility permission on the sending phone. Screen sharing works without enabling it.", 13, Color.LTGRAY, false))
        root.addView(button("Open touch-control settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(space(18))
        status = label("● Ready", 15, Color.WHITE, false)
        root.addView(status)
        root.addView(space(14))
        root.addView(label("Privacy: pairing uses a random session token. Video and optional audio travel over the local network without transport encryption. Use only a trusted private Wi-Fi network.", 12, Color.LTGRAY, false))
    }

    private fun startShareFlow() {
        val host = hostInput.text.toString().trim()
        val token = tokenInput.text.toString().trim()
        if (!isValidIpv4(host)) {
            Toast.makeText(this, "Scan the receiver QR or enter a valid receiver IPv4 address", Toast.LENGTH_LONG).show()
            return
        }
        if (token.length < 20) {
            Toast.makeText(this, "Scan the receiver QR to obtain its pairing token", Toast.LENGTH_LONG).show()
            return
        }
        if (audioCheck.isChecked) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(this, "App-audio capture requires Android 10 or newer", Toast.LENGTH_LONG).show()
                return
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                pendingShareAfterPermission = true
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
                return
            }
        }
        requestCapturePermission()
    }

    private fun requestCapturePermission() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), CAPTURE_REQUEST)
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val number = part.toIntOrNull()
            number != null && number in 0..255
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (scan != null) {
            val contents = scan.contents
            if (!contents.isNullOrBlank()) applyPairingQr(contents)
            return
        }

        if (requestCode == CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, CaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                    putExtra("host", hostInput.text.toString().trim())
                    putExtra("token", tokenInput.text.toString().trim())
                    putExtra("audioEnabled", audioCheck.isChecked)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                status.text = "● Connecting to receiver…"
            } else {
                status.text = "● Screen-capture permission was not granted"
            }
        }
    }

    private fun applyPairingQr(contents: String) {
        try {
            val uri = Uri.parse(contents)
            if (uri.scheme != "mirrorlinkx" || uri.host != "connect") {
                throw IllegalArgumentException("Not a MirrorLink X pairing code")
            }
            val host = uri.getQueryParameter("host") ?: throw IllegalArgumentException("Missing host")
            val token = uri.getQueryParameter("token") ?: throw IllegalArgumentException("Missing token")
            if (!isValidIpv4(host) || token.length < 20) {
                throw IllegalArgumentException("Invalid pairing data")
            }
            hostInput.setText(host)
            tokenInput.setText(token)
            status.text = "● Receiver paired: $host"
            Toast.makeText(this, "Receiver paired. Tap Share my screen.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val shouldContinue = pendingShareAfterPermission
            pendingShareAfterPermission = false
            if (granted && shouldContinue) requestCapturePermission()
            else if (shouldContinue) {
                audioCheck.isChecked = false
                Toast.makeText(this, "Audio permission denied. Turn off audio or allow the permission to continue.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }

    private fun space(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(21, 105, 135))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 8
        }
    }
}
