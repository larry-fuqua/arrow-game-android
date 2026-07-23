package com.larry.arrowgame.game

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.larry.arrowgame.data.GameStorage
import com.larry.arrowgame.data.ScoreEntry
import com.larry.arrowgame.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class Screen { LOGIN, LEVELS, PLAY, CELEBRATE, COMPLETE }

data class CompletionInfo(
    val score: Int,
    val base: Int,
    val arrowGross: Int,
    val penalties: Int,
    val timeBonus: Int,
    val targetSec: Float,
    val secondsUnder: Int,
    val elapsed: Float,
    val moves: Int,
    val best: Int,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = GameStorage(app)
    private val audio = GameAudio(app).also { it.muted = storage.isMuted() }

    var screen by mutableStateOf(Screen.LOGIN)
        private set
    var profile by mutableStateOf<UserProfile?>(storage.loadProfile())
        private set
    var statusMsg by mutableStateOf("")
        private set
    private var statusUntil = 0L

    var nameInput by mutableStateOf("")

    var level by mutableStateOf<LevelConfig?>(null)
        private set
    var puzzle by mutableStateOf<Puzzle?>(null)
        private set
    var generating by mutableStateOf(false)
        private set

    var hoverArrowId by mutableStateOf<Int?>(null)
    var errorArrowId by mutableStateOf<Int?>(null)
    private var errorUntil = 0L
    var penaltyPoints by mutableIntStateOf(0)
        private set
    var lastPointsPopup by mutableStateOf<Pair<String, Long>?>(null)
    var muted by mutableStateOf(storage.isMuted())

    /** Last computed on-screen cell size from BoardCanvas layout. */
    var layoutCellSize by mutableFloatStateOf(Config.BASE_CELL.toFloat())

    var playStartedMs = 0L
        private set
    var elapsedFrozen by mutableStateOf<Float?>(null)
        private set

    val flowAnims = mutableStateListOf<FlowAnim>()
    val fireworks = mutableStateListOf<FireworkParticle>()
    private var fwBurstT = 0L
    var celebrationT0 by mutableStateOf<Long?>(null)
        private set
    var completion by mutableStateOf<CompletionInfo?>(null)
        private set
    private var pendingComplete = false

    /** Tick counter to drive recomposition during play/celebration. */
    var frameTick by mutableIntStateOf(0)
        private set

    private var tickJob: Job? = null

    init {
        if (profile != null) screen = Screen.LEVELS
        ensureTicker()
    }

    fun statusActive(): Boolean =
        statusMsg.isNotEmpty() && SystemClock.elapsedRealtime() < statusUntil

    fun setStatus(msg: String, seconds: Float = 4f) {
        statusMsg = msg
        statusUntil = SystemClock.elapsedRealtime() + (seconds * 1000).toLong()
    }

    fun loginLocal() {
        val name = nameInput.trim()
        if (name.isEmpty()) {
            setStatus("Enter a display name first.")
            return
        }
        profile = storage.makeNamedLocal(name)
        screen = Screen.LEVELS
        setStatus("Signed in as ${profile!!.displayName}")
    }

    fun loginGuest() {
        profile = storage.makeGuest("Guest")
        screen = Screen.LEVELS
        setStatus("Welcome, ${profile!!.displayName}!")
    }

    fun logout() {
        storage.clearProfile()
        profile = null
        nameInput = ""
        screen = Screen.LOGIN
        puzzle = null
    }

    fun startLevel(levelId: Int) {
        val lv = getLevel(levelId)
        level = lv
        screen = Screen.PLAY
        playStartedMs = SystemClock.elapsedRealtime()
        elapsedFrozen = null
        flowAnims.clear()
        pendingComplete = false
        errorArrowId = null
        penaltyPoints = 0
        generating = true
        puzzle = null
        viewModelScope.launch {
            val p = withContext(Dispatchers.Default) {
                var result: Puzzle? = null
                var seed: Int? = null
                for (i in 0 until 12) {
                    val gen = generatePuzzle(lv, seed)
                    if (verifySolvable(gen) && gen.arrows.isNotEmpty() && gen.shape.isNotEmpty()) {
                        result = gen.copyForPlay()
                        break
                    }
                    seed = null
                }
                result ?: generatePuzzle(lv).copyForPlay()
            }
            puzzle = p
            generating = false
            if (p.shapeName.isNotEmpty()) setStatus("Shape: ${p.shapeName}", 2.5f)
        }
    }

    fun regen() {
        val lv = level ?: return
        startLevel(lv.id)
    }

    fun backToLevels() {
        screen = Screen.LEVELS
        puzzle = null
        flowAnims.clear()
        pendingComplete = false
        errorArrowId = null
        fireworks.clear()
    }

    fun replay() {
        level?.let { startLevel(it.id) }
    }

    fun toggleMute() {
        muted = !muted
        audio.muted = muted
        storage.setMuted(muted)
        setStatus(if (muted) "Sound muted" else "Sound on", 1.5f)
        if (!muted) audio.playTick(4)
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }

    fun elapsed(): Float {
        elapsedFrozen?.let { return it }
        if (playStartedMs == 0L) return 0f
        return (SystemClock.elapsedRealtime() - playStartedMs) / 1000f
    }

    fun isErrorBlinking(arrowId: Int): Boolean {
        return errorArrowId == arrowId && SystemClock.elapsedRealtime() < errorUntil
    }

    fun errorBlinkPhaseOn(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (errorArrowId == null || now >= errorUntil) return false
        val left = (errorUntil - now) / 1000f
        return ((left * 16).toInt() % 2) == 0
    }

    /** Highlight arrow under finger while dragging (release commits selection). */
    fun setHoverCell(r: Int?, c: Int?) {
        if (r == null || c == null) {
            hoverArrowId = null
            return
        }
        hoverArrowId = puzzle?.cellArrow(r, c)?.id
    }

    /** Commit selection for the cell under the finger on touch-up. */
    fun tryClickCell(r: Int, c: Int) {
        val p = puzzle ?: return
        if (p.isComplete() || generating) return
        val arrow = p.cellArrow(r, c) ?: return
        hoverArrowId = null
        if (!p.isRemovable(arrow)) {
            errorArrowId = arrow.id
            errorUntil = SystemClock.elapsedRealtime() + (Config.ERROR_BLINK_MS * 1000).toLong()
            val pen = Config.BLOCKED_CLICK_PENALTY
            p.score = maxOf(0, p.score - pen)
            penaltyPoints += pen
            lastPointsPopup = "−$pen" to SystemClock.elapsedRealtime()
            setStatus("Blocked (−$pen) — clear arrows in its path first.", 1.5f)
            audio.playError()
            // force state refresh
            puzzle = p
            return
        }
        val cells = arrow.cells.toList()
        val exitDir = arrow.exitDirection()
        val pts = p.removeArrow(arrow, Config.POINTS_PER_CELL, shade = false)
        val cs = layoutCellSize.coerceAtLeast(1f)
        flowAnims.add(FlowAnim(cells, exitDir, cs, p.width, p.height))
        lastPointsPopup = "+$pts" to SystemClock.elapsedRealtime()
        puzzle = p
        if (p.isComplete()) pendingComplete = true
    }

    fun tickFlowAnims() {
        val p = puzzle ?: return
        val alive = ArrayList<FlowAnim>()
        var shadedChanged = false
        for (anim in flowAnims.toList()) {
            val count = anim.shadedCount().coerceIn(0, anim.cells.size)
            if (count > anim.lastShadedCount) {
                val from = anim.lastShadedCount.coerceIn(0, anim.cells.size)
                val to = count.coerceIn(0, anim.cells.size)
                if (to > from) {
                    for (cell in anim.cells.subList(from, to)) {
                        p.shaded.add(cell)
                    }
                    for (i in anim.lastTickCount until count) {
                        audio.playTick(i)
                    }
                    shadedChanged = true
                }
                anim.lastTickCount = count
                anim.lastShadedCount = count
            }
            if (anim.done()) {
                p.shaded.addAll(anim.cells)
                shadedChanged = true
            } else {
                alive.add(anim)
            }
        }
        if (alive.size != flowAnims.size) {
            flowAnims.clear()
            flowAnims.addAll(alive)
        }
        // frameTick drives redraw; reassign puzzle when shaded cells change so state observers update.
        if (shadedChanged) {
            puzzle = p
        }

        if (pendingComplete && flowAnims.isEmpty()) {
            pendingComplete = false
            audio.playSuccess()
            beginCelebration()
        }
    }

    private fun beginCelebration() {
        val p = puzzle ?: return
        val prof = profile ?: return
        val lv = level ?: return
        elapsedFrozen = elapsed()
        val el = elapsedFrozen!!
        val nArrows = maxOf(1, p.moves)
        val targetSec = Config.SECONDS_PER_ARROW * nArrows
        val secondsUnder = maxOf(0f, targetSec - el).toInt()
        val timeBonus = secondsUnder * Config.TIME_BONUS_PER_SECOND
        val arrowNet = p.score
        val total = arrowNet + timeBonus
        val entry = ScoreEntry(
            userId = prof.userId,
            displayName = prof.displayName,
            levelId = lv.id,
            score = total,
            elapsedSec = el,
            moves = p.moves,
            seed = p.seed,
        )
        storage.saveScore(entry)
        val best = storage.bestForUser(prof.userId, lv.id)
        completion = CompletionInfo(
            score = total,
            base = arrowNet,
            arrowGross = arrowNet + penaltyPoints,
            penalties = penaltyPoints,
            timeBonus = timeBonus,
            targetSec = targetSec,
            secondsUnder = secondsUnder,
            elapsed = el,
            moves = p.moves,
            best = best?.score ?: total,
        )
        fireworks.clear()
        celebrationT0 = SystemClock.elapsedRealtime()
        repeat(4) { spawnFireworks() }
        audio.playCelebrate()
        if (timeBonus > 0) {
            lastPointsPopup = "+$timeBonus time bonus!" to SystemClock.elapsedRealtime()
        }
        screen = Screen.CELEBRATE
    }

    fun finishCelebration() {
        screen = Screen.COMPLETE
        celebrationT0 = null
    }

    fun spawnFireworks(originX: Float? = null, originY: Float? = null, screenW: Float = 1080f, screenH: Float = 1920f) {
        val ox = originX ?: (screenW / 2f + Random.nextInt(-180, 180))
        val oy = originY ?: (screenH / 2f + Random.nextInt(-140, 100))
        val palette = longArrayOf(
            0xFF50C878, 0xFF58A6FF, 0xFFFFBE46, 0xFFFF646E, 0xFFAA82E6,
            0xFFFFFFC8, 0xFFFF78C8, 0xFF78FFFF, 0xFFFFB450,
        )
        for (i in 0 until 36) {
            val ang = Random.nextFloat() * (Math.PI * 2).toFloat()
            val spd = Random.nextFloat() * 300f + 120f
            fireworks.add(
                FireworkParticle(
                    x = ox + Random.nextFloat() * 40f - 20f,
                    y = oy + Random.nextFloat() * 40f - 20f,
                    vx = cos(ang) * spd,
                    vy = sin(ang) * spd - Random.nextFloat() * 100f - 60f,
                    colorArgb = palette[Random.nextInt(palette.size)],
                    life = Random.nextFloat() * 0.9f + 0.9f,
                )
            )
        }
        fwBurstT = SystemClock.elapsedRealtime()
        audio.playPop()
    }

    fun updateFireworks(dt: Float, continuous: Boolean, screenW: Float, screenH: Float) {
        val alive = fireworks.filter { it.update(dt) }
        fireworks.clear()
        fireworks.addAll(alive)
        if (!continuous) return
        if (SystemClock.elapsedRealtime() - fwBurstT > 280) {
            spawnFireworks(screenW = screenW, screenH = screenH)
            if (Random.nextFloat() < 0.55f) spawnFireworks(screenW = screenW, screenH = screenH)
        }
    }

    fun bestForLevel(levelId: Int): ScoreEntry? {
        val uid = profile?.userId ?: return null
        return storage.bestForUser(uid, levelId)
    }

    private fun ensureTicker() {
        if (tickJob != null) return
        tickJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(16)
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last) / 1000f
                last = now
                when (screen) {
                    Screen.PLAY -> tickFlowAnims()
                    Screen.CELEBRATE -> {
                        updateFireworks(dt, continuous = true, 1080f, 1920f)
                        celebrationT0?.let { t0 ->
                            if ((now - t0) / 1000f >= Config.CELEBRATION_SECONDS) {
                                finishCelebration()
                            }
                        }
                    }
                    Screen.COMPLETE -> updateFireworks(dt, continuous = false, 1080f, 1920f)
                    else -> {}
                }
                frameTick++
            }
        }
    }
}
