package com.indianservers.ruhi

import android.graphics.Rect
import com.google.mlkit.vision.face.Face

class FaceProfileManager(private val repository: ConversationRepository) {
    suspend fun identify(face: Face): FaceProfile? {
        val descriptor = descriptorFor(face)
        val profiles = repository.faceProfiles()
        val match = profiles.minByOrNull {
            FaceDescriptor.decode(it.faceEmbedding).distanceTo(descriptor)
        } ?: return null
        val distance = FaceDescriptor.decode(match.faceEmbedding).distanceTo(descriptor)
        return if (distance < 0.35f) {
            repository.updateFaceSeen(match)
            match
        } else {
            null
        }
    }

    suspend fun saveNewFace(nickname: String, face: Face): Long {
        return repository.saveFaceProfile(
            FaceProfile(
                nickname = nickname,
                faceEmbedding = descriptorFor(face).encode(),
                relationshipScore = 0.25f,
                totalInteractions = 1,
                lastSeen = System.currentTimeMillis()
            )
        )
    }

    fun descriptorFor(face: Face): FaceDescriptor {
        val box: Rect = face.boundingBox
        val width = box.width().toFloat().coerceAtLeast(1f)
        val height = box.height().toFloat().coerceAtLeast(1f)
        val base = floatArrayOf(
            width / height,
            face.headEulerAngleX / 45f,
            face.headEulerAngleY / 45f,
            face.headEulerAngleZ / 45f,
            face.smilingProbability ?: 0f,
            face.leftEyeOpenProbability ?: 0f,
            face.rightEyeOpenProbability ?: 0f
        )
        val values = FloatArray(128) { index -> base[index % base.size] }
        return FaceDescriptor(values)
    }
}
