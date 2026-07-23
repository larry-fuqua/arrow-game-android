package com.larry.arrowgame.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.larry.arrowgame.game.Config
import com.larry.arrowgame.game.GameViewModel
import com.larry.arrowgame.game.LEVELS
import com.larry.arrowgame.game.Screen
import kotlin.math.max

@Composable
fun ArrowGameApp(vm: GameViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Config.BG)
    ) {
        when (vm.screen) {
            Screen.LOGIN -> LoginScreen(vm)
            Screen.LEVELS -> LevelsScreen(vm)
            Screen.PLAY -> PlayScreen(vm)
            Screen.CELEBRATE -> CelebrateScreen(vm)
            Screen.COMPLETE -> CompleteScreen(vm)
        }

        if (vm.statusActive()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2432))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    vm.statusMsg,
                    color = Config.TEXT,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(Config.APP_NAME, color = Config.ACCENT, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Shade the shape by releasing arrows in the right order.",
            color = Config.TEXT_DIM,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Track scores with a local name or Guest.",
            color = Config.TEXT_DIM,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Text("Display name (local tracking)", color = Config.TEXT_DIM, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.nameInput,
            onValueChange = { if (it.length <= 32) vm.nameInput = it },
            placeholder = { Text("Display name…", color = Config.TEXT_DIM) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.loginLocal() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Config.TEXT,
                unfocusedTextColor = Config.TEXT,
                focusedBorderColor = Config.ACCENT,
                unfocusedBorderColor = Config.PANEL_BORDER,
                cursorColor = Config.ACCENT,
                focusedContainerColor = Config.CELL_EMPTY,
                unfocusedContainerColor = Config.CELL_EMPTY,
            ),
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue with name", Modifier.fillMaxWidth(0.9f)) { vm.loginLocal() }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Play as Guest", Modifier.fillMaxWidth(0.9f)) { vm.loginGuest() }
    }
}

