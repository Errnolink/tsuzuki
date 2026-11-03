package tachiyomi.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test

private const val WAIT_LAUNCH = 30_000L
private const val WAIT_SCREEN = 15_000L
private const val WAIT_ELEMENT = 8_000L

class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "dev.errnolink.tsuzuki.benchmark",
        profileBlock = {
            pressHome()
            startActivityAndWait()

            waitOnLibrary(device)

            val startedClassic = device.hasObject(By.text("More"))

            flingLibrary(device)
            swipeCategoryPager(device)
            openDetailsAndBack(device)
            waitOnLibrary(device)

            if (startedClassic) {
                switchClassicTabs(device)
                switchNavigationStyle(device, "MangaDex")
                switchMangaDexTabs(device)
                switchNavigationStyle(device, "Classic")
            } else {
                switchMangaDexTabs(device)
                switchNavigationStyle(device, "Classic")
                switchClassicTabs(device)
                switchNavigationStyle(device, "MangaDex")
            }
            waitOnLibrary(device)
        },
    )
}

private fun waitOnLibrary(device: UiDevice) {
    check(device.wait(Until.hasObject(By.text("Library")), WAIT_LAUNCH)) { "Library did not appear" }
    device.waitForIdle()
    expandNavBar(device)
}

private fun flingLibrary(device: UiDevice) {
    val x = device.displayWidth / 2
    val downStart = (device.displayHeight * 0.70).toInt()
    val downEnd = (device.displayHeight * 0.30).toInt()
    repeat(3) {
        device.swipe(x, downStart, x, downEnd, 8)
        device.waitForIdle()
    }
    repeat(3) {
        device.swipe(x, downEnd, x, downStart, 8)
        device.waitForIdle()
    }
    expandNavBar(device)
}

private fun swipeCategoryPager(device: UiDevice) {
    val y = (device.displayHeight * 0.60).toInt()
    val leftEdge = (device.displayWidth * 0.88).toInt()
    val rightEdge = (device.displayWidth * 0.12).toInt()
    device.swipe(leftEdge, y, rightEdge, y, 16)
    device.waitForIdle()
    device.swipe(rightEdge, y, leftEdge, y, 16)
    device.waitForIdle()
}

private fun openDetailsAndBack(device: UiDevice) {
    val selector = By.desc(Pattern.compile(".+"))
    val found = device.wait(Until.findObjects(selector), WAIT_SCREEN).orEmpty()
    val tile = found.firstOrNull { !isChromeDescription(it.contentDescription?.toString().orEmpty()) }
    checkNotNull(tile) { "no library tile visible; fixture library is empty or covers never rendered" }
    val label = tile.contentDescription?.toString() ?: "unknown"
    tile.click()
    check(device.wait(Until.hasObject(By.text("Chapters")), WAIT_SCREEN)) {
        "Details did not open after clicking tile \"$label\""
    }
    device.waitForIdle()
    val x = device.displayWidth / 2
    device.swipe(x, (device.displayHeight * 0.70).toInt(), x, (device.displayHeight * 0.35).toInt(), 12)
    device.waitForIdle()
    device.pressBack()
    waitOnLibrary(device)
}

private fun isChromeDescription(desc: String): Boolean = when {
    desc == "Search" -> true
    desc == "Menu" -> true
    desc == "Resume next chapter" -> true
    desc.startsWith("Library filters") -> true
    desc.endsWith(", expand navigation") -> true
    else -> false
}

private fun switchMangaDexTabs(device: UiDevice) {
    for (label in listOf("Feed", "Browse", "Library")) {
        clickTab(device, label)
    }
}

private fun switchClassicTabs(device: UiDevice) {
    for (label in listOf("Updates", "History", "Browse", "More", "Library")) {
        clickTab(device, label)
    }
}

private fun switchNavigationStyle(device: UiDevice, target: String) {
    val current = if (target == "Classic") "MangaDex" else "Classic"
    openSettings(device)
    scrollAndClickText(device, "Appearance")
    scrollAndClickText(device, "Navigation style")
    check(device.wait(Until.hasObject(By.text(target)), WAIT_ELEMENT)) {
        "option \"$target\" missing from the Navigation style popup"
    }
    clickText(device, target)
    check(device.wait(Until.gone(By.text(current)), WAIT_ELEMENT)) {
        "navigation style did not switch to \"$target\""
    }
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    device.pressBack()
    waitOnLibrary(device)
}

private fun openSettings(device: UiDevice) {
    clickTab(device, "Library")
    clickDesc(device, "Menu")
    clickText(device, "Settings")
    check(device.wait(Until.hasObject(By.text("Appearance")), WAIT_SCREEN)) { "Settings did not open" }
}

private fun scrollAndClickText(device: UiDevice, label: String) {
    val list = checkNotNull(device.wait(Until.findObject(By.scrollable(true)), WAIT_SCREEN)) {
        "no scrollable list found while looking for \"$label\""
    }
    list.scrollUntil(Direction.DOWN, Until.findObject(By.text(label)))
    checkNotNull(device.wait(Until.findObject(By.text(label)), WAIT_ELEMENT)) {
        "row \"$label\" not found after scrolling"
    }.click()
    device.waitForIdle()
}

private fun clickTab(device: UiDevice, label: String) {
    expandNavBar(device)
    checkNotNull(device.wait(Until.findObject(By.text(label)), WAIT_SCREEN)) {
        "tab \"$label\" not found"
    }.click()
    device.waitForIdle()
}

private fun expandNavBar(device: UiDevice) {
    val inline = device.findObject(By.descContains(", expand navigation")) ?: return
    inline.click()
    device.waitForIdle()
}

private fun clickText(device: UiDevice, label: String) {
    checkNotNull(device.wait(Until.findObject(By.text(label)), WAIT_ELEMENT)) {
        "text \"$label\" not found"
    }.click()
    device.waitForIdle()
}

private fun clickDesc(device: UiDevice, desc: String) {
    checkNotNull(device.wait(Until.findObject(By.desc(desc)), WAIT_ELEMENT)) {
        "description \"$desc\" not found"
    }.click()
    device.waitForIdle()
}
