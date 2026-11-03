package exh.md.handlers

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.source.model.Page
import exh.md.dto.MangaUpViewerResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToString
import exh.md.utils.MdUtil
import okhttp3.Request
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaUpHandler(private val baseClient: OkHttpClient) {
    private val domain = "manga-up.com"
    private val apiUrl = "https://global-api.$domain/api"
    private val imgUrl = "https://global-img.$domain"
    private val lang = "en"

    val headers: Headers =
        Headers.Builder()
            .add("Origin", "https://global.$domain")
            .add("Referer", "https://global.$domain/")
            .add("User-Agent", "OpenAI File Downloader, XaiImageApiFetch/1.0")
            .build()

    @Volatile
    private var secret: String? = null

    val client: OkHttpClient by lazy {
        baseClient
            .newBuilder()
            .addInterceptor(MangaUpImageInterceptor())
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code != 410 && response.code != 401) return@addInterceptor response
                response.close()

                val failedSecret = request.url.queryParameter("secret")
                if (failedSecret != null) {
                    flushSecret(failedSecret)
                    synchronized(this) { if (secret == failedSecret) secret = null }
                }

                val newSecret = fetchSecret()
                val isSecretValid = !newSecret.isNullOrEmpty() && newSecret != failedSecret

                val newUrl = request.url.newBuilder()

                if (isSecretValid) {
                    newUrl.setQueryParameter("secret", newSecret)
                } else {
                    newUrl.removeAllQueryParameters("secret")
                    synchronized(this) { if (secret == newSecret) secret = null }
                }

                val newRequest = request.newBuilder().url(newUrl.build()).build()
                return@addInterceptor chain.proceed(newRequest)
            }
            .build()
    }

    fun pageListRequest(externalUrl: String): Request {
        val chapterId = externalUrl.toHttpUrl().pathSegments.last { it.isNotBlank() }
        val url =
            "$apiUrl/manga/viewer_v2"
                .toHttpUrl()
                .newBuilder()
                .apply { secret?.let { addQueryParameter("secret", it) } }
                .addQueryParameter("app_ver", "0")
                .addQueryParameter("os_ver", "0")
                .addQueryParameter("chapter_id", chapterId)
                .addQueryParameter("quality", "high")
                .addQueryParameter("lang", lang)
                .toString()
        return POST(url, headers)
    }

    suspend fun fetchPageList(externalUrl: String): List<Page> {
        val result = client.newCall(pageListRequest(externalUrl)).awaitSuccess().use { response ->
            ProtoBuf.decodeFromByteArray(MangaUpViewerResponse.serializer(), response.body.bytes())
        }
        return pageListParse(result)
    }

    fun pageListParse(result: MangaUpViewerResponse): List<Page> {
        val pages = result.pageBlocks.flatMap { it.pages }.filter { !it.url.contains("tutorial") }

        if (pages.isEmpty()) {
            throw Exception("Chapter requires login and/or purchasing on Manga UP!")
        }

        return pages.mapIndexed { i, page ->
            val imageUrl = imgUrl + page.url + if (page.key != null && page.iv != null) "#key=${page.key}#iv=${page.iv}" else ""
            Page(i, imageUrl = imageUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun fetchSecret(): String? {
        if (secret != null) return secret

        val latch = CountDownLatch(1)
        var token: String? = null

        val handler = Handler(Looper.getMainLooper())
        var pendingView: WebView? = null
        handler.post {
            val webView = WebView(Injekt.get<Application>())
            pendingView = webView
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript("window.localStorage.getItem('secret')") { value ->
                            token = value?.trim('"')
                            if (token == "null" || token.isNullOrBlank()) token = null

                            latch.countDown()
                            view.stopLoading()
                            view.destroy()
                            pendingView = null
                        }
                    }
                }
            webView.loadDataWithBaseURL(
                "https://global.$domain/",
                " ",
                "text/html",
                "utf-8",
                null,
            )
        }

        latch.await(10, TimeUnit.SECONDS)
        handler.post {
            pendingView?.stopLoading()
            pendingView?.destroy()
            pendingView = null
        }

        secret = token
        return secret
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun flushSecret(target: String) {
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(Injekt.get<Application>())
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val script =
                            "if(window.localStorage.getItem('secret')===${MdUtil.jsonParser.encodeToString(target)}){window.localStorage.removeItem('secret');}"
                        view.evaluateJavascript(script) {
                            view.stopLoading()
                            view.destroy()
                        }
                    }
                }
            webView.loadDataWithBaseURL(
                "https://global.$domain/",
                " ",
                "text/html",
                "utf-8",
                null,
            )
        }
    }
}

class MangaUpImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val fragment = url.fragment

        if (fragment.isNullOrEmpty()) return chain.proceed(request)

        val parts = fragment.split("#")
        val key = parts.getOrNull(0)?.removePrefix("key=")
        val iv = parts.getOrNull(1)?.removePrefix("iv=")

        if (key.isNullOrEmpty() || iv.isNullOrEmpty()) return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val secretKey = SecretKeySpec(key.hexToByteArray(), "AES")
        val ivSpec = IvParameterSpec(iv.hexToByteArray())
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val body =
            response.body
                .source()
                .cipherSource(cipher)
                .buffer()
                .asResponseBody(response.body.contentType())

        return response.newBuilder().body(body).build()
    }
}
