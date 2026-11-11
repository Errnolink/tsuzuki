package dev.errnolink.tsuzuki.lab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignLabTest {

    @Test
    fun `registry keys are unique and non-empty`() {
        val keys = DesignLab.all.map { it.key }
        assertTrue(keys.all { it.isNotBlank() })
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `every option default is its first choice`() {
        DesignLab.all.forEach { option ->
            assertEquals(option.choices.first(), option.default, "default of ${option.key} is not its first choice")
        }
    }

    @Test
    fun `mix encodes in registry order and round trips for the full registry`() {
        val values = DesignLab.all.associate { it.key to it.choices.last().name }
        val line = LabMix.encode(values)
        assertTrue(line.startsWith("v1;"))
        assertEquals(DesignLab.all.map { it.key }, line.split(';').drop(1).map { it.substringBefore('=') })
        val decoded = LabMix.decode(line)
        assertFalse(decoded.malformed)
        assertEquals(values, decoded.accepted)
        assertTrue(decoded.ignoredKeys.isEmpty())
        assertTrue(decoded.ignoredValues.isEmpty())
        assertEquals(line, LabMix.encode(decoded.accepted))
    }

    @Test
    fun `mix format matches the contract line`() {
        assertEquals("v1;pressShape=on;tabBar=liquid", LabMix.encode(mapOf("tabBar" to "liquid", "pressShape" to "on")))
    }

    @Test
    fun `decode ignores unknown keys and values and reports both`() {
        val result = LabMix.decode("v1;notAKey=on;tabBar=nope;pressShape=on")
        assertEquals(mapOf("pressShape" to "on"), result.accepted)
        assertEquals(listOf("notAKey"), result.ignoredKeys)
        assertEquals(listOf("nope"), result.ignoredValues)
        assertFalse(result.malformed)
    }

    @Test
    fun `decode of empty or malformed mix changes nothing`() {
        listOf("", "   ", "garbage", "v2;pressShape=on", "v1;brokenpair", "v1;=on").forEach { text ->
            val result = LabMix.decode(text)
            assertTrue(result.accepted.isEmpty(), "expected no accepted pairs for '$text'")
            assertTrue(result.malformed, "expected malformed for '$text'")
            assertTrue(result.ignoredKeys.isEmpty())
            assertTrue(result.ignoredValues.isEmpty())
        }
    }

    @Test
    fun `stored non-default value reads as default when the lab is disabled`() {
        DesignLab.all.forEach { option ->
            assertEquals(option.default, option.resolve(option.choices.last().name, enabled = false))
            assertEquals(option.default, option.resolve("madeUpChoice", enabled = false))
            assertEquals(option.default, option.resolve(null, enabled = false))
        }
    }

    @Test
    fun `stored value resolves to its choice when the lab is enabled`() {
        assertEquals(DesignLab.PressShape.on, DesignLab.pressShape.resolve("on", enabled = true))
        assertEquals(DesignLab.PressShape.off, DesignLab.pressShape.resolve("madeUpChoice", enabled = true))
        assertEquals(DesignLab.PressShape.off, DesignLab.pressShape.resolve(null, enabled = true))
        assertEquals(DesignLab.TabBar.liquid, DesignLab.tabBar.resolve("liquid", enabled = true))
    }

    @Test
    fun `release build types gate the lab off`() {
        listOf("release", "releaseTest", "foss", "unknownType").forEach { buildType ->
            assertFalse(DesignLab.buildTypeEnabled(buildType), "expected $buildType to gate the lab off")
        }
        listOf("debug", "benchmark", "preview").forEach { buildType ->
            assertTrue(DesignLab.buildTypeEnabled(buildType), "expected $buildType to gate the lab on")
        }
    }

    @Test
    fun `preset targets only options carrying that choice`() {
        val glass = DesignLab.presetTargets("glass")
        assertTrue(glass.isNotEmpty())
        assertEquals(DesignLab.all.filter { option -> option.choices.any { it.name == "glass" } }, glass)
        assertTrue(glass.all { option -> option.choices.any { it.name == "glass" } })
        assertTrue(DesignLab.presetTargets("blend").isEmpty())
        assertTrue(DesignLab.presetTargets("liquid").isNotEmpty())
        assertTrue(DesignLab.presetTargets("liquid").all { option -> option.choices.any { it.name == "liquid" } })
    }

    @Test
    fun `continue surface dock requires tab bar liquid`() {
        val continueSurface = DesignLab.option("continueSurface")!!
        val blocked = continueSurface.choiceBlocked("dock") { key -> if (key == "tabBar") "current" else "current" }
        assertTrue(blocked != null)
        assertEquals("tabBar", blocked!!.key)
        assertNull(continueSurface.choiceBlocked("dock") { "liquid" })
        assertNull(continueSurface.choiceBlocked("card") { "current" })
        assertNull(continueSurface.choiceBlocked("hero") { "current" })
    }
}
