package com.larry.arrowgame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import com.larry.arrowgame.game.Arrow
import com.larry.arrowgame.game.Config
import com.larry.arrowgame.game.DIRS
import com.larry.arrowgame.game.FlowAnim
import com.larry.arrowgame.game.Puzzle
import com.larry.arrowgame.game.headDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class BoardLayout(
    val originX: Float,
    val originY: Float,
    val cellSize: Float,
    val boardLeft: Float,
    val boardTop: Float,
    val boardW: Float,
    val boardH: Float,
    /** Grid column of the leftmost visible cell (shape crop). */
    val col0: Int,
    /** Grid row of the topmost visible cell (shape crop). */
    val row0: Int,
    val viewCols: Int,
    val viewRows: Int,
) {
    fun cellToScreen(r: Int, c: Int): Offset {
        return Offset(
            originX + (c - col0) * cellSize + cellSize / 2f,
            originY + (r - row0) * cellSize + cellSize / 2f,
        )
    }

    fun cellTopLeft(r: Int, c: Int): Offset {
        return Offset(
            originX + (c - col0) * cellSize,
            originY + (r - row0) * cellSize,
        )
    }
}

/**
 * Fit the shape's bounding box into the canvas with minimal chrome so cells
 * are as large as the phone screen allows (especially Expert).
 */
fun computeBoardLayout(
    canvasW: Float,
    canvasH: Float,
    puzzle: Puzzle,
    outerMarginPx: Float,
    frameChromePx: Float,
): BoardLayout {
    val margin = outerMarginPx.coerceAtLeast(0f)
    val chrome = frameChromePx.coerceAtLeast(1f)

    // Crop to shape bounds (+1 cell slack so heads near the edge have room).
    var minR = puzzle.height
    var maxR = -1
    var minC = puzzle.width
    var maxC = -1
    for ((r, c) in puzzle.shape) {
        if (r < minR) minR = r
        if (r > maxR) maxR = r
        if (c < minC) minC = c
        if (c > maxC) maxC = c
    }
    if (maxR < minR || maxC < minC) {
        minR = 0
        maxR = puzzle.height - 1
        minC = 0
        maxC = puzzle.width - 1
    }
    // 1-cell pad around the shape so exit animation / heads aren't clipped hard
    val padCells = 1
    val row0 = max(0, minR - padCells)
    val col0 = max(0, minC - padCells)
    val row1 = min(puzzle.height - 1, maxR + padCells)
    val col1 = min(puzzle.width - 1, maxC + padCells)
    val viewRows = max(1, row1 - row0 + 1)
    val viewCols = max(1, col1 - col0 + 1)

    val availableW = (canvasW - margin * 2f - chrome * 2f).coerceAtLeast(1f)
    val availableH = (canvasH - margin * 2f - chrome * 2f).coerceAtLeast(1f)
    val cell = max(
        Config.CELL_MIN.toFloat(),
        min(availableW / viewCols, availableH / viewRows),
    )
    val gw = viewCols * cell
    val gh = viewRows * cell
    val frameW = gw + chrome * 2f
    val frameH = gh + chrome * 2f
    val boardLeft = (canvasW - frameW) / 2f
    val boardTop = (canvasH - frameH) / 2f
    val ox = boardLeft + chrome
    val oy = boardTop + chrome
    return BoardLayout(
        originX = ox,
        originY = oy,
        cellSize = cell,
        boardLeft = boardLeft,
        boardTop = boardTop,
        boardW = frameW,
        boardH = frameH,
        col0 = col0,
        row0 = row0,
        viewCols = viewCols,
        viewRows = viewRows,
    )
}

private fun cellAt(
    x: Float,
    y: Float,
    layout: BoardLayout,
    puzzle: Puzzle,
): Pair<Int, Int>? {
    if (layout.cellSize <= 0f) return null
    val localC = ((x - layout.originX) / layout.cellSize).toInt()
    val localR = ((y - layout.originY) / layout.cellSize).toInt()
    if (localC !in 0 until layout.viewCols || localR !in 0 until layout.viewRows) return null
    val c = layout.col0 + localC
    val r = layout.row0 + localR
    if (r !in 0 until puzzle.height || c !in 0 until puzzle.width) return null
    if ((r to c) !in puzzle.shape) return null
    return r to c
}

/** Min / max zoom for fat-finger assist. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4.5f

/**
 * View transform: scale around canvas pivot, then pan.
 * screen = (world - pivot) * scale + pivot + pan
 */
