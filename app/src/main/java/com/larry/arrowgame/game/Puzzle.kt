package com.larry.arrowgame.game

import kotlin.random.Random

/** Cardinal directions: (dr, dc, name). Index: N=0 E=1 S=2 W=3 */
val DIRS: List<Triple<Int, Int, String>> = listOf(
    Triple(-1, 0, "N"),
    Triple(0, 1, "E"),
    Triple(1, 0, "S"),
    Triple(0, -1, "W"),
)

val OPPOSITE = mapOf(0 to 2, 1 to 3, 2 to 0, 3 to 1)

data class Arrow(
    val id: Int,
    val cells: MutableList<Pair<Int, Int>>,
    var exitDir: Int = 1,
    var removed: Boolean = false,
    var isHole: Boolean = false,
) {
    val length: Int get() = cells.size
    val head: Pair<Int, Int> get() = cells.last()
    val tail: Pair<Int, Int> get() = cells.first()

    fun exitDirection(): Int = exitDir

    fun stepDirections(): List<Int> {
        val dirs = ArrayList<Int>()
        for (i in 0 until cells.size - 1) {
            val (r0, c0) = cells[i]
            val (r1, c1) = cells[i + 1]
            val dr = r1 - r0
            val dc = c1 - c0
            var found = 1
            for ((di, d) in DIRS.withIndex()) {
                if (d.first == dr && d.second == dc) {
                    found = di
                    break
                }
            }
            dirs.add(found)
        }
        return dirs
    }

    fun segmentCount(): Int {
        val dirs = stepDirections()
        if (dirs.isEmpty()) return 1
        var segs = 1
        for (i in 1 until dirs.size) {
            if (dirs[i] != dirs[i - 1]) segs++
        }
        return segs
    }
}

data class Puzzle(
    val width: Int,
    val height: Int,
    val shape: Set<Pair<Int, Int>>,
    val arrows: MutableList<Arrow>,
    val levelId: Int = 1,
    val seed: Int? = null,
    val shapeName: String = "",
    var score: Int = 0,
    var moves: Int = 0,
    val shaded: MutableSet<Pair<Int, Int>> = mutableSetOf(),
    val solutionIds: MutableList<Int> = mutableListOf(),
    val holeCells: Set<Pair<Int, Int>> = emptySet(),
) {
    fun copyForPlay(): Puzzle {
        val newArrows = arrows.map {
            Arrow(it.id, it.cells.toMutableList(), it.exitDir, removed = false, isHole = it.isHole)
        }.toMutableList()
        return Puzzle(
            width = width,
            height = height,
            shape = shape.toSet(),
            arrows = newArrows,
            levelId = levelId,
            seed = seed,
            shapeName = shapeName,
            score = 0,
            moves = 0,
            shaded = mutableSetOf(),
            solutionIds = solutionIds.toMutableList(),
            holeCells = holeCells.toSet(),
        )
    }

    fun cellArrow(r: Int, c: Int): Arrow? {
        val cell = r to c
        for (a in arrows) {
            if (!a.removed && cell in a.cells) return a
        }
        return null
    }

    fun occupied(): Set<Pair<Int, Int>> {
        val occ = HashSet<Pair<Int, Int>>()
        for (a in arrows) {
            if (!a.removed) occ.addAll(a.cells)
        }
        return occ
    }

    fun pathClear(arrow: Arrow): Boolean {
        if (arrow.removed || arrow.cells.isEmpty()) return false
        val d = arrow.exitDirection()
        val (dr, dc, _) = DIRS[d]
        var r = arrow.head.first + dr
        var c = arrow.head.second + dc
        val occ = occupied()
        val own = arrow.cells.toSet()
        while (r in 0 until height && c in 0 until width) {
            val cell = r to c
            if (cell in shape) {
                if (cell in occ && cell !in own) return false
            } else {
                return true
            }
            r += dr
            c += dc
        }
        return true
    }

    fun isRemovable(arrow: Arrow): Boolean = !arrow.removed && pathClear(arrow)

    fun removableArrows(): List<Arrow> = arrows.filter { !it.removed && pathClear(it) }

    fun removeArrow(arrow: Arrow, pointsPerCell: Int = 10, shade: Boolean = true): Int {
        if (!isRemovable(arrow)) return 0
        arrow.removed = true
        if (shade) shaded.addAll(arrow.cells)
        val pts = arrow.length * pointsPerCell
        score += pts
        moves += 1
        return pts
    }

    fun isComplete(): Boolean = arrows.all { it.removed }

    fun remainingCount(): Int = arrows.count { !it.removed }

    fun moveCount(): Int = arrows.size
}

