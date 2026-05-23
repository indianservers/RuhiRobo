package com.indianservers.ruhi

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class TicTacToeState { PLAYING, PLAYER_WIN, RUHI_WIN, DRAW }
enum class TicTacToeDifficulty { EASY, MEDIUM, HARD, IMPOSSIBLE }

class TicTacToeGame(var difficulty: TicTacToeDifficulty = TicTacToeDifficulty.HARD) {
    val board = IntArray(9)
    var state: TicTacToeState = TicTacToeState.PLAYING
    private var impossibleMistakeUsed = false

    fun reset() {
        board.fill(0)
        state = TicTacToeState.PLAYING
        impossibleMistakeUsed = false
    }

    fun playerMove(index: Int): Boolean {
        if (state != TicTacToeState.PLAYING || index !in 0..8 || board[index] != 0) return false
        board[index] = 1
        updateState()
        return true
    }

    fun ruhiMove(): Int? {
        if (state != TicTacToeState.PLAYING) return null
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return null
        val randomChance = when (difficulty) {
            TicTacToeDifficulty.EASY -> 0.7f
            TicTacToeDifficulty.MEDIUM -> 0.3f
            TicTacToeDifficulty.HARD -> 0f
            TicTacToeDifficulty.IMPOSSIBLE -> if (!impossibleMistakeUsed) 1f else 0f
        }
        val move = if (Random.nextFloat() < randomChance) {
            impossibleMistakeUsed = true
            empty.random()
        } else {
            bestMove()
        }
        board[move] = 2
        updateState()
        return move
    }

    fun bestMove(): Int {
        var bestScore = Int.MIN_VALUE
        var best = board.indices.first { board[it] == 0 }
        for (i in board.indices) {
            if (board[i] == 0) {
                board[i] = 2
                val score = minimax(board, 0, isMaximizing = false, alpha = Int.MIN_VALUE, beta = Int.MAX_VALUE)
                board[i] = 0
                if (score > bestScore) {
                    bestScore = score
                    best = i
                }
            }
        }
        return best
    }

    fun minimax(board: IntArray, depth: Int, isMaximizing: Boolean, alpha: Int, beta: Int): Int {
        winner(board)?.let {
            return when (it) {
                2 -> 10 - depth
                1 -> depth - 10
                else -> 0
            }
        }
        if (board.none { it == 0 }) return 0
        var a = alpha
        var b = beta
        return if (isMaximizing) {
            var best = Int.MIN_VALUE
            for (i in board.indices) if (board[i] == 0) {
                board[i] = 2
                best = maxOf(best, minimax(board, depth + 1, false, a, b))
                board[i] = 0
                a = maxOf(a, best)
                if (b <= a) break
            }
            best
        } else {
            var best = Int.MAX_VALUE
            for (i in board.indices) if (board[i] == 0) {
                board[i] = 1
                best = minOf(best, minimax(board, depth + 1, true, a, b))
                board[i] = 0
                b = minOf(b, best)
                if (b <= a) break
            }
            best
        }
    }

    fun threatFor(player: Int): Boolean = wins.any { line -> line.count { board[it] == player } == 2 && line.any { board[it] == 0 } }

    private fun updateState() {
        state = when (winner(board)) {
            1 -> TicTacToeState.PLAYER_WIN
            2 -> TicTacToeState.RUHI_WIN
            else -> if (board.none { it == 0 }) TicTacToeState.DRAW else TicTacToeState.PLAYING
        }
    }

    private fun winner(target: IntArray): Int? {
        return wins.firstNotNullOfOrNull { line ->
            val v = target[line[0]]
            if (v != 0 && target[line[1]] == v && target[line[2]] == v) v else null
        }
    }

    companion object {
        val wins = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
        )
    }
}

class TicTacToeActivity : AppCompatActivity() {
    private val game = TicTacToeGame(TicTacToeDifficulty.HARD)
    private lateinit var face: RobotFaceView
    private lateinit var status: TextView
    private lateinit var cells: List<Button>
    private var you = 0
    private var ruhi = 0
    private var draws = 0
    private var startAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAt = System.currentTimeMillis()
        face = RobotFaceView(this)
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER }
        val grid = GridLayout(this).apply { rowCount = 3; columnCount = 3; useDefaultMargins = true }
        cells = (0 until 9).map { index ->
            Button(this).apply {
                textSize = 42f
                setOnClickListener { playerTap(index) }
                grid.addView(this, GridLayout.LayoutParams().apply { width = 170; height = 150 })
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 10, 18))
            addView(face, LinearLayout.LayoutParams(-1, 0, 0.3f))
            addView(grid, LinearLayout.LayoutParams(-1, 0, 0.55f).apply { gravity = Gravity.CENTER })
            addView(status, LinearLayout.LayoutParams(-1, 0, 0.15f))
        })
        render("Your move")
    }

    private fun playerTap(index: Int) {
        if (!game.playerMove(index)) return
        face.setExpression(if (game.threatFor(1)) RobotFaceView.Expression.WORRIED else RobotFaceView.Expression.CURIOUS)
        render("Ruhi is thinking...")
        if (game.state != TicTacToeState.PLAYING) return finishGame()
        lifecycleScope.launch {
            delay(if (game.difficulty == TicTacToeDifficulty.HARD) 1200 else 450)
            val beforeThreat = game.threatFor(1)
            game.ruhiMove()
            face.setExpression(
                when {
                    game.state == TicTacToeState.RUHI_WIN -> RobotFaceView.Expression.GRIN
                    beforeThreat -> RobotFaceView.Expression.SMIRK
                    game.threatFor(2) -> RobotFaceView.Expression.GRIN
                    else -> RobotFaceView.Expression.THINKING
                }
            )
            render(if (beforeThreat) "Not so fast!" else "Your move")
            if (game.state != TicTacToeState.PLAYING) finishGame()
        }
    }

    private fun finishGame() {
        when (game.state) {
            TicTacToeState.PLAYER_WIN -> { you++; face.setExpression(RobotFaceView.Expression.SHOCK); render("Beginner's luck. Best of 3?") }
            TicTacToeState.RUHI_WIN -> { ruhi++; face.victoryPose(); render("Checkmate! Wait... that's chess. Still won!") }
            TicTacToeState.DRAW -> { draws++; face.setExpression(RobotFaceView.Expression.SQUINT); render("You're smarter than you look.") }
            else -> Unit
        }
        lifecycleScope.launch {
            saveResult()
            delay(1700)
            game.reset()
            render("Your move")
        }
    }

    private fun render(message: String) {
        cells.forEachIndexed { i, cell ->
            cell.text = when (game.board[i]) { 1 -> "X"; 2 -> "O"; else -> "" }
            cell.alpha = if (game.state == TicTacToeState.PLAYING || game.board[i] != 0) 1f else 0.4f
        }
        status.text = "$message    You: $you | Ruhi: $ruhi | Draws: $draws"
    }

    private suspend fun saveResult() {
        RuhiDatabase.getInstance(this).gameResultDao().insert(
            GameResult(
                gameType = "TIC_TAC_TOE",
                playerScore = you,
                ruhiScore = ruhi,
                outcome = when (game.state) {
                    TicTacToeState.PLAYER_WIN -> "WIN"
                    TicTacToeState.RUHI_WIN -> "LOSE"
                    TicTacToeState.DRAW -> "DRAW"
                    else -> "DRAW"
                },
                durationMs = System.currentTimeMillis() - startAt,
                timestamp = System.currentTimeMillis(),
                bondLevelAtTime = 0
            )
        )
    }
}