private fun screenToWorld(
    screen: Offset,
    pan: Offset,
    scale: Float,
    pivot: Offset,
): Offset {
    val s = scale.coerceAtLeast(0.001f)
    return Offset(
        (screen.x - pan.x - pivot.x) / s + pivot.x,
        (screen.y - pan.y - pivot.y) / s + pivot.y,
    )
}

private fun clampPan(
    pan: Offset,
    scale: Float,
    canvasW: Float,
    canvasH: Float,
    layout: BoardLayout,
    pivot: Offset,
): Offset {
    if (scale <= 1.02f || layout.boardW <= 0f) return Offset.Zero
    // Keep at least this much of the board frame overlapping the viewport.
    val margin = min(canvasW, canvasH) * 0.12f
    val bl = layout.boardLeft
    val bt = layout.boardTop
    val br = bl + layout.boardW
    val bb = bt + layout.boardH
    // screenX = (worldX - pivot.x) * scale + pivot.x + pan.x
    val minPanX = margin - (br - pivot.x) * scale - pivot.x
    val maxPanX = canvasW - margin - (bl - pivot.x) * scale - pivot.x
    val minPanY = margin - (bb - pivot.y) * scale - pivot.y
    val maxPanY = canvasH - margin - (bt - pivot.y) * scale - pivot.y
    val loX = min(minPanX, maxPanX)
    val hiX = max(minPanX, maxPanX)
    val loY = min(minPanY, maxPanY)
    val hiY = max(minPanY, maxPanY)
    return Offset(pan.x.coerceIn(loX, hiX), pan.y.coerceIn(loY, hiY))
}

