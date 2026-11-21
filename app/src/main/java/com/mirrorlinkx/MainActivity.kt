package com.mirrorlinkx

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.*
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : Activity() {
    private val bg = Color.rgb(10, 13, 22)
    private val panel = Color.rgb(22, 28, 43)
    private val cyan = Color.rgb(70, 220, 255)
    private lateinit var status: TextView
    private lateinit var ipText: TextView
    private lateinit var qrView: ImageView
    private val captureRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 30, 28, 24)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(label("MIRRORLINK", 13, cyan, true))
        root.addView(label("X", 38, Color.WHITE, true))
        root.addView(label("Local screen sharing • Private LAN", 14, Color.LTGRAY, false))
        root.addView(spacer(22))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(panel)
        }
        card.addView(label("DEVICE STATUS", 12, cyan, true))
        status = label("● Ready to pair", 16, Color.WHITE, false)
        card.addView(status)
        ipText = label("Local IP: " + localIp(), 14, Color.LTGRAY, false)
        card.addView(ipText)
        root.addView(card)
        root.addView(spacer(18))

        root.addView(label("START MIRROR", 12, cyan, true))
        root.addView(spacer(8))
        root.addView(button("Share this screen", Color.rgb(0, 160, 190)) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
        })
        root.addView(spacer(10))
        root.addView(button("Connect to another device", Color.rgb(45, 55, 75)) {
            showConnectDialog()
        })
        root.addView(spacer(20))

        root.addView(label("PAIRING QR", 12, cyan, true))
        root.addView(spacer(8))
        qrView = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(18, 18, 18, 18)
        }
        val ip = localIp()
        val qrData = "mirrorlinkx://pair?host=$ip&port=4747"
        try {
            val matrix = QRCodeWriter().encode(qrData, BarcodeFormat.QR_CODE, 420, 420)
            val bmp = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
            for (x in 0 until 420) for (y in 0 until 420) bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            qrView.setImageBitmap(bmp)
        } catch (_: Exception) { }
        root.addView(qrView, LinearLayout.LayoutParams(260, 260).apply { gravity = Gravity.CENTER })
        root.addView(spacer(10))
        root.addView(label("Both devices must be connected to the same Wi-Fi network.", 13, Color.LTGRAY, false))
        root.addView(spacer(20))
        root.addView(label("FEATURE STATUS", 12, cyan, true))
        root.addView(label("✓ QR pairing code\n✓ Screen capture permission flow\n✓ Adaptive portrait/landscape UI\n• Video transport and decoding: in development\n• Remote touch: requires opt-in accessibility setup\n• Audio capture: compatibility work in progress", 14, Color.WHITE, false))
        root.addView(spacer(18))
        root.addView(label("MirrorLink X • MVP 1.0", 12, Color.GRAY, false))
    }

    private fun showConnectDialog() {
        val input = EditText(this).apply { hint = "Receiver IP (e.g. 192.168.1.10)"; setTextColor(Color.WHITE); hintTextColor = Color.GRAY }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 12, 32, 0); addView(input) }
        AlertDialogBuilderCompat.show(this, "Connect device", box) {
            val host = input.text.toString().trim()
            if (host.isNotEmpty()) {
                status.text = "● Target entered: $host"
                Toast.makeText(this, "Pairing transport is not included in this MVP yet.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(service)
            status.text = "● Screen capture active"
        } else if (requestCode == captureRequest) {
            status.text = "● Capture permission declined"
        }
    }

    private fun localIp(): String {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wm.connectionInfo.ipAddress).takeIf { it != "0.0.0.0" } ?: "Unavailable"
        } catch (_: Exception) { "Unavailable" }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private fun spacer(px: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, px) }
    private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE); setBackgroundColor(color); setOnClickListener { action() }
    }
}