// ---------------------------------------------------------------------------
// Generation helpers
// ---------------------------------------------------------------------------

private fun canStepOut(
    r: Int,
    c: Int,
    d: Int,
    remaining: Set<Pair<Int, Int>>,
    shape: Set<Pair<Int, Int>>,
    height: Int,
    width: Int,
): Boolean {
    val (dr, dc, _) = DIRS[d]
    var nr = r + dr
    var nc = c + dc
    if ((nr to nc) in remaining) return false
    while (nr in 0 until height && nc in 0 until width) {
        if ((nr to nc) in remaining) return false
        if ((nr to nc) !in shape) return true
        nr += dr
        nc += dc
    }
    return true
}

private fun exitHitsOwn(
    path: List<Pair<Int, Int>>,
    exitDir: Int,
    height: Int,
    width: Int,
    shape: Set<Pair<Int, Int>>,
): Boolean {
    if (path.isEmpty()) return false
    val own = path.toSet()
    val (hr, hc) = path.last()
    val (dr, dc, _) = DIRS[exitDir]
    var r = hr + dr
    var c = hc + dc
    while (r in 0 until height && c in 0 until width) {
        if ((r to c) in own) return true
        if ((r to c) !in shape) return false
        r += dr
        c += dc
    }
    return false
}

private fun growWindingPath(
    start: Pair<Int, Int>,
    remaining: Set<Pair<Int, Int>>,
    shape: Set<Pair<Int, Int>>,
    height: Int,
    width: Int,
    segmentSize: Int,
    rng: Random,
    mustExitRemaining: Boolean = true,
): List<Pair<Int, Int>>? {
    if (start !in remaining) return null
    val path = mutableListOf(start)
    val used = mutableSetOf(start)
    var r = start.first
    var c = start.second

    val initOpts = ArrayList<Int>()
    for (d in 0 until 4) {
        val (dr, dc, _) = DIRS[d]
        val nr = r + dr
        val nc = c + dc
        if ((nr to nc) in remaining) {
            initOpts.add(d)
        } else if (canStepOut(r, c, d, remaining - used, shape, height, width)) {
            initOpts.add(d)
        }
    }
    if (initOpts.isEmpty()) return null
    var direction = initOpts[rng.nextInt(initOpts.size)]

    if (canStepOut(r, c, direction, remaining - used, shape, height, width)) {
        val hasNeighbor = (0 until 4).any { d ->
            d != OPPOSITE[direction] &&
                (r + DIRS[d].first to c + DIRS[d].second) in remaining
        }
        if (!hasNeighbor || rng.nextFloat() < 0.25f) {
            val (dr, dc, _) = DIRS[direction]
            if ((r + dr to c + dc) !in remaining) return path
        }
    }

    var safety = remaining.size + 20
    while (safety > 0) {
        safety--
        val maxSeg = maxOf(1, segmentSize)
        val want = rng.nextInt(1, maxSeg + 1)
        var stepsTaken = 0
        for (step in 0 until want) {
            val (dr, dc, _) = DIRS[direction]
            val nr = r + dr
            val nc = c + dc
            if ((nr to nc) in used) break
            if ((nr to nc) in remaining) {
                path.add(nr to nc)
                used.add(nr to nc)
                r = nr
                c = nc
                stepsTaken++
                continue
            }
            if (mustExitRemaining) {
                if (canStepOut(r, c, direction, remaining - used, shape, height, width)) {
                    return path
                }
            }
            return if (path.isNotEmpty()) path else null
        }

        if (canStepOut(r, c, direction, remaining - used, shape, height, width)) {
            if (stepsTaken > 0 || path.isNotEmpty()) {
                val freeTurns = (0 until 4).filter { d ->
                    d != OPPOSITE[direction] &&
                        (r + DIRS[d].first to c + DIRS[d].second) in remaining &&
                        (r + DIRS[d].first to c + DIRS[d].second) !in used
                }
                if (freeTurns.isEmpty() || rng.nextFloat() < 0.55f) return path
            }
        }

        val candidates = ArrayList<Int>()
        for (d in 0 until 4) {
            if (d == OPPOSITE[direction]) continue
            val nr = r + DIRS[d].first
            val nc = c + DIRS[d].second
            if ((nr to nc) in remaining && (nr to nc) !in used) {
                candidates.add(d)
            } else if (canStepOut(r, c, d, remaining - used, shape, height, width)) {
                candidates.add(d)
            }
        }
        if (candidates.isEmpty()) {
            val d = OPPOSITE[direction]!!
            val nr = r + DIRS[d].first
            val nc = c + DIRS[d].second
            if ((nr to nc) in remaining && (nr to nc) !in used) {
                candidates.add(d)
            } else if (canStepOut(r, c, d, remaining - used, shape, height, width)) {
                candidates.add(d)
            }
        }
        if (candidates.isEmpty()) return null
        val turns = candidates.filter { it != direction }
        direction = (if (turns.isNotEmpty()) turns else candidates).let { it[rng.nextInt(it.size)] }

        if (canStepOut(r, c, direction, remaining - used, shape, height, width)) {
            val (dr, dc, _) = DIRS[direction]
            if ((r + dr to c + dc) !in remaining) return path
        }
    }
    return null
}

