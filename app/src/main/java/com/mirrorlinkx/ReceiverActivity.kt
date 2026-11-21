package com.mirrorlinkx

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import android.util.Base64
import java.util.concurrent.Executors

class ReceiverActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private lateinit var qrPanel: LinearLayout

    private var server: ServerSocket? = null
    private var videoSocket: Socket? = null
    private var controlSocket: Socket? = null
    private var controlOutput: DataOutputStream? = null
    private var audioTrack: AudioTrack? = null
    private val pool = Executors.newSingleThreadExecutor()
    private val controlLock = Any()
    private var touchStart: Pair<Float, Float>? = null
    private lateinit var sessionToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionToken = createSessionToken()
        var listener: ServerSocket? = null
        try {
            listener = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(VIDEO_PORT))
            }
            server = listener
        } catch (_: Exception) {
        }
        buildUi()
        if (listener != null) {
            pool.execute { listenForSender(listener) }
        } else {
            status.text = "Could not open port 4747. Close another receiver session and retry."
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 18, 14, 14)
            setBackgroundColor(Color.BLACK)
        }

        status = TextView(this).apply {
            text = "Preparing receiver…"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        root.addView(status)

        qrPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 18)
        }

        val ip = localIp()
        val qrPayload = "mirrorlinkx://connect?host=$ip&port=4747&token=$sessionToken"
        val qrImage = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            contentDescription = "MirrorLink X receiver pairing QR code"
        }
        try {
            val matrix = QRCodeWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 420, 420)
            val bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
            for (x in 0 until 420) {
                for (y in 0 until 420) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            qrImage.setImageBitmap(bitmap)
        } catch (_: Exception) {
        }

        qrPanel.addView(
            qrImage,
            LinearLayout.LayoutParams(260, 260).apply { gravity = Gravity.CENTER }
        )
        val instructions = TextView(this).apply {
            text = "Receiver IP: $ip\nScan this QR from the sender phone, then approve screen capture there. Keep both phones on the same trusted Wi-Fi."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 8)
        }
        qrPanel.addView(instructions)
        root.addView(qrPanel, LinearLayout.LayoutParams(-1, 0, 1f))

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
            contentDescription = "Mirrored phone screen. Touch and drag to control the sender when accessibility control is enabled."
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        preview.setOnTouchListener { _, event -> handleRemoteTouch(event) }

        root.addView(Button(this).apply {
            text = "Stop receiver"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        status.text = if (ip == "Unavailable") {
            "Receiver needs a Wi-Fi connection to show a pairing QR"
        } else {
            "Waiting for sender on TCP 4747"
        }
    }

    private fun createSessionToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun localIp(): String {
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val address = Formatter.formatIpAddress(wifi.connectionInfo.ipAddress)
            address.takeIf { it != "0.0.0.0" } ?: "Unavailable"
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    private fun listenForSender(listening: ServerSocket) {
        try {
            while (!listening.isClosed) {
                val client = listening.accept()
                videoSocket = client
                handleVideoSession(client)
            }
        } catch (e: Exception) {
            if (!isFinishing) {
                runOnUiThread { status.text = "Receiver stopped: ${e.message ?: "network error"}" }
            }
        }
    }

    private fun handleVideoSession(client: Socket) {
        try {
            val input = DataInputStream(BufferedInputStream(client.getInputStream()))
            val suppliedToken = input.readUTF()
            if (suppliedToken != sessionToken) {
                runOnUiThread { status.text = "Rejected an unpaired connection" }
                client.close()
                return
            }

            connectControlChannel(client.inetAddress.hostAddress ?: "")
            runOnUiThread {
                qrPanel.visibility = View.GONE
                preview.visibility = View.VISIBLE
                status.text = if (controlOutput != null) {
                    "Connected • drag on the image to send touch gestures"
                } else {
                    "Video connected • touch-control channel unavailable"
                }
            }

            while (!client.isClosed) {
                val type = try {
                    input.readUnsignedByte()
                } catch (_: Exception) {
                    break
                }
                val size = input.readInt()
                val maxSize = if (type == PACKET_VIDEO) MAX_VIDEO_PACKET else MAX_AUDIO_PACKET
                if (size <= 0 || size > maxSize) break
                val packet = ByteArray(size)
                input.readFully(packet)

                when (type) {
                    PACKET_VIDEO -> showVideoFrame(packet)
                    PACKET_AUDIO -> playAudioPacket(packet)
                    else -> break
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
            try {
                controlSocket?.close()
            } catch (_: Exception) {
            }
            controlSocket = null
            controlOutput = null
            if (!isFinishing) {
                runOnUiThread {
                    preview.visibility = View.GONE
                    qrPanel.visibility = View.VISIBLE
                    status.text = "Disconnected • waiting for sender"
                }
            }
        }
    }

    private fun connectControlChannel(senderIp: String) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(senderIp, CONTROL_PORT), 2500)
            socket.tcpNoDelay = true
            val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            stream.writeUTF(sessionToken)
            stream.flush()
            controlSocket = socket
            controlOutput = stream
        } catch (_: Exception) {
            controlSocket = null
            controlOutput = null
        }
    }

    private fun showVideoFrame(bytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        runOnUiThread {
            val previous = (preview.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            preview.setImageBitmap(bitmap)
            if (previous != null && previous !== bitmap && !previous.isRecycled) {
                previous.recycle()
            }
        }
    }

    private fun playAudioPacket(bytes: ByteArray) {
        try {
            var track = audioTrack
            if (track == null) {
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
                val minimum = AudioTrack.getMinBufferSize(
                    AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(8192)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minimum * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                audioTrack = track
            }
            track.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
        } catch (_: Exception) {
        }
    }

    private fun handleRemoteTouch(event: MotionEvent): Boolean {
        val output = controlOutput ?: return false
        val width = preview.width.coerceAtLeast(1).toFloat()
        val height = preview.height.coerceAtLeast(1).toFloat()
        val x = (event.x / width).coerceIn(0f, 1f)
        val y = (event.y / height).coerceIn(0f, 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStart = x to y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val start = touchStart ?: (x to y)
                val duration = (event.eventTime - event.downTime).toInt().coerceIn(80, 700)
                try {
                    synchronized(controlLock) {
                        output.writeByte(CONTROL_SWIPE)
                        output.writeFloat(start.first)
                        output.writeFloat(start.second)
                        output.writeFloat(x)
                        output.writeFloat(y)
                        output.writeInt(duration)
                        output.flush()
                    }
                } catch (_: Exception) {
                    controlOutput = null
                }
                touchStart = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                touchStart = null
                return true
            }
        }
        return true
    }

    override fun onDestroy() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        try {
            videoSocket?.close()
        } catch (_: Exception) {
        }
        try {
            controlSocket?.close()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        pool.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val VIDEO_PORT = 4747
        private const val CONTROL_PORT = 4749
        private const val PACKET_VIDEO = 1
        private const val PACKET_AUDIO = 2
        private const val CONTROL_SWIPE = 1
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val MAX_VIDEO_PACKET = 6_000_000
        private const val MAX_AUDIO_PACKET = 64_000
    }
}
