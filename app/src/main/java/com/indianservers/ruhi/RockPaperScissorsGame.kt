package com.indianservers.ruhi

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RpsGesture { ROCK, PAPER, SCISSORS, UNKNOWN }
enum class RpsOutcome { WIN, LOSE, DRAW }

class RockPaperScissorsGame {
    private val history = mutableListOf<RpsGesture>()

    fun classifyHand(fingerExtended: List<Boolean>): RpsGesture {
        val extended = fingerExtended.count { it }
        return when {
            extended <= 1 -> RpsGesture.ROCK
            extended >= 4 -> RpsGesture.PAPER
            fingerExtended.getOrNull(1) == true && fingerExtended.getOrNull(2) == true && extended == 2 -> RpsGesture.SCISSORS
            else -> RpsGesture.UNKNOWN
        }
    }

    fun ruhiPick(hard: Boolean = false): RpsGesture {
        if (hard && history.size >= 3 && history.takeLast(3).distinct().size == 1) {
            return beats(history.last())
        }
        return listOf(RpsGesture.ROCK, RpsGesture.PAPER, RpsGesture.SCISSORS).random()
    }

    fun play(user: RpsGesture, ruhi: RpsGesture): RpsOutcome {
        if (user != RpsGesture.UNKNOWN) history += user
        return when {
            user == ruhi -> RpsOutcome.DRAW
            beats(user) == ruhi -> RpsOutcome.LOSE
            else -> RpsOutcome.WIN
        }
    }

    private fun beats(gesture: RpsGesture): RpsGesture = when (gesture) {
        RpsGesture.ROCK -> RpsGesture.PAPER
        RpsGesture.PAPER -> RpsGesture.SCISSORS
        RpsGesture.SCISSORS -> RpsGesture.ROCK
        RpsGesture.UNKNOWN -> RpsGesture.ROCK
    }
}

class RockPaperScissorsActivity : AppCompatActivity() {
    private val game = RockPaperScissorsGame()
    private lateinit var face: RobotFaceView
    private lateinit var status: TextView
    private var you = 0
    private var ruhi = 0
    private var startAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAt = System.currentTimeMillis()
        face = RobotFaceView(this)
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 10, 18))
            addView(face, LinearLayout.LayoutParams(-1, 0, 0.55f))
            addView(status, LinearLayout.LayoutParams(-1, 0, 0.15f))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                listOf(RpsGesture.ROCK, RpsGesture.PAPER, RpsGesture.SCISSORS).forEach { gesture ->
                    addView(Button(context).apply {
                        text = gesture.name
                        setOnClickListener { countdownAndPlay(gesture) }
                    })
                }
            }, LinearLayout.LayoutParams(-1, 0, 0.3f))
        })
        status.text = "Choose on SHOOT!"
    }

    private fun countdownAndPlay(user: RpsGesture) {
        lifecycleScope.launch {
            listOf("3...", "2...", "1...", "SHOOT!").forEach {
                status.text = it
                delay(420)
            }
            val ruhiPick = game.ruhiPick(hard = true)
            drawRuhiGesture(ruhiPick)
            when (game.play(user, ruhiPick)) {
                RpsOutcome.WIN -> { you++; face.setExpression(RobotFaceView.Expression.SHOCK); status.text = "You win! Your hand detection is cheating!" }
                RpsOutcome.LOSE -> { ruhi++; face.setExpression(RobotFaceView.Expression.GRIN); status.text = "${ruhiPick.name} wins. Science." }
                RpsOutcome.DRAW -> { face.setExpression(RobotFaceView.Expression.SQUINT); status.text = "Again! You: $you Ruhi: $ruhi" }
            }
            save()
        }
    }

    private fun drawRuhiGesture(gesture: RpsGesture) {
        face.armLAngle = when (gesture) {
            RpsGesture.ROCK -> -30f
            RpsGesture.PAPER -> -90f
            RpsGesture.SCISSORS -> -55f
            else -> 0f
        }
        face.armRAngle = -face.armLAngle
        face.setExpression(
            when (gesture) {
                RpsGesture.ROCK -> RobotFaceView.Expression.ANGRY
                RpsGesture.PAPER -> RobotFaceView.Expression.COOL
                RpsGesture.SCISSORS -> RobotFaceView.Expression.SMIRK
                else -> RobotFaceView.Expression.SURPRISED
            }
        )
    }

    private suspend fun save() {
        RuhiDatabase.getInstance(this).gameResultDao().insert(
            GameResult(
                gameType = "ROCK_PAPER_SCISSORS",
                playerScore = you,
                ruhiScore = ruhi,
                outcome = if (you > ruhi) "WIN" else if (you < ruhi) "LOSE" else "DRAW",
                durationMs = System.currentTimeMillis() - startAt,
                timestamp = System.currentTimeMillis(),
                bondLevelAtTime = 0
            )
        )
    }
}
