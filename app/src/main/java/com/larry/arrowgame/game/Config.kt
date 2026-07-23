package com.larry.arrowgame.game

import androidx.compose.ui.graphics.Color

object Config {
    const val APP_NAME = "Arrow Game"
    const val VERSION = "0.1.0"

    const val PLAY_AREA_W = 720
    const val PLAY_AREA_H = 540
    const val BOARD_INNER_PAD = 14
    const val BASE_CELL = 48
    const val BASE_LINE = 11.0f
    const val BASE_HEAD_LEN = 14.0f
    const val BASE_HEAD_WING = 15.0f
    const val BASE_UNIT_TAIL = 0.38f
    const val CELL_MIN = 8

    const val CELEBRATION_SECONDS = 3.0f
    const val ERROR_BLINK_MS = 0.55f

    const val POINTS_PER_CELL = 10
    const val BLOCKED_CLICK_PENALTY = 10
    const val SECONDS_PER_ARROW = 5.0f
    const val TIME_BONUS_PER_SECOND = 5

    val LEVEL_TARGET_CELLS = intArrayOf(45, 36, 30, 20, 15)

    val BG = Color(0xFF121620)
    val PANEL = Color(0xFF1C2230)
    val PANEL_BORDER = Color(0xFF37445F)
    val TEXT = Color(0xFFE6EBF5)
    val TEXT_DIM = Color(0xFF8C96AA)
    val ACCENT = Color(0xFF58A6FF)
    val ACCENT_HOVER = Color(0xFF78BEFF)
    val SUCCESS = Color(0xFF50C878)
    val WARN = Color(0xFFFFBE46)
    val DANGER = Color(0xFFFF646E)
    val SHAPE_FILL = Color(0xFF28344E)
    val SHAPE_EDGE = Color(0xFF465A82)
    val HOLE_FILL = Color(0xFF342A48)
    val HOLE_EDGE = Color(0xFF6E5A96)
    val ARROW_BODY = Color(0xFFD2AF5A)
    val ARROW_HOVER = Color(0xFFFFE68C)
    val ARROW_HOLE = Color(0xFFAA82E6)
    val ARROW_HOLE_HOVER = Color(0xFFD2B4FF)
    val ARROW_ERROR = Color(0xFFFF4650)
    val ARROW_FLOW = Color(0xFF46D278)
    val ARROW_FLOW_LINE = Color(0xFF5AEB8C)
    val CELL_EMPTY = Color(0xFF181C28)
    val SHADE = Color(0xFF2D7855)
    val SHADE_GLOW = Color(0xFF46BE78)
    val HOLE_SHADE = Color(0xFF5F4696)
    val HOLE_SHADE_GLOW = Color(0xFFA078DC)

    data class GlyphMetrics(
        val scale: Float,
        val line: Float,
        val headLen: Float,
        val headWing: Float,
        val neckInset: Float,
        val tailOverhang: Float,
        val unitTail: Float,
        val elbowR: Float,
    )

    fun boardInnerSize(): Pair<Int, Int> = PLAY_AREA_W to PLAY_AREA_H

    fun gridForTargetCell(targetCell: Int): Triple<Int, Int, Int> {
        val (iw, ih) = boardInnerSize()
        var t = maxOf(CELL_MIN, targetCell)
        val cell = if (iw % t == 0 && ih % t == 0) {
            t
        } else {
            var c = t
            while (c > CELL_MIN && (iw % c != 0 || ih % c != 0)) c--
            if (iw % c != 0 || ih % c != 0) {
                c = minOf(iw / maxOf(4, iw / t), ih / maxOf(4, ih / t))
                maxOf(CELL_MIN, c)
            } else c
        }
        return Triple(iw / cell, ih / cell, cell)
    }

    fun glyphMetrics(cellSize: Int): GlyphMetrics {
        val scale = maxOf(0.15f, cellSize / BASE_CELL.toFloat())
        val line = maxOf(2.5f, BASE_LINE * scale)
        var headLen = minOf(BASE_HEAD_LEN * scale, cellSize * 0.32f)
        var headWing = minOf(BASE_HEAD_WING * scale, cellSize * 0.36f)
        headWing = maxOf(headWing, headLen * 0.95f)
        val neckInset = minOf(cellSize * 0.06f, headLen * 0.2f)
        return GlyphMetrics(
            scale = scale,
            line = line,
            headLen = headLen,
            headWing = headWing,
            neckInset = neckInset,
            tailOverhang = minOf(cellSize * 0.12f, line * 1.1f),
            unitTail = minOf(cellSize * BASE_UNIT_TAIL, cellSize * 0.42f),
            elbowR = maxOf(1.5f, line * 0.5f),
        )
    }
}
