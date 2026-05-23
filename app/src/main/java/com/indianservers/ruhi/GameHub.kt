package com.indianservers.ruhi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.indianservers.ruhi.hardware.BleRobotManager
import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameCard(val type: String, val title: String, val activity: Class<*>?, val expression: RobotFaceView.Expression, val taunt: String)

class GameHub : AppCompatActivity() {
    private lateinit var face: RobotFaceView
    private lateinit var grid: GridLayout
    private val games by lazy {
        listOf(
            GameCard("DANCE", "DANCE OFF", DanceActivity::class.java, RobotFaceView.Expression.COOL, "I've got new moves."),
            GameCard("FIGHT", "FIGHT", FightActivity::class.java, RobotFaceView.Expression.ANGRY, "I want a rematch."),
            GameCard("SOCCER", "SOCCER", SoccerActivity::class.java, RobotFaceView.Expression.GRIN, "I've been practicing!"),
            GameCard("TIC_TAC_TOE", "TIC-TAC-TOE", TicTacToeActivity::class.java, RobotFaceView.Expression.THINKING, "Let's see how smart you are."),
            GameCard("ROCK_PAPER_SCISSORS", "ROCK PAPER SCISSORS", RockPaperScissorsActivity::class.java, RobotFaceView.Expression.SMIRK, "Shoot on three."),
            GameCard("WHACK_A_MOLE", "WHACK-A-MOLE", WhackAMoleActivity::class.java, RobotFaceView.Expression.CURIOUS, "Find me if you can!"),
            GameCard("SIMON_BLINK", "SIMON BLINK", GameActivity::class.java, RobotFaceView.Expression.WINK, "Blink if you're ready."),
            GameCard("COPY_FACE", "COPY MY FACE", GameActivity::class.java, RobotFaceView.Expression.HAPPY, "Mirror me!"),
            GameCard("MORE", "MORE COMING SOON", null, RobotFaceView.Expression.STARS, "I'm inventing something.")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        face = RobotFaceView(this)
        grid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = true }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 10, 18))
            addView(face, LinearLayout.LayoutParams(-1, 0, 0.36f))
            addView(TextView(context).apply {
                text = "What should we play?"
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, 64))
            addView(grid, LinearLayout.LayoutParams(-1, 0, 0.64f))
        })
        renderCards()
    }

    private fun renderCards() {
        grid.removeAllViews()
        games.forEach { game ->
            grid.addView(Button(this).apply {
                text = "${game.title}\n${highScoreText(game.type)}\nPLAY"
                setOnClickListener {
                    face.setExpression(game.expression)
                    if (game.activity != null) startActivity(Intent(this@GameHub, game.activity)) else face.emitParticles(RobotFaceView.ParticleType.SPARKLE, 20)
                }
                setOnLongClickListener {
                    face.setExpression(game.expression)
                    true
                }
            }, GridLayout.LayoutParams().apply { width = resources.displayMetrics.widthPixels / 2 - 24; height = 170 })
        }
    }

    private fun highScoreText(type: String): String {
        var label = "No record yet"
        lifecycleScope.launch {
            label = withContext(Dispatchers.IO) {
                val recent = RuhiDatabase.getInstance(this@GameHub).gameResultDao().recentForGame(type, 1).firstOrNull()
                recent?.let { "Last ${it.playerScore}-${it.ruhiScore} ${it.outcome}" } ?: "No record yet"
            }
        }
        return label
    }
}

class CelebrationEngine(
    private val context: Context,
    private val faceView: RobotFaceView,
    private val danceEngine: DanceEngine = DanceEngine(faceView, RobotHardwareController(BleRobotManager(context)))
) {
    suspend fun celebrate(result: GameResult, streak: Int = 1, comeback: Boolean = false, firstWin: Boolean = false) {
        when {
            firstWin -> {
                faceView.setExpression(RobotFaceView.Expression.CRYING)
                faceView.emitParticles(RobotFaceView.ParticleType.CONFETTI, 50)
                kotlinx.coroutines.delay(500)
                faceView.setExpression(RobotFaceView.Expression.HAPPY)
            }
            comeback -> {
                faceView.setExpression(RobotFaceView.Expression.SHOCK)
                kotlinx.coroutines.delay(350)
                faceView.setExpression(RobotFaceView.Expression.STARS)
            }
            streak >= 3 -> {
                faceView.setExpression(RobotFaceView.Expression.HEARTS)
                faceView.emitParticles(RobotFaceView.ParticleType.HEART, 30)
                faceView.emitParticles(RobotFaceView.ParticleType.STAR, 30)
                danceEngine.start((context as? AppCompatActivity)?.lifecycleScope ?: return, DanceStyle.ROBOT_DANCE)
            }
            result.outcome == "WIN" -> {
                faceView.setExpression(RobotFaceView.Expression.GRIN)
                faceView.emitParticles(RobotFaceView.ParticleType.CONFETTI, 35)
            }
            result.outcome == "LOSE" -> {
                val bond = result.bondLevelAtTime
                faceView.setExpression(if (bond >= 80) RobotFaceView.Expression.DEAD else RobotFaceView.Expression.SAD)
            }
        }
    }
}

class MultiplayerGameSync(private val deviceName: String = "RuhiRobo") {
    fun challengePayload(gameType: String): String = "$deviceName wants to play $gameType"
    fun supports(gameType: String): Boolean = gameType in setOf("TIC_TAC_TOE", "ROCK_PAPER_SCISSORS")
}
