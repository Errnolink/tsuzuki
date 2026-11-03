// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.shell.ShellList
import eu.kanade.presentation.more.settings.screen.SettingsMangadexScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import exh.md.utils.MdUtil
import exh.ui.intercept.InterceptActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.math.BigInteger
import java.net.URI

object MangaDexIntents {
    const val SETTINGS = "eu.kanade.tachiyomi.MANGADEX_SETTINGS"
    data class Entity(val kind: String, val id: String)
    data class TrackerLink(val service: String, val serviceName: String, val lookupId: String)
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun entity(url: String?): Entity? {
        val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.host?.lowercase() !in setOf("mangadex.org", "www.mangadex.org")) return null
        val parts = uri.path.orEmpty().trim('/').split('/')
        if (parts.size < 2 || parts[0] !in setOf("author", "group", "list") || !uuid.matches(parts[1])) return null
        return Entity(parts[0], parts[1].lowercase())
    }

    fun trackerLink(url: String?): TrackerLink? {
        val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme !in setOf("https", "http")) return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
        if (segments.isEmpty()) return null
        val (path, id) = if (segments.size == 1) "" to segments[0] else segments[0] to segments[1]
        if (id.isEmpty()) return null
        return when (uri.host?.lowercase()) {
            "anilist.co" -> if (path == "manga") TrackerLink("al", "AniList", id) else null
            "myanimelist.net" -> if (path == "manga") TrackerLink("mal", "MyAnimeList", id) else null
            "www.mangaupdates.com", "mangaupdates.com" ->
                if (path == "series") {
                    TrackerLink("mu_new", "MangaUpdates", runCatching { BigInteger(id, 36).toString() }.getOrNull() ?: return null)
                } else {
                    null
                }
            "mangabaka.org" -> TrackerLink("mb", "MangaBaka", id)
            else -> null
        }
    }

    fun handle(intent: Intent, navigator: Navigator): Boolean {
        val screen = when {
            intent.action == SETTINGS -> SettingsMangadexScreen
            intent.action == Intent.ACTION_VIEW -> entity(intent.dataString)?.let {
                if (it.kind == "list") MangaDexListScreen(it.id, "MDList") else MangaDexEntityScreen(it.kind, it.id)
            } ?: trackerLink(intent.dataString)?.let(::MangaDexTrackerScreen)
            else -> null
        } ?: return false
        navigator.popUntilRoot()
        navigator.push(screen)
        return true
    }
}

private class MangaDexEntityScreen(private val kind: String, private val id: String) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var unavailable by remember { mutableStateOf(false) }
        LaunchedEffect(kind, id) {
            val source = withContext(Dispatchers.IO) {
                val sources = Injekt.get<SourceManager>()
                sources.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sources)
            }
            if (source == null) unavailable = true else navigator.replace(BrowseSourceScreen(source.id, "$kind:$id"))
        }
        ShellList("MangaDex ${kind.replaceFirstChar(Char::uppercase)}", navigateUp = navigator::pop) {
            item {
                if (unavailable) MangaDexEmptyState("MangaDex source unavailable", "Enable a MangaDex source in Browse, then open this link again.")
                else MangaDexLoading("Opening MangaDex…")
            }
        }
    }
}

private class MangaDexTrackerScreen(private val link: MangaDexIntents.TrackerLink) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(link) {
            val uuid = withContext(Dispatchers.IO) {
                MangaMappings.get(context).getMangadexUUID(link.lookupId, link.service)
            }
            if (uuid == null) {
                error = "No MangaDex mapping entry found for ${link.serviceName} ID ${link.lookupId}"
            } else {
                context.startActivity(
                    Intent(context, InterceptActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData("https://mangadex.org/title/$uuid".toUri()),
                )
                navigator.popUntilRoot()
            }
        }
        ShellList("${link.serviceName} link", navigateUp = navigator::pop) {
            item {
                val message = error
                if (message == null) MangaDexLoading("Resolving ${link.serviceName} entry…")
                else MangaDexEmptyState(message, "The bundled MangaDex mapping database has no entry for this ${link.serviceName} ID.")
            }
        }
    }
}
