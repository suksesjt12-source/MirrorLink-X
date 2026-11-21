package com.mirrorlinkx

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.util.UUID
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var videoSocket: Socket? = null
    private var output: DataOutputStream? = null
    private var controlServer: ServerSocket? = null
    private var bluetoothServer: BluetoothServerSocket? = null
    private var bluetoothClient: BluetoothSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null

    private val frameExecutor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val outputLock = Any()
    @Volatile private var streaming = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val host = intent?.getStringExtra("host")
        val token = intent?.getStringExtra("token")
        val audioEnabled = intent?.getBooleanExtra("audioEnabled", false) ?: false
        val bluetoothControlEnabled = intent?.getBooleanExtra("bluetoothControlEnabled", false) ?: false

        if (data == null || host.isNullOrBlank() || token.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Thread {
            startStreaming(host, token, resultCode, data, audioEnabled, bluetoothControlEnabled)
        }.start()
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startStreaming(host: String, token: String, resultCode: Int, permissionData: Intent, audioEnabled: Boolean, bluetoothControlEnabled: Boolean) {
        try {
            val controlListener = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(4749))
            }
            controlServer = controlListener
            startControlListener(controlListener, token)
            if (bluetoothControlEnabled) startBluetoothControlListener(token)

            val socket = Socket()
            socket.connect(InetSocketAddress(host, VIDEO_PORT), 7000)
            socket.tcpNoDelay = true
            videoSocket = socket
            val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            output = stream
            stream.writeUTF(token)
            stream.flush()

            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = manager.getMediaProjection(resultCode, permissionData)
            projection = mediaProjection

            // Android 14+ requires a MediaProjection callback before creating the virtual display.
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            val metrics = DisplayMetrics()
            val displayManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            displayManager.defaultDisplay.getRealMetrics(metrics)

            val scale = minOf(1f, MAX_CAPTURE_WIDTH.toFloat() / metrics.widthPixels.toFloat())
            val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(2)
            val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(2)

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader = imageReader
            imageReader.setOnImageAvailableListener({ source ->
                val image = try {
                    source.acquireLatestImage()
                } catch (_: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                if (!busy.compareAndSet(false, true)) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                frameExecutor.execute {
                    try {
                        val plane = image.planes[0]
                        val pixelStride = plane.pixelStride.coerceAtLeast(1)
                        val rowPixels = (plane.rowStride / pixelStride).coerceAtLeast(width)
                        val padded = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)

                        val bytes = ByteArrayOutputStream().use { buffer ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)
                            buffer.toByteArray()
                        }
                        sendPacket(PACKET_VIDEO, bytes)

                        if (cropped !== padded) padded.recycle()
                        cropped.recycle()
                    } catch (_: Exception) {
                        stopSelf()
                    } finally {
                        image.close()
                        busy.set(false)
                    }
                }
            }, Handler(Looper.getMainLooper()))

            display = mediaProjection.createVirtualDisplay(
                "MirrorLinkX",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )
            streaming = true

            if (audioEnabled) startAppAudioCapture(mediaProjection)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun sendPacket(type: Int, bytes: ByteArray) {
        val stream = output ?: return
        synchronized(outputLock) {
            if (!streaming && type != PACKET_VIDEO) return
            stream.writeByte(type)
            stream.writeInt(bytes.size)
            stream.write(bytes)
            stream.flush()
        }
    }

    private fun startAppAudioCapture(mediaProjection: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minimum = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minimum * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            audioRecord = record
            record.startRecording()

            streaming = true
            audioThread = Thread {
                val buffer = ByteArray(8192)
                while (streaming && !Thread.currentThread().isInterrupted) {
                    val count = try {
                        record.read(buffer, 0, buffer.size)
                    } catch (_: Exception) {
                        break
                    }
                    if (count > 0) {
                        sendPacket(PACKET_AUDIO, buffer.copyOf(count))
                    } else if (count < 0) {
                        break
                    }
                }
            }.apply {
                name = "MirrorLinkX-Audio"
                start()
            }
        } catch (_: Exception) {
            // Some playback apps prohibit capture. Video remains available.
            try {
                audioRecord?.release()
            } catch (_: Exception) {
            }
            audioRecord = null
        }
    }

    private fun startControlListener(server: ServerSocket, token: String) {
        Thread {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                Thread {
                    client.use { connection ->
                        try {
                            handleControlInput(DataInputStream(connection.getInputStream()), token)
                        } catch (_: Exception) {
                        }
                    }
                }.apply { isDaemon = true; start() }
            }
        }.apply {
            name = "MirrorLinkX-Control"
            isDaemon = true
            start()
        }
    }

    private fun startBluetoothControlListener(token: String) {
        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@Thread
                if (!adapter.isEnabled) return@Thread
                val listener = adapter.listenUsingRfcommWithServiceRecord("MirrorLink X Touch", BLUETOOTH_UUID)
                bluetoothServer = listener
                while (!listener.isClosed && !streaming) Thread.sleep(100)
                while (!listener.isClosed) {
                    val client = try { listener.accept() } catch (_: Exception) { break }
                    bluetoothClient = client
                    Thread {
                        client.use { connection ->
                            try { handleControlInput(DataInputStream(connection.inputStream), token) } catch (_: Exception) { }
                        }
                    }.apply { name = "MirrorLinkX-BluetoothControl"; isDaemon = true; start() }
                }
            } catch (_: SecurityException) {
                // Bluetooth permission may have been revoked while sharing.
            } catch (_: Exception) {
                // Bluetooth is optional; Wi-Fi video remains available.
            }
        }.apply { name = "MirrorLinkX-BluetoothServer"; isDaemon = true; start() }
    }

    private fun handleControlInput(input: DataInputStream, token: String) {
        if (input.readUTF() != token) return
        while (!Thread.currentThread().isInterrupted) {
            val type = try { input.readUnsignedByte() } catch (_: Exception) { break }
            if (type != CONTROL_SWIPE) break
            val startX = input.readFloat().coerceIn(0f, 1f)
            val startY = input.readFloat().coerceIn(0f, 1f)
            val endX = input.readFloat().coerceIn(0f, 1f)
            val endY = input.readFloat().coerceIn(0f, 1f)
            val duration = input.readInt().coerceIn(80, 700)
            MirrorLinkAccessibilityService.sendSwipe(startX, startY, endX, endY, duration)
        }
    }
    override fun onDestroy() {
        streaming = false
        try {
            output?.flush()
        } catch (_: Exception) {
        }
        try {
            videoSocket?.close()
        } catch (_: Exception) {
        }
        try {
            controlServer?.close()
        } catch (_: Exception) {
        }
        try { bluetoothServer?.close() } catch (_: Exception) { }
        try { bluetoothClient?.close() } catch (_: Exception) { }
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        try {
            display?.release()
        } catch (_: Exception) {
        }
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        frameExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "MirrorLink X screen sharing",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MirrorLink X")
                .setContentText("Screen sharing is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("MirrorLink X")
                .setContentText("Screen sharing is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "mirrorlink_capture"
        private const val VIDEO_PORT = 4747
        private const val MAX_CAPTURE_WIDTH = 900
        private const val JPEG_QUALITY = 52
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val PACKET_VIDEO = 1
        private const val PACKET_AUDIO = 2
        private const val CONTROL_SWIPE = 1
        private val BLUETOOTH_UUID: UUID = UUID.fromString("a3f1c2d4-6b8e-4f10-9a2c-7d5e1f3b4c60")
    }
}
