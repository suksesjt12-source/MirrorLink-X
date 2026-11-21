package com.mirrorlinkx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class MirrorLinkAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No window content is read or inspected.
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    private fun performNormalizedSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Int
    ) {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(startX * metrics.widthPixels, startY * metrics.heightPixels)
            lineTo(endX * metrics.widthPixels, endY * metrics.heightPixels)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    durationMs.toLong()
                )
            )
            .build()
        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        private var activeService: MirrorLinkAccessibilityService? = null

        fun sendSwipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Int
        ) {
            val service = activeService ?: return
            Handler(Looper.getMainLooper()).post {
                service.performNormalizedSwipe(
                    startX.coerceIn(0f, 1f),
                    startY.coerceIn(0f, 1f),
                    endX.coerceIn(0f, 1f),
                    endY.coerceIn(0f, 1f),
                    durationMs.coerceIn(80, 700)
                )
            }
        }
    }
}
