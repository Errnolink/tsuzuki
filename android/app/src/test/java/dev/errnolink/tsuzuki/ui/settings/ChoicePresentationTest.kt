package dev.errnolink.tsuzuki.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChoicePresentationTest {

    @Test
    fun `single choice opens the menu`() {
        assertEquals(ChoicePresentation.Menu, choicePresentation(1, multi = false))
    }

    @Test
    fun `seven choices open the menu`() {
        assertEquals(ChoicePresentation.Menu, choicePresentation(7, multi = false))
    }

    @Test
    fun `eight choices keep the existing presentation`() {
        assertEquals(ChoicePresentation.Existing, choicePresentation(8, multi = false))
    }

    @Test
    fun `multi select keeps the existing presentation`() {
        assertEquals(ChoicePresentation.Existing, choicePresentation(3, multi = true))
    }

    @Test
    fun `no choices keep the existing presentation`() {
        assertEquals(ChoicePresentation.Existing, choicePresentation(0, multi = false))
    }
}