private fun inHole(cell: Pair<Int, Int>, holeCells: Set<Pair<Int, Int>>?): Boolean =
    !holeCells.isNullOrEmpty() && cell in holeCells

private fun tryPlaceArrowFromBoundary(
    remaining: Set<Pair<Int, Int>>,
    shape: Set<Pair<Int, Int>>,
    height: Int,
    width: Int,
    segmentSize: Int,
    rng: Random,
    preferLen: Int = 4,
    holeCells: Set<Pair<Int, Int>>? = null,
): Pair<List<Pair<Int, Int>>, Int>? {
    val heads = ArrayList<Pair<Pair<Int, Int>, Int>>()
    for ((r, c) in remaining) {
        for (d in 0 until 4) {
            if (canStepOut(r, c, d, remaining - setOf(r to c), shape, height, width)) {
                val (dr, dc, _) = DIRS[d]
                if ((r + dr to c + dc) !in remaining) {
                    heads.add((r to c) to d)
                }
            }
        }
    }
    if (heads.isEmpty()) return null

    val prefer = maxOf(1, preferLen)
    val longBias = prefer >= 5 || remaining.size >= 16
    heads.shuffle(rng)

    for ((headCell, exitDir) in heads.take(minOf(36, heads.size))) {
        val (hr, hc) = headCell
        val headIsHole = inHole(hr to hc, holeCells)

        fun allowed(cell: Pair<Int, Int>): Boolean {
            if (cell !in remaining) return false
            if (holeCells == null) return true
            return inHole(cell, holeCells) == headIsHole
        }

        val pathRev = mutableListOf(hr to hc)
        val used = mutableSetOf(hr to hc)
        var r = hr
        var c = hc
        val freeIn = (0 until 4).filter { d ->
            val cell = (r + DIRS[d].first) to (c + DIRS[d].second)
            allowed(cell) && cell !in used
        }
        if (freeIn.isEmpty()) continue

        val inward = OPPOSITE[exitDir]!!
        var direction = if (inward in freeIn && rng.nextFloat() < 0.75f) {
            inward
        } else {
            freeIn[rng.nextInt(freeIn.size)]
        }

        var segments = 0
        val minSegments = when {
            remaining.size >= 20 -> 3
            remaining.size >= 8 -> 2
            else -> 1
        }
        var maxPath = maxOf(prefer + segmentSize, prefer * 2, 6)
        if (longBias) maxPath = maxOf(maxPath, prefer + segmentSize * 2)
        var safety = remaining.size + 20
        while (safety > 0) {
            safety--
            val maxWant = maxOf(2, minOf(segmentSize, maxOf(2, prefer)))
            val lo = if (longBias) maxOf(2, (maxWant * 2) / 3) else maxOf(1, maxWant / 2)
            val want = if (lo <= maxWant) rng.nextInt(lo, maxWant + 1) else maxWant
            var progressed = false
            for (step in 0 until want) {
                if (pathRev.size >= maxPath) break
                val (dr, dc, _) = DIRS[direction]
                val nr = r + dr
                val nc = c + dc
                if (!allowed(nr to nc) || (nr to nc) in used) break
                pathRev.add(nr to nc)
                used.add(nr to nc)
                r = nr
                c = nc
                progressed = true
            }
            if (progressed) segments++

            var freeNext = (0 until 4).filter { d ->
                d != OPPOSITE[direction] &&
                    allowed((r + DIRS[d].first) to (c + DIRS[d].second)) &&
                    ((r + DIRS[d].first) to (c + DIRS[d].second)) !in used
            }
            val turns = freeNext.filter { it != direction }
            if (turns.isNotEmpty() && (segments < minSegments || rng.nextFloat() < 0.65f)) {
                freeNext = turns
            }
            if (freeNext.isEmpty()) break
            if (segments < minSegments) {
                direction = freeNext[rng.nextInt(freeNext.size)]
                continue
            }
            if (pathRev.size >= maxPath) break
            if (pathRev.size >= prefer && segments >= minSegments) {
                if (rng.nextFloat() < if (longBias) 0.25f else 0.45f) break
            }
            direction = freeNext[rng.nextInt(freeNext.size)]
        }

        val path = pathRev.asReversed()
        if (path.isNotEmpty() && path.last() == (hr to hc)) {
            if (exitHitsOwn(path, exitDir, height, width, shape)) continue
            if (path.size >= 2 || remaining.size <= 3) return path to exitDir
        }
    }

    for ((headCell, exitDir) in heads.take(12)) {
        val unit = listOf(headCell)
        if (exitHitsOwn(unit, exitDir, height, width, shape)) continue
        val (hr, hc) = headCell
        if (canStepOut(hr, hc, exitDir, remaining - setOf(hr to hc), shape, height, width)) {
            return unit to exitDir
        }
    }
    return null
}

