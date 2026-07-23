package com.larry.arrowgame.game

import kotlin.random.Random

data class ShapeTemplate(val name: String, val rows: List<String>)

val TEMPLATES: List<ShapeTemplate> = listOf(
    ShapeTemplate("Square", listOf("######", "######", "######", "######", "######", "######")),
    ShapeTemplate("Rectangle", listOf("########", "########", "########", "########")),
    ShapeTemplate("Circle", listOf("  ####  ", " ###### ", "########", "########", " ###### ", "  ####  ")),
    ShapeTemplate("Triangle", listOf("   ##   ", "  ####  ", " ###### ", "########", "########")),
    ShapeTemplate("Diamond", listOf("   ##   ", "  ####  ", " ###### ", "########", " ###### ", "  ####  ", "   ##   ")),
    ShapeTemplate("Cross", listOf("  ####  ", "  ####  ", "########", "########", "  ####  ", "  ####  ")),
    ShapeTemplate("Plus", listOf("  ##  ", "  ##  ", "######", "######", "  ##  ", "  ##  ")),
    ShapeTemplate("Heart", listOf(" ##  ## ", "########", "########", " ###### ", "  ####  ", "   ##   ")),
    ShapeTemplate("Star", listOf("   ##   ", "  ####  ", "########", " ###### ", " ##  ## ", " ##  ## ")),
    ShapeTemplate("Apple", listOf("   #    ", "  ###   ", " ###### ", "########", "########", " ###### ", "  ####  ")),
    ShapeTemplate("Tree", listOf("   ##   ", "  ####  ", " ###### ", "########", "  ####  ", "   ##   ", "   ##   ")),
    ShapeTemplate("House", listOf("   ##   ", "  ####  ", " ###### ", "########", "## ## ##", "########")),
    ShapeTemplate("Mushroom", listOf("  ####  ", " ###### ", "########", "  ####  ", "  ####  ", "  ####  ")),
    ShapeTemplate("Fish", listOf("  ##### ", " #######", "########", " #######", "  ##### ", "     #  ")),
    ShapeTemplate("Arrow (shape)", listOf("   ##   ", "  ####  ", " ###### ", "########", "  ####  ", "  ####  ")),
    ShapeTemplate("Crescent", listOf("  ####  ", " #####  ", "####    ", "####    ", " #####  ", "  ####  ")),
    ShapeTemplate("Ring", listOf("  ####  ", " ##  ## ", "##    ##", "##    ##", " ##  ## ", "  ####  ")),
    ShapeTemplate("Pyramid", listOf("    ##    ", "   ####   ", "  ######  ", " ######## ", "##########")),
    ShapeTemplate("L-Block", listOf("####  ", "####  ", "####  ", "######", "######")),
    ShapeTemplate("T-Block", listOf("######", "######", "  ##  ", "  ##  ", "  ##  ")),
    ShapeTemplate("U-Shape", listOf("##  ##", "##  ##", "##  ##", "######", "######")),
    ShapeTemplate("Boat", listOf("   ##   ", "  ####  ", " ###### ", "########", " ###### ")),
    ShapeTemplate("Cat face", listOf("#    #", "##  ##", "######", "# ## #", "######", " #### ")),
    ShapeTemplate("Rocket", listOf("  ##  ", " #### ", " #### ", "######", " #### ", "##  ##")),
)

private fun scaleTemplate(rows: List<String>, scale: Int): List<String> {
    if (scale <= 1) return rows
    val out = ArrayList<String>(rows.size * scale)
    for (row in rows) {
        val expanded = row.map { ch -> ch.toString().repeat(scale) }.joinToString("")
        repeat(scale) { out.add(expanded) }
    }
    return out
}