@Composable
private fun LevelsScreen(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(80.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Choose a level", color = Config.TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                val name = vm.profile?.displayName ?: "Player"
                val provider = vm.profile?.authProvider ?: ""
                Text("Playing as $name  ·  $provider", color = Config.TEXT_DIM, fontSize = 12.sp)
            }
            TextButton(onClick = { vm.logout() }) {
                Text("Log out", color = Config.TEXT_DIM)
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (lv in LEVELS) {
                val best = vm.bestForLevel(lv.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Config.PANEL)
                        .border(1.dp, Config.PANEL_BORDER, RoundedCornerShape(12.dp))
                        .clickable { vm.startLevel(lv.id) }
                        .padding(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(8.dp)
                            .height(96.dp)
                            .background(lv.color)
                    )
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(lv.name, color = Config.TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Moves ${lv.minMoves}–${lv.maxMoves}  ·  ${lv.gridW}×${lv.gridH}",
                            color = Config.TEXT_DIM,
                            fontSize = 12.sp,
                        )
                        if (best != null) {
                            Text(
                                "Best: ${best.score} pts  (${"%.1f".format(best.elapsedSec)}s)",
                                color = Config.SUCCESS,
                                fontSize = 12.sp,
                            )
                        } else {
                            Text(lv.description, color = Config.TEXT_DIM, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayScreen(vm: GameViewModel) {
    val puzzle = vm.puzzle
    val level = vm.level
    val tick = vm.frameTick

    Column(Modifier.fillMaxSize()) {
        // Compact HUD — maximize board area
        Row(
            Modifier
                .fillMaxWidth()
                .background(Config.PANEL)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {
                SmallHudButton("←") { vm.backToLevels() }
                Spacer(Modifier.width(4.dp))
                SmallHudButton("New") { vm.regen() }
                Spacer(Modifier.width(4.dp))
                SmallHudButton(if (vm.muted) "Muted" else "Sound") { vm.toggleMute() }
            }
            Column(horizontalAlignment = Alignment.End) {
                val elapsed = vm.elapsed()
                val nArrows = max(1, puzzle?.arrows?.size ?: 1)
                val targetSec = Config.SECONDS_PER_ARROW * nArrows
                val previewBonus = max(0f, targetSec - elapsed).toInt() * Config.TIME_BONUS_PER_SECOND
                val titleBit = buildString {
                    append(level?.name ?: "")
                    if (puzzle?.shapeName?.isNotEmpty() == true) {
                        append(" · ")
                        append(puzzle.shapeName)
                    }
                }
                Text(titleBit, color = level?.color ?: Config.TEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Score ${puzzle?.score ?: 0}  ·  ${"%.0f".format(elapsed)}s/${"%.0f".format(targetSec)}s  ·  +$previewBonus  ·  ${puzzle?.remainingCount() ?: 0} left",
                    color = Config.TEXT_DIM,
                    fontSize = 11.sp,
                )
                Text(
                    "Pinch zoom · drag pan · double-tap reset",
                    color = Config.TEXT_DIM,
                    fontSize = 10.sp,
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (vm.generating || puzzle == null || level == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Config.ACCENT)
                    Spacer(Modifier.height(12.dp))
                    Text("Generating puzzle…", color = Config.TEXT_DIM)
                }
            } else {
                BoardCanvas(
                    modifier = Modifier.fillMaxSize(),
                    puzzle = puzzle,
                    hoverArrowId = vm.hoverArrowId,
                    errorArrowId = vm.errorArrowId,
                    errorBlinkOn = vm.errorBlinkPhaseOn(),
                    flowAnims = vm.flowAnims.toList(),
                    frameTick = tick,
                    onLayout = { layout -> vm.layoutCellSize = layout.cellSize },
                    onCellSelect = { r, c -> vm.tryClickCell(r, c) },
                    onHoverCell = { r, c -> vm.setHoverCell(r, c) },
                )

                // Points popup — never use negative padding (crashed the slide animation).
                vm.lastPointsPopup?.let { (text, t0) ->
                    val age = (SystemClock.elapsedRealtime() - t0) / 1000f
                    if (age < 0.8f) {
                        val col = if (text.startsWith("−") || text.startsWith("-")) Config.DANGER else Config.SUCCESS
                        val topPad = (8f - age * 20f).coerceAtLeast(0f)
                        Text(
                            text,
                            color = col,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topPad.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CelebrateScreen(vm: GameViewModel) {
    val c = vm.completion
    val tick = vm.frameTick
    Box(
        Modifier
            .fillMaxSize()
            .background(Config.BG)
            // Tap anywhere to skip the fireworks wait
            .clickable { vm.finishCelebration() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            tick
            for (p in vm.fireworks) {
                val fade = max(0f, 1f - p.age / max(0.05f, p.life))
                val r = max(4f, 10f * fade + 3f)
                val col = Color(p.colorArgb).copy(alpha = 0.45f + 0.55f * fade)
                drawCircle(col, r, Offset(p.x, p.y))
                val core = max(2f, r / 2f)
                drawRect(col, Offset(p.x - core, p.y - core), androidx.compose.ui.geometry.Size(core * 2, core * 2))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nice clear!", color = Config.SUCCESS, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val tBonus = c?.timeBonus ?: 0
            if (tBonus > 0) {
                Text("+$tBonus  time bonus!", color = Config.WARN, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${c?.secondsUnder ?: 0}s under ${"%.0f".format(c?.targetSec ?: 0f)}s target  ·  run ${"%.1f".format(c?.elapsed ?: 0f)}s",
                    color = Config.TEXT,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text("Time bonus  +0", color = Config.TEXT_DIM, fontSize = 20.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Tap to continue", color = Config.TEXT_DIM, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CompleteScreen(vm: GameViewModel) {
    val c = vm.completion
    val tick = vm.frameTick
    Box(Modifier.fillMaxSize().background(Config.BG), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            tick
            for (p in vm.fireworks) {
                val fade = max(0f, 1f - p.age / max(0.05f, p.life))
                val r = max(4f, 10f * fade + 3f)
                val col = Color(p.colorArgb).copy(alpha = 0.45f + 0.55f * fade)
                drawCircle(col, r, Offset(p.x, p.y))
            }
        }
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Config.PANEL)
                .border(2.dp, Config.PANEL_BORDER, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .fillMaxWidth(0.92f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Puzzle complete!", color = Config.SUCCESS, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (c != null) {
                Text("Score: ${c.score}", color = Config.TEXT, fontSize = 18.sp)
                Text(
                    "  arrows  ${c.arrowGross}" + if (c.penalties > 0) "   penalties  −${c.penalties}" else "",
                    color = Config.TEXT,
                    fontSize = 14.sp,
                )
                Text(
                    "  time bonus  +${c.timeBonus}   (target ${"%.0f".format(c.targetSec)}s · ${c.secondsUnder}s under)",
                    color = Config.TEXT,
                    fontSize = 14.sp,
                )
                Text(
                    "Time: ${"%.1f".format(c.elapsed)}s   Moves: ${c.moves}",
                    color = Config.TEXT,
                    fontSize = 14.sp,
                )
                Text("Personal best: ${c.best}", color = Config.TEXT, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Again") { vm.replay() }
                SecondaryButton("Levels") { vm.backToLevels() }
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Config.ACCENT,
            contentColor = Color(0xFF0A0E16),
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF465A78),
            contentColor = Config.TEXT,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun SmallHudButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF464E5F),
            contentColor = Config.TEXT,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(label, fontSize = 12.sp)
    }
}
