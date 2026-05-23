package com.indianservers.ruhi.model

data class FaceData(
    val smilingProbability: Float = -1f,
    val leftEyeOpenProbability: Float = -1f,
    val rightEyeOpenProbability: Float = -1f,
    val headTiltZ: Float = 0f,
    val eyeOffsetX: Float = 0f,
    val eyeOffsetY: Float = 0f,
    val faceAreaRatio: Float = 0f
)
