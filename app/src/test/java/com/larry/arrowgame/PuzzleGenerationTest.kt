package com.larry.arrowgame

import com.larry.arrowgame.game.LEVELS
import com.larry.arrowgame.game.generatePuzzle
import com.larry.arrowgame.game.verifySolvable
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleGenerationTest {
    @Test
    fun beginnerPuzzleIsSolvable() {
        val lv = LEVELS.first { it.id == 1 }
        val p = generatePuzzle(lv, seed = 42)
        assertTrue(p.arrows.isNotEmpty())
        assertTrue(p.shape.isNotEmpty())
        assertTrue(verifySolvable(p))
    }

    @Test
    fun easyPuzzleIsSolvable() {
        val lv = LEVELS.first { it.id == 2 }
        val p = generatePuzzle(lv, seed = 42)
        assertTrue(verifySolvable(p))
    }
}