@Composable
fun BoardCanvas(
    modifier: Modifier = Modifier,
    puzzle: Puzzle,
    hoverArrowId: Int?,
    errorArrowId: Int?,
    errorBlinkOn: Boolean,
    flowAnims: List<FlowAnim>,
    frameTick: Int,
    onLayout: (BoardLayout) -> Unit = {},
    onCellSelect: (Int, Int) -> Unit,
    onHoverCell: (Int?, Int?) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    // Essentially fill the canvas: 1dp margin, 2dp frame chrome.
    val outerMarginPx = with(density) { 1.dp.toPx() }
    val frameChromePx = with(density) { 2.dp.toPx() }
    val panSlopPx = with(density) { 18.dp.toPx() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(
        canvasSize,
        puzzle.width,
        puzzle.height,
        puzzle.shape,
        outerMarginPx,
        frameChromePx,
    ) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            BoardLayout(0f, 0f, Config.BASE_CELL.toFloat(), 0f, 0f, 0f, 0f, 0, 0, 1, 1)
        } else {
            computeBoardLayout(
                canvasSize.width.toFloat(),
                canvasSize.height.toFloat(),
                puzzle,
                outerMarginPx,
                frameChromePx,
            )
        }
    }

    // Pinch-zoom + pan (board-local; resets on new puzzle).
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(puzzle.seed, puzzle.shapeName, puzzle.width, puzzle.height) {
        scale = 1f
        pan = Offset.Zero
        lastTapMs = 0L
    }

    val latestLayout by rememberUpdatedState(layout)
    val latestPuzzle by rememberUpdatedState(puzzle)
    val latestOnSelect by rememberUpdatedState(onCellSelect)
    val latestOnHover by rememberUpdatedState(onHoverCell)
    val latestOnLayout by rememberUpdatedState(onLayout)
    val latestScale by rememberUpdatedState(scale)
    val latestPan by rememberUpdatedState(pan)
    val latestCanvas by rememberUpdatedState(canvasSize)

    LaunchedEffect(layout.cellSize, layout.viewCols, layout.viewRows, layout.boardW) {
        if (layout.boardW > 0f) latestOnLayout(layout)
    }

    fun pivotOf(size: IntSize): Offset =
        Offset(size.width / 2f, size.height / 2f)

    fun toWorld(screen: Offset): Offset {
        val sz = latestCanvas
        if (sz.width <= 0) return screen
        return screenToWorld(screen, latestPan, latestScale, pivotOf(sz))
    }

    fun applyZoom(zoomChange: Float, centroid: Offset) {
        val sz = latestCanvas
        if (sz.width <= 0) return
        val pivot = pivotOf(sz)
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newScale == oldScale) return
        // Keep the world point under the centroid fixed while scaling.
        val worldUnder = screenToWorld(centroid, pan, oldScale, pivot)
        // newPan such that worldUnder stays under centroid:
        // centroid = (worldUnder - pivot) * newScale + pivot + newPan
        val newPan = Offset(
            centroid.x - (worldUnder.x - pivot.x) * newScale - pivot.x,
            centroid.y - (worldUnder.y - pivot.y) * newScale - pivot.y,
        )
        scale = newScale
        pan = if (newScale <= 1.02f) {
            Offset.Zero
        } else {
            clampPan(newPan, newScale, sz.width.toFloat(), sz.height.toFloat(), latestLayout, pivot)
        }
    }

    fun applyPan(delta: Offset) {
        val sz = latestCanvas
        if (sz.width <= 0 || scale <= 1.02f) return
        val pivot = pivotOf(sz)
        pan = clampPan(
            pan + delta,
            scale,
            sz.width.toFloat(),
            sz.height.toFloat(),
            latestLayout,
            pivot,
        )
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(puzzle.width, puzzle.height, puzzle.shape.size, puzzle.seed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    var multiTouch = false
                    var panning = false
                    var pastSlop = false
                    var dragDistance = 0f
                    var lastWorldPos = toWorld(down.position)
                    val downScreen = down.position

                    fun reportHover(screen: Offset) {
                        val w = toWorld(screen)
                        val cell = cellAt(w.x, w.y, latestLayout, latestPuzzle)
                        if (cell == null) latestOnHover(null, null)
                        else latestOnHover(cell.first, cell.second)
                    }

                    // Start as potential select
                    reportHover(down.position)

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            multiTouch = true
                            panning = true // no select after pinch
                            latestOnHover(null, null)
                            val zoomChange = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid != Offset.Unspecified) {
                                if (zoomChange != 1f) {
                                    applyZoom(zoomChange, centroid)
                                }
                                // Two-finger pan via centroid movement
                                val panChange = event.calculatePan()
                                if (panChange != Offset.Zero) {
                                    applyPan(panChange)
                                }
                            }
                            event.changes.forEach {
                                if (it.positionChanged()) it.consume()
                            }
                        } else if (pressed.size == 1) {
                            val change = pressed[0]
                            val screenPos = change.position
                            if (multiTouch) {
                                // Residual single finger after pinch — pan only if still zoomed
                                val panChange = change.position - change.previousPosition
                                if (latestScale > 1.02f) applyPan(panChange)
                                change.consume()
                            } else {
                                val delta = (screenPos - down.position).getDistance()
                                dragDistance = max(dragDistance, delta)
                                if (!pastSlop && dragDistance > panSlopPx) {
                                    pastSlop = true
                                    // When zoomed, large drag becomes pan; otherwise stay on select.
                                    if (latestScale > 1.02f) {
                                        panning = true
                                        latestOnHover(null, null)
                                    }
                                }
                                if (panning && latestScale > 1.02f) {
                                    val panChange = change.position - change.previousPosition
                                    applyPan(panChange)
                                } else {
                                    // Select / highlight in board space
                                    reportHover(screenPos)
                                    lastWorldPos = toWorld(screenPos)
                                }
                                change.consume()
                            }
                        } else {
                            // All pointers up
                            val upChange = event.changes.firstOrNull()
                            val upScreen = upChange?.position ?: downScreen
                            val world = toWorld(upScreen)
                            lastWorldPos = world

                            if (!multiTouch && !panning) {
                                // Double-tap (quick, little movement) resets zoom
                                val now = SystemClock.elapsedRealtime()
                                val isQuick = dragDistance < panSlopPx
                                val dt = now - lastTapMs
                                val distFromLast = (upScreen - lastTapPos).getDistance()
                                if (isQuick && dt in 40L..350L && distFromLast < panSlopPx * 1.5f) {
                                    scale = 1f
                                    pan = Offset.Zero
                                    lastTapMs = 0L
                                    latestOnHover(null, null)
                                    break
                                }
                                if (isQuick) {
                                    lastTapMs = now
                                    lastTapPos = upScreen
                                } else {
                                    lastTapMs = 0L
                                }

                                val pick = cellAt(world.x, world.y, latestLayout, latestPuzzle)
                                if (pick != null) {
                                    latestOnSelect(pick.first, pick.second)
                                }
                            } else {
                                lastTapMs = 0L
                            }
                            latestOnHover(null, null)
                            break
                        }

                        // Exit if nothing pressed
                        if (pressed.isEmpty()) {
                            latestOnHover(null, null)
                            break
                        }
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        if (layout.boardW <= 0f || layout.cellSize <= 0f) return@Canvas

        val pivot = Offset(size.width / 2f, size.height / 2f)

        // Clip to canvas; zoom may push content outside.
        withTransform({
            // Match screenToWorld / applyZoom math:
            // screen = (world - pivot) * scale + pivot + pan
            translate(left = pan.x, top = pan.y)
            scale(scaleX = scale, scaleY = scale, pivot = pivot)
        }) {
            drawRoundRect(
                color = Config.CELL_EMPTY,
                topLeft = Offset(layout.boardLeft, layout.boardTop),
                size = Size(layout.boardW, layout.boardH),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = Config.PANEL_BORDER,
                topLeft = Offset(layout.boardLeft, layout.boardTop),
                size = Size(layout.boardW, layout.boardH),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 2f),
            )

            val cs = layout.cellSize
            val rad = max(1f, min(4f, cs / 6f))
            val hole = puzzle.holeCells

            for ((r, c) in puzzle.shape) {
                val topLeft = layout.cellTopLeft(r, c)
                val fill = when {
                    (r to c) in puzzle.shaded -> if ((r to c) in hole) Config.HOLE_SHADE else Config.SHADE
                    (r to c) in hole -> Config.HOLE_FILL
                    else -> Config.SHAPE_FILL
                }
                drawRoundRect(
                    color = fill,
                    topLeft = topLeft,
                    size = Size(max(1f, cs - 1f), max(1f, cs - 1f)),
                    cornerRadius = CornerRadius(rad, rad),
                )
                if (cs >= 8f && (r to c) !in puzzle.shaded) {
                    val edge = if ((r to c) in hole) Config.HOLE_EDGE else Config.SHAPE_EDGE
                    drawRoundRect(
                        color = edge,
                        topLeft = topLeft,
                        size = Size(max(1f, cs - 1f), max(1f, cs - 1f)),
                        cornerRadius = CornerRadius(rad, rad),
                        style = Stroke(width = 1f),
                    )
                }
            }

            for (arrow in puzzle.arrows) {
                if (arrow.removed) continue
                val selected = hoverArrowId == arrow.id
                val color = arrowColor(arrow, hoverArrowId, errorArrowId, errorBlinkOn)
                val centers = arrow.cells.map { (r, c) ->
                    val o = layout.cellToScreen(r, c)
                    o.x to o.y
                }
                val headDir = headDirection(arrow.cells, arrow.exitDir)
                // Halo + thicker stroke so select reads clearly on dense Expert grids
                if (selected) {
                    drawPolylineArrow(
                        centers, headDir, Config.ARROW_SELECT_GLOW.copy(alpha = 0.55f), cs,
                        thicknessScale = 1.85f,
                    )
                }
                drawPolylineArrow(
                    centers, headDir, color, cs,
                    thicknessScale = if (selected) 1.35f else 1f,
                )
            }

            for (anim in flowAnims) {
                if (anim.done()) continue
                // Map grid-space polyline into cropped board space (may leave the frame).
                val centers = anim.polylinePoints(0f, 0f).map { (sx, sy) ->
                    val gridC = (sx - anim.cellSize / 2f) / anim.cellSize
                    val gridR = (sy - anim.cellSize / 2f) / anim.cellSize
                    val x = layout.originX + (gridC - layout.col0) * layout.cellSize + layout.cellSize / 2f
                    val y = layout.originY + (gridR - layout.row0) * layout.cellSize + layout.cellSize / 2f
                    x to y
                }
                drawPolylineArrow(centers, anim.exitDirection, Config.ARROW_FLOW_LINE, cs)
                val n = anim.cells.size
                if (n > 0) {
                    val idx = min(n - 1, max(0, anim.shadedCount()))
                    val (r, c) = anim.cells[idx]
                    val isHole = (r to c) in puzzle.holeCells
                    val glow = if (isHole) Config.HOLE_SHADE_GLOW else Config.SHADE_GLOW
                    val tl = layout.cellTopLeft(r, c)
                    drawRect(
                        color = glow.copy(alpha = 0.35f),
                        topLeft = tl,
                        size = Size(cs - 1f, cs - 1f),
                    )
                }
            }
        }
    }
}

