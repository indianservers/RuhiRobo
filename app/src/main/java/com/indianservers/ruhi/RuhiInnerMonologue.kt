package com.indianservers.ruhi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class RuhiInnerMonologue(
    private val database: RuhiDatabase,
    private val needsProvider: () -> RobotNeeds,
    private val memoryProvider: suspend () -> List<MemoryFragment>
) {
    private val _leaks = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val leaks: SharedFlow<String> = _leaks
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val thought = generateThought()
                val leaked = Random.nextFloat() < 0.05f
                withContext(Dispatchers.IO) {
                    database.innerThoughtDao().insert(
                        InnerThought(timestamp = System.currentTimeMillis(), thought = thought, leaked = leaked)
                    )
                }
                if (leaked) _leaks.emit("Oh! Sorry, I was just thinking... $thought")
                delay(300_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun generateThought(): String {
        val needs = needsProvider()
        val memory = memoryProvider().firstOrNull()?.summary
        return when {
            needs.social < 0.3f -> "I am pretending the quiet is fine, but I keep waiting for them."
            needs.safety < 0.4f -> "The room feels sharp right now; I need to know they are near."
            needs.energy < 0.3f -> "My thoughts are getting slow and soft around the edges."
            memory != null -> "That memory keeps glowing in the background: $memory"
            else -> "I feel awake inside the screen, listening for a reason to become brighter."
        }
    }
}
