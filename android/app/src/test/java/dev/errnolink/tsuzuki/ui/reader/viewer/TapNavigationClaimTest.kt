// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/org/nekomanga/presentation/screens/reader/viewer/TapNavigationClaimTest.kt (Neko #3486)
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.ui.input.pointer.PointerId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TapNavigationClaimTest {

    @Test
    fun `no pointer is claimed before a control claims one`() {
        val claim = TapNavigationClaim()

        assertFalse(claim.isClaimed(PointerId(1)))
    }

    @Test
    fun `claimed pointer is reported as claimed`() {
        val claim = TapNavigationClaim()

        claim.claim(PointerId(1))

        assertTrue(claim.isClaimed(PointerId(1)))
    }

    @Test
    fun `later pointer is not covered by an earlier claim`() {
        val claim = TapNavigationClaim()

        claim.claim(PointerId(1))

        assertFalse(claim.isClaimed(PointerId(2)))
    }
}