private fun arrowColor(
    arrow: Arrow,
    hoverId: Int?,
    errorId: Int?,
    errorBlinkOn: Boolean,
): Color {
    val base = if (arrow.isHole) Config.ARROW_HOLE else Config.ARROW_BODY
    val hover = if (arrow.isHole) Config.ARROW_HOLE_HOVER else Config.ARROW_HOVER
    if (errorId == arrow.id && errorBlinkOn) return Config.ARROW_ERROR
    if (hoverId == arrow.id) return hover
    return base
}

/**
 * Draw shaft + elbows + head.
 *
 * The triangle head is never a combined tip-elbow: multi-cell arrows always
 * continue the final shaft segment into the head. Any turn must be a normal
 * elbow on an earlier cell.
 */
private fun DrawScope.drawPolylineArrow(
    centers: List<Pair<Float, Float>>,
    exitDirection: Int,
    color: Color,
    cs: Float,
    thicknessScale: Float = 1f,
) {
    if (centers.isEmpty()) return
    var m = Config.glyphMetrics(cs.roundToInt().coerceAtLeast(1))
    val thickness = max(2f, m.line * thicknessScale)
    val maxReach = cs * 0.46f
    val headLen = min(m.headLen, maxReach * 0.7f)
    val headWing = minOf(m.headWing, cs * 0.38f, headLen * 1.15f)
    m = m.copy(headLen = headLen, headWing = headWing)

    fun unitFromTo(p0: Pair<Float, Float>, p1: Pair<Float, Float>): Pair<Float, Float> {
        val dx = p1.first - p0.first
        val dy = p1.second - p0.second
        val L = hypot(dx, dy).takeIf { it > 0f } ?: 1f
        return (dx / L) to (dy / L)
    }

    fun isTurn(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Boolean {
        val u = unitFromTo(a, b)
        val v = unitFromTo(b, c)
        return kotlin.math.abs(u.first - v.first) > 0.01f || kotlin.math.abs(u.second - v.second) > 0.01f
    }

    // Head always continues the final segment (never turns at the tip).
    val headDir: Int
    val headAng: Float
    if (centers.size == 1) {
        headDir = exitDirection
        val (dr, dc, _) = DIRS[headDir]
        headAng = atan2(dr.toFloat(), dc.toFloat())
    } else {
        val (lastUx, lastUy) = unitFromTo(centers[centers.size - 2], centers.last())
        headAng = atan2(lastUy, lastUx)
        // Snap to nearest cardinal for the polygon head
        headDir = when {
            kotlin.math.abs(lastUx) >= kotlin.math.abs(lastUy) && lastUx >= 0f -> 1 // E
            kotlin.math.abs(lastUx) >= kotlin.math.abs(lastUy) -> 3 // W
            lastUy >= 0f -> 2 // S
            else -> 0 // N
        }
    }

    if (centers.size == 1) {
        val (hx, hy) = centers[0]
        val back = (hx - cos(headAng) * m.unitTail) to (hy - sin(headAng) * m.unitTail)
        val neck = (hx + cos(headAng) * m.neckInset) to (hy + sin(headAng) * m.neckInset)
        drawShaft(back, neck, color, thickness)
        drawCircle(color, thickness / 2f, Offset(back.first, back.second))
        drawArrowHead(neck, headDir, color, m)
        return
    }

    val (ux, uy) = unitFromTo(centers[0], centers[1])
    val tail = (centers[0].first - ux * m.tailOverhang) to (centers[0].second - uy * m.tailOverhang)
    // Extend tip slightly past the head center along the final segment only
    val neck = (centers.last().first + cos(headAng) * m.neckInset) to
        (centers.last().second + sin(headAng) * m.neckInset)

    val pts = ArrayList<Pair<Float, Float>>(centers.size + 2)
    pts.add(tail)
    pts.addAll(centers)
    pts[pts.lastIndex] = neck // replace head center with neck (straight, no tip turn)

    var runStart = 0
    for (i in 1 until pts.size - 1) {
        if (isTurn(pts[i - 1], pts[i], pts[i + 1])) {
            drawShaft(pts[runStart], pts[i], color, thickness)
            drawCircle(color, m.elbowR, Offset(pts[i].first, pts[i].second))
            runStart = i
        }
    }
    drawShaft(pts[runStart], pts.last(), color, thickness)
    drawCircle(color, thickness / 2f, Offset(tail.first, tail.second))
    drawArrowHead(pts.last(), headDir, color, m)
}

private fun DrawScope.drawShaft(a: Pair<Float, Float>, b: Pair<Float, Float>, color: Color, thickness: Float) {
    if (a == b) return
    drawLine(
        color = color,
        start = Offset(a.first, a.second),
        end = Offset(b.first, b.second),
        strokeWidth = thickness,
    )
}

private fun DrawScope.drawArrowHead(
    base: Pair<Float, Float>,
    direction: Int,
    color: Color,
    m: Config.GlyphMetrics,
) {
    val (dr, dc, _) = DIRS[direction]
    val ang = atan2(dr.toFloat(), dc.toFloat())
    val tip = Offset(
        base.first + cos(ang) * m.headLen,
        base.second + sin(ang) * m.headLen,
    )
    val left = Offset(
        base.first + cos(ang + 2.2f) * m.headWing,
        base.second + sin(ang + 2.2f) * m.headWing,
    )
    val right = Offset(
        base.first + cos(ang - 2.2f) * m.headWing,
        base.second + sin(ang - 2.2f) * m.headWing,
    )
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, color)
}
