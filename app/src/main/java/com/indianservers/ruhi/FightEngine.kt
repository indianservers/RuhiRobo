package com.indianservers.ruhi

import com.indianservers.ruhi.hardware.LedEffect
import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.delay
import kotlin.random.Random

data class FighterState(
    var hp: Float = 1f,
    var stamina: Float = 1f,
    var stance: FightStance = FightStance.NEUTRAL,
    var isBlocking: Boolean = false,
    var isStunned: Boolean = false,
    var stunDurationMs: Int = 0,
    var comboCount: Int = 0,
    var specialMeter: Float = 0f
)

enum class FightStance { NEUTRAL, AGGRESSIVE, DEFENSIVE, EXHAUSTED, KNOCKED_DOWN }
enum class FightMove { JAB, CROSS, UPPERCUT, KICK, BLOCK, DODGE_LEFT, DODGE_RIGHT, SPECIAL_MOVE, GRAB, SWEEP, HEADBUTT, SPIN_KICK }
enum class FightDifficulty { EASY, MEDIUM, HARD }
enum class FightResult { HIT, BLOCKED, DODGED, STUNNED, KNOCKOUT, EXHAUSTED }

class FightEngine(
    private val hardware: RobotHardwareController? = null,
    private val difficulty: FightDifficulty = FightDifficulty.MEDIUM
) {
    val player = FighterState()
    val opponent = FighterState()

    fun tickRecharge() {
        player.stamina = (player.stamina + 0.035f).coerceAtMost(1f)
        opponent.stamina = (opponent.stamina + 0.03f).coerceAtMost(1f)
        if (player.stunDurationMs > 0) player.stunDurationMs -= 100 else player.isStunned = false
        if (opponent.stunDurationMs > 0) opponent.stunDurationMs -= 100 else opponent.isStunned = false
    }

    suspend fun playerMove(move: FightMove): FightResult {
        val result = applyMove(player, opponent, move)
        syncHardware(move, result)
        return result
    }

    suspend fun cpuMove(lastPlayerMove: FightMove?): Pair<FightMove, FightResult> {
        val move = chooseCpuMove(lastPlayerMove)
        val result = applyMove(opponent, player, move)
        return move to result
    }

    private fun applyMove(attacker: FighterState, defender: FighterState, move: FightMove): FightResult {
        if (attacker.isStunned) return FightResult.STUNNED
        if (move == FightMove.BLOCK) {
            attacker.isBlocking = true
            attacker.stance = FightStance.DEFENSIVE
            attacker.stamina = (attacker.stamina - 0.06f).coerceAtLeast(0f)
            return FightResult.BLOCKED
        }
        if (attacker.stamina < cost(move)) {
            attacker.stance = FightStance.EXHAUSTED
            return FightResult.EXHAUSTED
        }
        attacker.isBlocking = false
        attacker.stamina = (attacker.stamina - cost(move)).coerceAtLeast(0f)
        val dodged = defender.stance == FightStance.DEFENSIVE && Random.nextFloat() < dodgeChance()
        if (dodged) return FightResult.DODGED
        val blocked = defender.isBlocking && move !in listOf(FightMove.GRAB, FightMove.SWEEP)
        val damage = damage(move) * if (blocked) 0.25f else 1f
        defender.hp = (defender.hp - damage).coerceAtLeast(0f)
        attacker.comboCount++
        attacker.specialMeter = (attacker.specialMeter + damage * 1.8f).coerceAtMost(1f)
        defender.stance = if (defender.hp <= 0f) FightStance.KNOCKED_DOWN else FightStance.NEUTRAL
        if (move in listOf(FightMove.UPPERCUT, FightMove.SPECIAL_MOVE, FightMove.SPIN_KICK)) {
            defender.isStunned = true
            defender.stunDurationMs = 450
        }
        return if (defender.hp <= 0f) FightResult.KNOCKOUT else if (blocked) FightResult.BLOCKED else FightResult.HIT
    }

    private fun chooseCpuMove(lastPlayerMove: FightMove?): FightMove {
        return when (difficulty) {
            FightDifficulty.EASY -> listOf(FightMove.JAB, FightMove.KICK, FightMove.JAB).random()
            FightDifficulty.MEDIUM -> when {
                lastPlayerMove in listOf(FightMove.JAB, FightMove.CROSS, FightMove.KICK) && Random.nextFloat() < 0.4f -> FightMove.BLOCK
                player.isBlocking -> FightMove.GRAB
                else -> listOf(FightMove.JAB, FightMove.CROSS, FightMove.KICK).random()
            }
            FightDifficulty.HARD -> when {
                lastPlayerMove == FightMove.SPECIAL_MOVE -> FightMove.DODGE_LEFT
                lastPlayerMove in listOf(FightMove.JAB, FightMove.CROSS, FightMove.KICK) && Random.nextFloat() < 0.7f -> FightMove.BLOCK
                player.isBlocking -> FightMove.GRAB
                opponent.specialMeter > 0.85f -> FightMove.SPIN_KICK
                else -> listOf(FightMove.JAB, FightMove.JAB, FightMove.CROSS, FightMove.UPPERCUT, FightMove.KICK).random()
            }
        }
    }

    private fun cost(move: FightMove): Float = when (move) {
        FightMove.JAB -> 0.08f
        FightMove.CROSS -> 0.12f
        FightMove.UPPERCUT, FightMove.KICK -> 0.18f
        FightMove.SPECIAL_MOVE, FightMove.SPIN_KICK -> 0.65f
        FightMove.BLOCK, FightMove.DODGE_LEFT, FightMove.DODGE_RIGHT -> 0.06f
        FightMove.GRAB, FightMove.SWEEP, FightMove.HEADBUTT -> 0.22f
    }

    private fun damage(move: FightMove): Float = when (move) {
        FightMove.JAB -> 0.055f
        FightMove.CROSS -> 0.09f
        FightMove.UPPERCUT -> 0.13f
        FightMove.KICK -> 0.12f
        FightMove.SPECIAL_MOVE, FightMove.SPIN_KICK -> 0.28f
        FightMove.GRAB, FightMove.SWEEP, FightMove.HEADBUTT -> 0.1f
        else -> 0f
    }

    private fun dodgeChance(): Float = when (difficulty) {
        FightDifficulty.EASY -> 0.05f
        FightDifficulty.MEDIUM -> 0.18f
        FightDifficulty.HARD -> 0.34f
    }

    private suspend fun syncHardware(move: FightMove, result: FightResult) {
        val hw = hardware ?: return
        when (move) {
            FightMove.JAB -> hw.driveForward(4f, 0.8f)
            FightMove.KICK -> { hw.turnRight(45f); delay(140); hw.turnLeft(45f) }
            FightMove.BLOCK -> hw.lookAt(0f, 0.8f)
            FightMove.SPIN_KICK, FightMove.SPECIAL_MOVE -> { hw.setLed(android.graphics.Color.MAGENTA, LedEffect.RAINBOW); hw.spin(1f) }
            else -> Unit
        }
        if (result == FightResult.HIT) hw.driveBackward(3f, 0.45f)
        val color = when {
            player.specialMeter > 0.95f -> android.graphics.Color.MAGENTA
            player.hp > 0.5f -> android.graphics.Color.GREEN
            player.hp > 0.25f -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }
        hw.setLed(color, if (player.hp < 0.25f) LedEffect.PULSE else LedEffect.SOLID)
    }
}

