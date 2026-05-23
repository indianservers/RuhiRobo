package com.indianservers.ruhi

interface MotorController {
    fun tiltHead(degrees: Float)
    fun nudge(x: Float, y: Float)
}

interface LedController {
    fun showMood(mood: PetMood)
}

interface PetHardware {
    val motors: MotorController
    val leds: LedController
    fun apply(state: PetState)
}

class AndroidPreviewHardware : PetHardware {
    override val motors: MotorController = object : MotorController {
        override fun tiltHead(degrees: Float) = Unit
        override fun nudge(x: Float, y: Float) = Unit
    }

    override val leds: LedController = object : LedController {
        override fun showMood(mood: PetMood) = Unit
    }

    override fun apply(state: PetState) {
        leds.showMood(state.mood)
    }
}
