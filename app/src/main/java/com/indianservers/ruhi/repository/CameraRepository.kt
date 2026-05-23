package com.indianservers.ruhi.repository

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.indianservers.ruhi.model.FaceData

class CameraRepository(
    private val onFaceData: (FaceData) -> Unit,
    private val onRawFace: (Face) -> Unit = {}
) : ImageAnalysis.Analyzer {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                faces.firstOrNull()?.let { face ->
                    val rotated = imageProxy.imageInfo.rotationDegrees % 180 != 0
                    val width = if (rotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
                    val height = if (rotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()
                    val offsetX = ((face.boundingBox.centerX() / width) - 0.5f) * 2f
                    val offsetY = ((face.boundingBox.centerY() / height) - 0.5f) * 2f
                    val area = (face.boundingBox.width() * face.boundingBox.height()).toFloat() /
                        (width * height).coerceAtLeast(1f)
                    onRawFace(face)
                    onFaceData(
                        FaceData(
                            smilingProbability = face.smilingProbability ?: -1f,
                            leftEyeOpenProbability = face.leftEyeOpenProbability ?: -1f,
                            rightEyeOpenProbability = face.rightEyeOpenProbability ?: -1f,
                            headTiltZ = face.headEulerAngleZ,
                            eyeOffsetX = -offsetX,
                            eyeOffsetY = offsetY,
                            faceAreaRatio = area
                        )
                    )
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