private fun fitScale(tw: Int, th: Int, gridW: Int, gridH: Int): Int {
    if (tw <= 0 || th <= 0) return 1
    val pad = if (minOf(gridW, gridH) >= 8) 1 else 0
    val availW = maxOf(1, gridW - 2 * pad)
    val availH = maxOf(1, gridH - 2 * pad)
    return maxOf(1, minOf(availW / tw, availH / th))
}

fun stampTemplate(
    template: ShapeTemplate,
    gridW: Int,
    gridH: Int,
    scale: Int? = null,
): Set<Pair<Int, Int>> {
    val raw = template.rows
    val th = raw.size
    val tw = raw.maxOfOrNull { it.length } ?: 0
    val norm = raw.map { it.padEnd(tw) }
    var sc = scale ?: fitScale(tw, th, gridW, gridH)
    var scaled = scaleTemplate(norm, sc)
    var sh = scaled.size
    var sw = scaled.maxOfOrNull { it.length } ?: 0
    while ((sw > gridW || sh > gridH) && sc > 1) {
        sc--
        scaled = scaleTemplate(norm, sc)
        sh = scaled.size
        sw = scaled.maxOfOrNull { it.length } ?: 0
    }
    val offR = maxOf(0, (gridH - sh) / 2)
    val offC = maxOf(0, (gridW - sw) / 2)
    val cells = HashSet<Pair<Int, Int>>()
    for ((r, row) in scaled.withIndex()) {
        for ((c, ch) in row.withIndex()) {
            if (ch == '#') {
                val gr = offR + r
                val gc = offC + c
                if (gr in 0 until gridH && gc in 0 until gridW) {
                    cells.add(gr to gc)
                }
            }
        }
    }
    return cells
}

fun findInteriorHoles(
    shape: Set<Pair<Int, Int>>,
    gridW: Int,
    gridH: Int,
): Set<Pair<Int, Int>> {
    val exterior = HashSet<Pair<Int, Int>>()
    val stack = ArrayDeque<Pair<Int, Int>>()
    for (r in 0 until gridH) {
        for (c in intArrayOf(0, gridW - 1)) {
            if ((r to c) !in shape) stack.add(r to c)
        }
    }
    for (c in 0 until gridW) {
        for (r in intArrayOf(0, gridH - 1)) {
            if ((r to c) !in shape) stack.add(r to c)
        }
    }
    while (stack.isNotEmpty()) {
        val (r, c) = stack.removeLast()
        if ((r to c) in exterior) continue
        if (r !in 0 until gridH || c !in 0 until gridW) continue
        if ((r to c) in shape) continue
        exterior.add(r to c)
        stack.add((r - 1) to c)
        stack.add((r + 1) to c)
        stack.add(r to (c - 1))
        stack.add(r to (c + 1))
    }
    val holes = HashSet<Pair<Int, Int>>()
    for (r in 0 until gridH) {
        for (c in 0 until gridW) {
            val cell = r to c
            if (cell !in shape && cell !in exterior) holes.add(cell)
        }
    }
    return holes
}

fun pickShape(
    gridW: Int,
    gridH: Int,
    rng: Random,
    minCells: Int = 8,
    maxCells: Int? = null,
): Pair<String, Set<Pair<Int, Int>>> {
    val candidates = ArrayList<Pair<String, Set<Pair<Int, Int>>>>()
    val small = ArrayList<Pair<String, Set<Pair<Int, Int>>>>()
    val order = TEMPLATES.shuffled(rng)
    for (tmpl in order) {
        val cells = stampTemplate(tmpl, gridW, gridH)
        val n = cells.size
        if (n < 4) continue
        if (n >= maxOf(6, minCells / 3)) {
            candidates.add(tmpl.name to cells)
        } else {
            small.add(tmpl.name to cells)
        }
    }
    val pool = if (candidates.isNotEmpty()) candidates else small
    if (pool.isEmpty()) {
        val cells = stampTemplate(TEMPLATES[2], gridW, gridH)
        return "Circle" to cells
    }
    return pool[rng.nextInt(pool.size)]
}
