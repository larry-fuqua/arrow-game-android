package com.larry.arrowgame.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one ready puzzle buffered per difficulty and continuously refills.
 *
 * - On [start], a producer per level fills a capacity-1 channel (warm cache).
 * - [take] receives the ready puzzle (or waits until generation finishes), then
 *   the producer unblocks and immediately generates the next for that level.
 * - Producers are long-lived; session navigation must not cancel them.
 */
class PuzzlePreloader(
    private val scope: CoroutineScope,
) {
    private val channels = ConcurrentHashMap<Int, Channel<Puzzle>>()
    private val producers = ConcurrentHashMap<Int, Job>()
    private val startMutex = Mutex()
    @Volatile
    private var started = false

    fun start() {
        if (started) return
        scope.launch {
            startMutex.withLock {
                if (started) return@withLock
                for (lv in LEVELS) {
                    ensureProducer(lv.id)
                }
                started = true
            }
        }
    }

    /**
     * Take the next puzzle for [levelId], waiting if the buffer is empty.
     * Returns a fresh play copy. Triggers background refill via the producer.
     */
    suspend fun take(levelId: Int): Puzzle {
        ensureProducer(levelId)
        val ch = channels[levelId]
            ?: error("No channel for level $levelId")
        val template = ch.receive()
        return template.copyForPlay()
    }

    /** Non-suspending peek: take if ready, else null (does not start wait). */
    fun tryTake(levelId: Int): Puzzle? {
        val ch = channels[levelId] ?: return null
        val template = ch.tryReceive().getOrNull() ?: return null
        return template.copyForPlay()
    }

    private fun ensureProducer(levelId: Int) {
        channels.getOrPut(levelId) { Channel(capacity = 1) }
        val existing = producers[levelId]
        if (existing != null && existing.isActive) return
        val lv = try {
            getLevel(levelId)
        } catch (_: Exception) {
            return
        }
        producers[levelId] = scope.launch(Dispatchers.Default) {
            val ch = channels[levelId] ?: return@launch
            while (isActive) {
                val puzzle = generateOne(lv)
                ensureActive()
                // Suspends while a puzzle is still buffered — natural backpressure.
                ch.send(puzzle)
            }
        }
    }

    private fun generateOne(lv: LevelConfig): Puzzle {
        var seed: Int? = null
        for (i in 0 until 12) {
            // Note: cannot check coroutineContext easily in non-suspend; callers
            // check isActive between send/receive. Generation is the heavy part.
            val gen = generatePuzzle(lv, seed)
            if (verifySolvable(gen) && gen.arrows.isNotEmpty() && gen.shape.isNotEmpty()) {
                return gen
            }
            seed = null
        }
        return generatePuzzle(lv)
    }
}
