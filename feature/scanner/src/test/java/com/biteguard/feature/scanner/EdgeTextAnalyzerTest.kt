package com.biteguard.feature.scanner

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTextAnalyzerTest {

    private fun testRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun clusterTextBoxes_emptyList_returnsEmpty() {
        val result = EdgeTextAnalyzer.clusterTextBoxes(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun clusterTextBoxes_singleBox_returnsExactBox() {
        val single = DetectedTextBox("Ingredients: Flour, Water", testRect(10, 10, 200, 50), true)
        val result = EdgeTextAnalyzer.clusterTextBoxes(listOf(single))
        assertEquals(1, result.size)
        assertEquals(single.text, result[0].text)
        assertEquals(10, result[0].boundingBox.left)
        assertEquals(50, result[0].boundingBox.bottom)
    }

    @Test
    fun clusterTextBoxes_stackedIngredientLines_mergesIntoSingleWideParagraph() {
        // Simulates ML Kit splitting a 3-line ingredient list on a product package
        val line1 = DetectedTextBox(
            text = "Ingredients:",
            boundingBox = testRect(50, 100, 200, 130),
            containsIngredientKeyword = true
        )
        val line2 = DetectedTextBox(
            text = "Enriched flour, water, yeast, salt,",
            boundingBox = testRect(50, 135, 450, 165),
            containsIngredientKeyword = false
        )
        val line3 = DetectedTextBox(
            text = "Contains: Wheat, Soy.",
            boundingBox = testRect(50, 170, 320, 200),
            containsIngredientKeyword = true
        )

        val result = EdgeTextAnalyzer.clusterTextBoxes(listOf(line1, line2, line3))

        // All 3 lines must merge into 1 cohesive paragraph block
        assertEquals(1, result.size)
        val merged = result[0]
        assertTrue(merged.containsIngredientKeyword)
        assertEquals(50, merged.boundingBox.left)
        assertEquals(100, merged.boundingBox.top)
        assertEquals(450, merged.boundingBox.right)
        assertEquals(200, merged.boundingBox.bottom)
        assertTrue(merged.text.contains("Ingredients:"))
        assertTrue(merged.text.contains("Enriched flour"))
        assertTrue(merged.text.contains("Contains: Wheat, Soy."))
    }

    @Test
    fun clusterTextBoxes_distinctColumns_keptSeparate() {
        // Left column: Nutrition Facts
        val leftCol = DetectedTextBox(
            text = "Nutrition Facts\nCalories 250",
            boundingBox = testRect(40, 100, 250, 300),
            containsIngredientKeyword = true
        )
        // Right column: Manufacturer Info (gap between right of leftCol and left of rightCol is 100px)
        val rightCol = DetectedTextBox(
            text = "Distributed by BiteGuard Corp\nChicago, IL 60601",
            boundingBox = testRect(350, 100, 580, 280),
            containsIngredientKeyword = false
        )

        val result = EdgeTextAnalyzer.clusterTextBoxes(listOf(leftCol, rightCol))

        // Separate columns should not merge
        assertEquals(2, result.size)
    }

    @Test
    fun clusterTextBoxes_sameLineContinuation_mergesHorizontally() {
        // When ML Kit splits a single line into 2 adjacent horizontal fragments
        val part1 = DetectedTextBox(
            text = "May contain:",
            boundingBox = testRect(50, 200, 150, 230),
            containsIngredientKeyword = true
        )
        val part2 = DetectedTextBox(
            text = "Tree nuts and peanuts.",
            boundingBox = testRect(160, 200, 380, 230),
            containsIngredientKeyword = false
        )

        val result = EdgeTextAnalyzer.clusterTextBoxes(listOf(part1, part2))

        assertEquals(1, result.size)
        assertTrue(result[0].containsIngredientKeyword)
        assertEquals(50, result[0].boundingBox.left)
        assertEquals(380, result[0].boundingBox.right)
    }

    @Test
    fun clusterTextBoxes_strayNoiseFilteredOut() {
        val ingredientHeader = DetectedTextBox(
            text = "Ingredients: Oats, Honey",
            boundingBox = testRect(50, 100, 300, 140),
            containsIngredientKeyword = true
        )
        val strayPunctuation = DetectedTextBox(
            text = ".",
            boundingBox = testRect(10, 10, 15, 15),
            containsIngredientKeyword = false
        )
        val isolatedChar = DetectedTextBox(
            text = "#",
            boundingBox = testRect(500, 500, 510, 510),
            containsIngredientKeyword = false
        )

        val result = EdgeTextAnalyzer.clusterTextBoxes(listOf(ingredientHeader, strayPunctuation, isolatedChar))

        assertEquals(1, result.size)
        assertEquals("Ingredients: Oats, Honey", result[0].text)
    }
}
