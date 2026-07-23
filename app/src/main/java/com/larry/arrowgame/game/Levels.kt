package com.larry.arrowgame.game

import androidx.compose.ui.graphics.Color

data class LevelConfig(
    val id: Int,
    val name: String,
    val description: String,
    val gridW: Int,
    val gridH: Int,
    val cellSize: Int,
    val minCells: Int,
    val maxCells: Int,
    val minMoves: Int,
    val maxMoves: Int,
    val segmentSize: Int,
    val maxGenAttempts: Int,
    val color: Color,
) {
    val minArrow: Int get() = 1
    val maxArrow: Int get() = segmentSize
}

private fun buildLevel(
    id: Int,
    name: String,
    description: String,
    targetCell: Int,
    minMoves: Int,
    maxMoves: Int,
    segmentSize: Int,
    maxGenAttempts: Int,
    color: Color,
): LevelConfig {
    val (gw, gh, cs) = Config.gridForTargetCell(targetCell)
    val total = gw * gh
    val minCells = maxOf(8, total / 10)
    val maxCells = maxOf(minCells + 8, (total * 3) / 4)
    return LevelConfig(
        id = id,
        name = name,
        description = description,
        gridW = gw,
        gridH = gh,
        cellSize = cs,
        minCells = minCells,
        maxCells = maxCells,
        minMoves = minMoves,
        maxMoves = maxMoves,
        segmentSize = segmentSize,
        maxGenAttempts = maxGenAttempts,
        color = color,
    )
}

val LEVELS: List<LevelConfig> = run {
    val t = Config.LEVEL_TARGET_CELLS
    listOf(
        buildLevel(1, "Beginner", "Few arrows, large cells — learn the flow.", t[0], 3, 7, 10, 60, Color(0xFF50C878)),
        buildLevel(2, "Easy", "A bit denser — more arrows to clear.", t[1], 6, 12, 12, 70, Color(0xFF64BEFF)),
        buildLevel(3, "Medium", "Smaller cells, longer chains of arrows.", t[2], 10, 18, 14, 80, Color(0xFFFFBE46)),
        buildLevel(4, "Hard", "Dense board — plan long blocking chains.", t[3], 16, 28, 16, 100, Color(0xFFFF8C5A)),
        buildLevel(5, "Expert", "Finest grid — many arrows, long paths.", t[4], 22, 40, 18, 100, Color(0xFFFF6478)),
    )
}

fun getLevel(levelId: Int): LevelConfig =
    LEVELS.firstOrNull { it.id == levelId }
        ?: error("Unknown level id: $levelId")
