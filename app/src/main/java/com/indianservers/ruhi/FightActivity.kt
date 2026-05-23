package com.indianservers.ruhi

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
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

class FightActivity : AppCompatActivity() {
    private lateinit var playerFace: RobotFaceView
    private lateinit var opponentFace: RobotFaceView
    private lateinit var hud: GameHUD
    private lateinit var engine: FightEngine
    private var cpuJob: Job? = null
    private var lastPlayerMove: FightMove? = null
    private var round = 1
    private var wins = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hardware = RobotHardwareController(BleRobotManager(this))
        engine = FightEngine(hardware, FightDifficulty.MEDIUM)
        playerFace = RobotFaceView(this)
        opponentFace = RobotFaceView(this).apply { scaleX = -1f; alpha = 0.72f }
        hud = GameHUD(this)
        setContentView(FrameLayout(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(playerFace, LinearLayout.LayoutParams(0, -1, 1f))
                addView(opponentFace, LinearLayout.LayoutParams(0, -1, 1f))
            }, FrameLayout.LayoutParams(-1, -1))
            addView(hud, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(context).apply {
                text = "VS"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 42f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
            addView(buttonRow(), FrameLayout.LayoutParams(-1, 104, Gravity.BOTTOM))
        })
        updateHud()
        startCpu()
    }

    private fun buttonRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0x77000000)
            listOf(FightMove.JAB, FightMove.KICK, FightMove.BLOCK, FightMove.SPECIAL_MOVE).forEach { move ->
                addView(Button(context).apply {
                    text = if (move == FightMove.SPECIAL_MOVE) "SPECIAL" else move.name
                    setOnClickListener { performPlayerMove(move) }
                })
            }
        }
    }

    private fun performPlayerMove(move: FightMove) {
        lifecycleScope.launch {
            lastPlayerMove = move
            playerFace.playCombatMove(if (move == FightMove.SPECIAL_MOVE) FightMove.SPIN_KICK else move)
            val result = engine.playerMove(move)
            if (result == FightResult.HIT || result == FightResult.KNOCKOUT) opponentFace.takeCombatHit(fromLeft = true)
            if (result == FightResult.KNOCKOUT) finishRound(playerWon = true)
            hud.comboText = if (engine.player.comboCount > 1) "COMBO x${engine.player.comboCount}" else ""
            updateHud()
        }
    }

    private fun startCpu() {
        cpuJob = lifecycleScope.launch {
            while (isActive) {
                delay((1000..3000).random().toLong())
                engine.tickRecharge()
                val (move, result) = engine.cpuMove(lastPlayerMove)
                opponentFace.playCombatMove(move)
                if (result == FightResult.HIT || result == FightResult.KNOCKOUT) playerFace.takeCombatHit(fromLeft = false)
                if (result == FightResult.KNOCKOUT) finishRound(playerWon = false)
                updateHud()
            }
        }
    }

    private fun finishRound(playerWon: Boolean) {
        if (playerWon) {
            wins++
            playerFace.victoryPose()
            opponentFace.defeatPose()
            hud.scoreText = "Ruhi wins round!"
        } else {
            playerFace.defeatPose()
            opponentFace.victoryPose()
            hud.scoreText = "Rematch!"
        }
        lifecycleScope.launch {
            delay(1600)
            if (round >= 3) {
                RuhiDatabase.getInstance(this@FightActivity).tournamentDao().insert(
                    TournamentRecord(date = System.currentTimeMillis(), placement = if (wins >= 2) 1 else 2, roundsWon = wins)
                )
                hud.scoreText = if (wins >= 2) "Tournament won!" else "Tournament complete"
                playerFace.victoryPose()
            } else {
                round++
                resetRound()
            }
        }
    }

    private fun resetRound() {
        engine.player.hp = 1f
        engine.opponent.hp = 1f
        engine.player.stamina = 1f
        engine.opponent.stamina = 1f
        engine.player.comboCount = 0
        engine.opponent.comboCount = 0
        playerFace.resetBodyPose()
        opponentFace.resetBodyPose()
        hud.scoreText = ""
        updateHud()
    }

    private fun updateHud() {
        hud.roundText = "Round $round/3"
        hud.playerHp = engine.player.hp
        hud.opponentHp = engine.opponent.hp
        hud.playerSpecial = engine.player.specialMeter
        hud.opponentSpecial = engine.opponent.specialMeter
    }

    override fun onDestroy() {
        cpuJob?.cancel()
        super.onDestroy()
    }
}