private fun fallbackUnit(
    remaining: Set<Pair<Int, Int>>,
    shape: Set<Pair<Int, Int>>,
    height: Int,
    width: Int,
): Pair<List<Pair<Int, Int>>, Int>? {
    for ((r, c) in remaining) {
        for (d in 0 until 4) {
            if (!canStepOut(r, c, d, remaining - setOf(r to c), shape, height, width)) continue
            val (dr, dc, _) = DIRS[d]
            if ((r + dr to c + dc) in remaining) continue
            val unit = listOf(r to c)
            if (exitHitsOwn(unit, d, height, width, shape)) continue
            return unit to d
        }
    }
    return null
}

private fun dirBetween(a: Pair<Int, Int>, b: Pair<Int, Int>): Int? {
    val dr = b.first - a.first
    val dc = b.second - a.second
    for ((d, dir) in DIRS.withIndex()) {
        if (dir.first == dr && dir.second == dc) return d
    }
    return null
}

private fun pickExitDir(
    head: Pair<Int, Int>,
    remainingAfter: Set<Pair<Int, Int>>,
    shape: Set<Pair<Int, Int>>,
    height: Int,
    width: Int,
    preferred: Int? = null,
    path: List<Pair<Int, Int>>? = null,
): Int? {
    val order = ArrayList<Int>()
    if (path != null && path.size >= 2) {
        dirBetween(path[path.size - 2], path.last())?.let { order.add(it) }
    }
    if (preferred != null && preferred !in order) order.add(preferred)
    for (d in 0 until 4) if (d !in order) order.add(d)
    val pathCells = path ?: listOf(head)
    for (d in order) {
        if (!canStepOut(head.first, head.second, d, remainingAfter, shape, height, width)) continue
        if (exitHitsOwn(pathCells, d, height, width, shape)) continue
        val (dr, dc, _) = DIRS[d]
        if ((head.first + dr to head.second + dc) !in remainingAfter) return d
    }
    return null
}

