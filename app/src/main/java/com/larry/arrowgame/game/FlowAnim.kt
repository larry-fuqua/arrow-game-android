package com.larry.arrowgame.game

import android.os.SystemClock

/** Slide the polyline along its path, then out past the board edge. */
class FlowAnim(
    cells: List<Pair<Int, Int>>,
    val exitDirection: Int,
    val cellSize: Float,
    gridW: Int,
    gridH: Int,
    duration: Float? = null,
) {
    val cells: List<Pair<Int, Int>> = cells.toList()
    val path: List<Pair<Float, Float>>
    val travelCells: Float
    val durationSec: Float
    private val t0 = SystemClock.elapsedRealtime()
    var lastShadedCount: Int = 0
    var lastTickCount: Int = 0

    init {
        val n = maxOf(1, this.cells.size)
        val (dr, dc, _) = DIRS[exitDirection]
        val pathList = ArrayList<Pair<Float, Float>>()
        for ((r, c) in this.cells) pathList.add(r.toFloat() to c.toFloat())
        var r = pathList.last().first
        var c = pathList.last().second
        val maxSteps = gridW + gridH + n + 8
        for (i in 0 until maxSteps) {
            r += dr
            c += dc
            pathList.add(r to c)
            if (r.toInt() !in 0 until gridH || c.toInt() !in 0 until gridW) {
                for (extra in 0 until n + 2) {
                    r += dr
                    c += dc
                    pathList.add(r to c)
                }
                break
            }
        }
        path = pathList
        var offIdx = pathList.size - 1
        for ((i, p) in pathList.withIndex()) {
            val pr = p.first.toInt()
            val pc = p.second.toInt()
            if (pr !in 0 until gridH || pc !in 0 until gridW) {
                offIdx = i
                break
            }
        }
        travelCells = maxOf(n, offIdx + 1).toFloat()
        durationSec = duration ?: maxOf(0.35f, 0.055f * travelCells)
    }

    fun progress(): Float {
        val elapsed = (SystemClock.elapsedRealtime() - t0) / 1000f
        return minOf(1f, elapsed / maxOf(0.05f, durationSec))
    }

    fun done(): Boolean = progress() >= 1f

    fun shiftCells(): Float = progress() * travelCells

    fun shadedCount(): Int {
        val n = cells.size
        return minOf(n, (shiftCells() + 0.001f).toInt())
    }

    /** Screen-space centers of the sliding polyline. */
    fun polylinePoints(originX: Float, originY: Float): List<Pair<Float, Float>> {
        val cs = cellSize
        val (dr, dc, _) = DIRS[exitDirection]
        val shift = shiftCells()
        val n = cells.size
        val pts = ArrayList<Pair<Float, Float>>(n)
        for (i in 0 until n) {
            val pos = i + shift
            val i0 = pos.toInt()
            val t = pos - i0
            val (r, c) = if (i0 >= path.size - 1) {
                val last = path.last()
                val extra = pos - (path.size - 1)
                (last.first + dr * extra) to (last.second + dc * extra)
            } else {
                val (r0, c0) = path[i0]
                val (r1, c1) = path[i0 + 1]
                (r0 + (r1 - r0) * t) to (c0 + (c1 - c0) * t)
            }
            pts.add((originX + c * cs + cs / 2f) to (originY + r * cs + cs / 2f))
        }
        return pts
    }
}

data class FireworkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val colorArgb: Long,
    val life: Float,
    var age: Float = 0f,
) {
    fun update(dt: Float): Boolean {
        age += dt
        x += vx * dt
        y += vy * dt
        vy += 220f * dt
        return age < life
    }
}
