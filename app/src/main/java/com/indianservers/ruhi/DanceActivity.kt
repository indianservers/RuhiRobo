package com.indianservers.ruhi

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.indianservers.ruhi.hardware.BleRobotManager
import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DanceActivity : AppCompatActivity() {
    private lateinit var faceView: RobotFaceView
    private lateinit var hud: GameHUD
    private lateinit var engine: DanceEngine
    private var currentStyle = DanceStyle.ROBOT_DANCE
    private var dancing = false
    private var beatJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hardware = RobotHardwareController(BleRobotManager(this))
        faceView = RobotFaceView(this)
        engine = DanceEngine(faceView, hardware)
        hud = GameHUD(this)
        setContentView(
            android.widget.FrameLayout(this).apply {
                addView(faceView, android.widget.FrameLayout.LayoutParams(-1, -1))
                addView(hud, android.widget.FrameLayout.LayoutParams(-1, -1))
                addView(TextView(context).apply {
                    text = "Ruhi Dance Stage"
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                }, android.widget.FrameLayout.LayoutParams(-1, 64, Gravity.TOP))
                addView(styleStrip(), android.widget.FrameLayout.LayoutParams(-1, 96, Gravity.BOTTOM))
                addView(Button(context).apply {
                    text = "Start"
                    setOnClickListener { toggleDance(this) }
                }, android.widget.FrameLayout.LayoutParams(160, 96, Gravity.START or Gravity.CENTER_VERTICAL))
                addView(Button(context).apply {
                    text = "Dance Off!"
                    setOnClickListener { startDanceOff() }
                }, android.widget.FrameLayout.LayoutParams(190, 96, Gravity.END or Gravity.CENTER_VERTICAL))
            }
        )
        startBeatVisualizer()
    }

    private fun styleStrip(): HorizontalScrollView {
        return HorizontalScrollView(this).apply {
            setBackgroundColor(0x66000000)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                DanceStyle.entries.forEach { style ->
                    addView(Button(context).apply {
                        text = style.name.replace("_", " ")
                        setOnClickListener {
                            currentStyle = style
                            if (dancing) engine.start(lifecycleScope, currentStyle, beatSync = true)
                        }
                    })
                }
            })
        }
    }

    private fun toggleDance(button: Button) {
        dancing = !dancing
        button.text = if (dancing) "Stop" else "Start"
        if (dancing) engine.start(lifecycleScope, currentStyle, beatSync = true) else engine.stop()
    }

    private fun startDanceOff() {
        hud.scoreText = "Copy Ruhi!"
        lifecycleScope.launch {
            var score = 0
            repeat(5) { round ->
                currentStyle = DanceStyle.entries.random()
                hud.roundText = "Dance Off ${round + 1}/5"
                engine.start(this, currentStyle, beatSync = false)
                delay(2200)
                val match = (55..100).random()
                score += if (match > 72) 1 else 0
                hud.scoreText = "$match% match"
                faceView.setExpression(if (match > 72) RobotFaceView.Expression.GRIN else RobotFaceView.Expression.SAD)
                delay(900)
            }
            hud.scoreText = "Score $score/5"
            engine.stop()
        }
    }

    private fun startBeatVisualizer() {
        beatJob = lifecycleScope.launch {
            var bpm = 96
            while (isActive) {
                bpm += (-2..2).random()
                bpm = bpm.coerceIn(70, 150)
                hud.bpm = bpm
                hud.waveform = (0..100).random() / 100f
                if (!dancing && hud.waveform > 0.78f) {
                    currentStyle = engine.styleForBpm(bpm)
                    dancing = true
                    engine.start(this, currentStyle, beatSync = true)
                }
                delay(450)
            }
        }
    }

    override fun onDestroy() {
        beatJob?.cancel()
        engine.stop()
        super.onDestroy()
    }
}