fun coverShapeWithArrows(
    shape: Set<Pair<Int, Int>>,
    width: Int,
    height: Int,
    segmentSize: Int,
    rng: Random,
    targetMoves: Int? = null,
    holeCells: Set<Pair<Int, Int>>? = null,
): Pair<List<Arrow>, List<Int>> {
    val remaining = shape.toMutableSet()
    val solution = ArrayList<Arrow>()
    var nextId = 0
    var safety = shape.size * 30 + 50
    val midMoves = targetMoves ?: maxOf(4, shape.size / 4)
    val holes = holeCells ?: emptySet()

    while (remaining.isNotEmpty() && safety > 0) {
        safety--
        var path: List<Pair<Int, Int>>? = null
        var exitDir: Int? = null
        val arrowsLeftGuess = maxOf(1, midMoves - solution.size)
        val denom = maxOf(1.0, arrowsLeftGuess * 0.85)
        var preferLen = maxOf(4, kotlin.math.round(remaining.size / denom).toInt())
        preferLen = maxOf(4, minOf(preferLen, segmentSize * 4))
        if (remaining.size >= maxOf(12, midMoves)) {
            preferLen = maxOf(preferLen, minOf(segmentSize + 2, remaining.size / 2))
        }

        val placed = tryPlaceArrowFromBoundary(
            remaining, shape, height, width, segmentSize, rng, preferLen,
            holeCells = if (holes.isNotEmpty()) holes else null,
        )
        if (placed != null) {
            path = placed.first
            exitDir = placed.second
        }

        if (path == null) {
            val starts = remaining.toList().shuffled(rng)
            for (start in starts.take(12)) {
                val region = if (holes.isNotEmpty()) {
                    remaining.filter { inHole(it, holes) == inHole(start, holes) }.toSet()
                } else remaining
                val cand = growWindingPath(start, region, shape, height, width, segmentSize, rng, true)
                if (cand == null || !cand.all { it in remaining }) continue
                val head = cand.last()
                val pref = if (cand.size >= 2) dirBetween(cand[cand.size - 2], cand.last()) else null
                val ed = pickExitDir(head, remaining - cand.toSet(), shape, height, width, pref, cand)
                    ?: continue
                if (!canStepOut(head.first, head.second, ed, remaining - cand.toSet(), shape, height, width)) continue
                if (exitHitsOwn(cand, ed, height, width, shape)) continue
                path = cand
                exitDir = ed
                break
            }
        }

        if (path == null) {
            val unit = fallbackUnit(remaining, shape, height, width)
            if (unit != null) {
                path = unit.first
                exitDir = unit.second
            }
        }
        if (path == null) break

        val clean = ArrayList<Pair<Int, Int>>()
        val seen = HashSet<Pair<Int, Int>>()
        for (cell in path) {
            if (cell in remaining && cell !in seen) {
                clean.add(cell)
                seen.add(cell)
            }
        }
        if (clean.isEmpty()) {
            val unit = fallbackUnit(remaining, shape, height, width) ?: break
            clean.clear()
            clean.addAll(unit.first)
            exitDir = unit.second
        }

        var ed = pickExitDir(
            clean.last(), remaining - clean.toSet(), shape, height, width, exitDir, clean,
        )
        if (ed == null ||
            !canStepOut(clean.last().first, clean.last().second, ed, remaining - clean.toSet(), shape, height, width) ||
            exitHitsOwn(clean, ed, height, width, shape)
        ) {
            val unit = fallbackUnit(remaining, shape, height, width) ?: break
            clean.clear()
            clean.addAll(unit.first)
            ed = unit.second
            if (ed == null ||
                !canStepOut(clean.last().first, clean.last().second, ed, remaining - clean.toSet(), shape, height, width) ||
                exitHitsOwn(clean, ed, height, width, shape)
            ) break
        }

        val isHole = holes.isNotEmpty() && clean.isNotEmpty() && clean.all { inHole(it, holes) }
        val arrow = Arrow(nextId, clean.toMutableList(), ed, isHole = isHole)
        nextId++
        for (cell in clean) remaining.remove(cell)
        solution.add(arrow)
    }

    val placedList = solution.asReversed().mapIndexed { i, a ->
        a.copy(id = i, cells = a.cells.toMutableList())
    }
    val solutionIds = placedList.asReversed().map { it.id }
    return placedList to solutionIds
}