object CombatAnimator {
    fun keyframesFor(move: FightMove): List<BodyKeyframe> = when (move) {
        FightMove.JAB -> listOf(
            k(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.SQUINT),
            k(90, 0f, 0f, 0f, 15f, 0f, -90f, 0f, 0f, RobotFaceView.Expression.SQUINT),
            k(200, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.NEUTRAL)
        )
        FightMove.CROSS -> listOf(k(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.ANGRY), k(150, 0f, 8f, 0f, 20f, -90f, 0f, 0f, 10f, RobotFaceView.Expression.ANGRY), k(280, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.NEUTRAL))
        FightMove.UPPERCUT -> listOf(k(0, 0f, 0f, 20f, 0f, -45f, 0f, 0f, 0f, RobotFaceView.Expression.GRIN), k(250, -15f, 0f, -15f, 0f, 90f, 0f, 0f, 0f, RobotFaceView.Expression.GRIN, RobotFaceView.ParticleType.STAR))
        FightMove.KICK -> listOf(k(0, 0f, 0f, 0f, 0f, -30f, 30f, 0f, 0f, RobotFaceView.Expression.COOL), k(300, 0f, 0f, -4f, 6f, -65f, 65f, 0f, 120f, RobotFaceView.Expression.COOL))
        FightMove.BLOCK -> listOf(k(0, 0f, 0f, 0f, -10f, -30f, 30f, 0f, 0f, RobotFaceView.Expression.WORRIED), k(350, 0f, 0f, 0f, -10f, -45f, 45f, 0f, 0f, RobotFaceView.Expression.SQUINT))
        FightMove.DODGE_LEFT -> listOf(k(0, 0f, 20f, 0f, -12f, 20f, -20f, -8f, 8f, RobotFaceView.Expression.SMIRK))
        FightMove.DODGE_RIGHT -> listOf(k(0, 0f, -20f, 0f, 12f, 20f, -20f, 8f, -8f, RobotFaceView.Expression.SMIRK))
        FightMove.SPIN_KICK, FightMove.SPECIAL_MOVE -> listOf(k(0, 0f, 0f, 0f, 0f, -60f, 60f, 0f, 0f, RobotFaceView.Expression.COOL), k(300, 0f, 0f, -8f, 180f, -80f, 80f, 0f, 110f, RobotFaceView.Expression.STARS, RobotFaceView.ParticleType.SPARKLE), k(600, 0f, 0f, 0f, 360f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.GRIN))
        FightMove.GRAB -> listOf(k(0, 0f, 0f, 0f, 8f, -80f, 80f, 0f, 0f, RobotFaceView.Expression.SQUINT))
        FightMove.SWEEP -> listOf(k(0, 0f, 0f, 10f, -18f, -20f, 20f, -90f, 15f, RobotFaceView.Expression.SMIRK))
        FightMove.HEADBUTT -> listOf(k(0, -18f, 0f, -8f, 0f, 10f, -10f, 0f, 0f, RobotFaceView.Expression.ANGRY), k(160, 16f, 0f, 8f, 0f, 10f, -10f, 0f, 0f, RobotFaceView.Expression.SHOCK, RobotFaceView.ParticleType.STAR))
    }

    private fun k(time: Int, headBob: Float, headWag: Float, torsoY: Float, torsoRot: Float, armL: Float, armR: Float, legL: Float, legR: Float, expr: RobotFaceView.Expression, particle: RobotFaceView.ParticleType? = null): BodyKeyframe {
        return BodyKeyframe(time, headBob, headWag, torsoY, torsoRot, armL, armR, legL, legR, expr, particle)
    }
}
