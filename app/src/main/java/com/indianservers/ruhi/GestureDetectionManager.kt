package com.indianservers.ruhi

import androidx.camera.core.ImageProxy

class GestureDetectionManager(
    private val delegate: GestureRecognitionManager = GestureRecognitionManager()
) {
    fun detect(imageProxy: ImageProxy): GestureEvent? = delegate.detect(imageProxy)

    fun reactionFor(event: GestureEvent): Pair<RobotFaceView.Expression, String>? {
        return when (event) {
            is GestureEvent.Detected -> delegate.reactionFor(event.type)
        }
    }
}