fun verifySolvable(puzzle: Puzzle): Boolean {
    val p = puzzle.copyForPlay()
    val byId = p.arrows.associateBy { it.id }
    val order = if (puzzle.solutionIds.isNotEmpty()) puzzle.solutionIds else p.arrows.asReversed().map { it.id }
    for (aid in order) {
        val arrow = byId[aid] ?: return false
        if (!p.isRemovable(arrow)) return false
        p.removeArrow(arrow)
    }
    return p.isComplete()
}

private fun tagHoleArrows(arrows: List<Arrow>, holeCells: Set<Pair<Int, Int>>) {
    if (holeCells.isEmpty()) return
    for (a in arrows) {
        a.isHole = a.cells.isNotEmpty() && a.cells.all { it in holeCells }
    }
}

fun generatePuzzle(level: LevelConfig, seed: Int? = null): Puzzle {
    val baseSeed = seed ?: Random.nextInt(0, Int.MAX_VALUE)
    var best: Puzzle? = null
    var bestDist = 1_000_000_000

    for (attempt in 0 until level.maxGenAttempts) {
        val rng = Random(baseSeed + attempt * 9973L)
        val (shapeName, body) = pickShape(
            level.gridW, level.gridH, rng,
            minCells = level.minCells,
            maxCells = level.maxCells * 2,
        )
        if (body.size < 4) continue

        val holes = findInteriorHoles(body, level.gridW, level.gridH)
        val shape = body + holes
        if (shape.size < 4) continue

        val targetMoves = (level.minMoves + level.maxMoves) / 2
        val (arrows, solutionIds) = coverShapeWithArrows(
            shape, level.gridW, level.gridH, level.segmentSize, rng,
            targetMoves = targetMoves,
            holeCells = holes,
        )
        if (arrows.isEmpty()) continue

        val covered = HashSet<Pair<Int, Int>>()
        for (a in arrows) covered.addAll(a.cells)
        if (covered != shape) continue

        tagHoleArrows(arrows, holes)

        if (arrows.any { exitHitsOwn(it.cells, it.exitDir, level.gridH, level.gridW, shape) }) continue

        val puzzle = Puzzle(
            width = level.gridW,
            height = level.gridH,
            shape = shape,
            arrows = arrows.toMutableList(),
            levelId = level.id,
            seed = baseSeed + attempt * 9973,
            shapeName = shapeName,
            solutionIds = solutionIds.toMutableList(),
            holeCells = holes,
        )
        if (!verifySolvable(puzzle)) continue

        val moves = puzzle.moveCount()
        if (moves in level.minMoves..level.maxMoves) return puzzle

        val dist = if (moves < level.minMoves) level.minMoves - moves else moves - level.maxMoves
        if (dist < bestDist) {
            bestDist = dist
            best = puzzle
        }
    }

    if (best != null) return best

    for (fb in 0 until 40) {
        val rng = Random(baseSeed + 100_003L + fb * 17L)
        val (shapeName, body) = pickShape(level.gridW, level.gridH, rng, minCells = 8, maxCells = null)
        val holes = findInteriorHoles(body, level.gridW, level.gridH)
        val shape = body + holes
        val (arrows, solutionIds) = coverShapeWithArrows(
            shape, level.gridW, level.gridH, level.segmentSize, rng,
            targetMoves = (level.minMoves + level.maxMoves) / 2,
            holeCells = holes,
        )
        if (arrows.isEmpty()) continue
        if (arrows.any { exitHitsOwn(it.cells, it.exitDir, level.gridH, level.gridW, shape) }) continue
        tagHoleArrows(arrows, holes)
        val puzzle = Puzzle(
            width = level.gridW,
            height = level.gridH,
            shape = shape,
            arrows = arrows.toMutableList(),
            levelId = level.id,
            seed = baseSeed + 100_003 + fb * 17,
            shapeName = shapeName,
            solutionIds = solutionIds.toMutableList(),
            holeCells = holes,
        )
        if (verifySolvable(puzzle)) return puzzle
    }

    return best ?: error("failed to generate a valid arrow puzzle")
}
