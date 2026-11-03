package dev.errnolink.tsuzuki.perf

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Printer
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import coil3.EventListener
import dev.errnolink.tsuzuki.designsystem.coverRatioMutationCount
import eu.kanade.tachiyomi.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

object PerfTimelineTracer {

    val isEnabled: Boolean = BuildConfig.PERF_TRACING

    val coilEventListenerFactory: EventListener.Factory = PerfCoilEventListener.Factory()

    @PublishedApi
    internal val processStartUptime: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Process.getStartUptimeMillis()
    } else {
        SystemClock.uptimeMillis()
    }

    private val isInitialized = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)
    private val firstFrameDrawn = AtomicBoolean(false)
    private val firstCoverVisible = AtomicBoolean(false)
    private val libraryDataReady = AtomicBoolean(false)
    private val coversSettled = AtomicBoolean(false)

    private val inFlightCovers = AtomicInteger(0)
    private var libraryDataReadyUptime = 0L

    private val aggregator = PerfBucketAggregator()

    private var logFile: File? = null
    private var logWriter: BufferedWriter? = null
    private val fileLock = Any()

    private var metricsThread: HandlerThread? = null
    private var metricsHandler: Handler? = null
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    private var attachedWindow: Window? = null

    private var sessionHandler: Handler? = null
    private var coversSettlingRunnable: Runnable? = null

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMoved = false
    @Volatile
    private var scrollActiveUntilUptime = 0L

    fun init(application: Application) {
        if (!isEnabled || !isInitialized.compareAndSet(false, true)) return

        setupFileSink(application)
        installLooperMonitor()
        startSessionScheduler()
        markStage("Application.onCreate start")
    }

    fun markStage(name: String, details: String? = null) {
        if (!isEnabled) return
        val offset = SystemClock.uptimeMillis() - processStartUptime
        val message = if (details != null) {
            String.format(Locale.US, "[TsuzukiPerf] [Stage] %s (+%dms, %s)", name, offset, details)
        } else {
            String.format(Locale.US, "[TsuzukiPerf] [Stage] %s (+%dms)", name, offset)
        }
        emit(message)
    }

    inline fun <T> measureStage(name: String, block: () -> T): T {
        if (!isEnabled) return block()
        val start = SystemClock.uptimeMillis()
        try {
            return block()
        } finally {
            val took = SystemClock.uptimeMillis() - start
            val offset = SystemClock.uptimeMillis() - processStartUptime
            val message = String.format(
                Locale.US,
                "[TsuzukiPerf] [Stage] %s (+%dms, took %dms)",
                name,
                offset,
                took,
            )
            emit(message)
        }
    }

    fun attachToWindow(activity: ComponentActivity) {
        if (!isEnabled || attachedWindow != null) return
        val window = activity.window
        attachedWindow = window

        val thread = HandlerThread("perf-frame-metrics")
        thread.start()
        metricsThread = thread
        val handler = Handler(thread.looper)
        metricsHandler = handler

        val refreshRate = getRefreshRate(activity)
        val frameBudgetMs = if (refreshRate > 0f) 1000.0 / refreshRate else 16.6667

        val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val inputNs = frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
            val animNs = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
            val layoutNs = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            val drawNs = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
            val syncNs = frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)
            val commandNs = frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)

            val totalMs = totalNs / 1_000_000.0
            val uiMs = (inputNs + animNs + layoutNs) / 1_000_000.0
            val drawMs = (drawNs + syncNs + commandNs) / 1_000_000.0

            aggregator.recordFrame(totalMs, uiMs, drawMs, frameBudgetMs)
        }
        frameMetricsListener = listener
        window.addOnFrameMetricsAvailableListener(listener, handler)

        window.decorView.post {
            Choreographer.getInstance().postFrameCallback {
                onFirstFrameDrawn(activity)
            }
        }
    }

    private fun getRefreshRate(activity: ComponentActivity): Float {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.refreshRate
            }
        } catch (_: Throwable) {
            60f
        }
    }

    fun onFirstFrameDrawn(activity: ComponentActivity) {
        if (!isEnabled || !firstFrameDrawn.compareAndSet(false, true)) return
        try {
            activity.reportFullyDrawn()
        } catch (_: Throwable) {
        }
        markStage("First frame drawn")
    }

    fun onLibraryDataReady(itemCount: Int) {
        if (!isEnabled || !libraryDataReady.compareAndSet(false, true)) return
        libraryDataReadyUptime = SystemClock.uptimeMillis()
        markStage("Library data ready", "items=$itemCount")
        checkCoversSettling()
    }

    fun onCoilRequestStarted() {
        if (!isEnabled) return
        val current = inFlightCovers.incrementAndGet()
        aggregator.updateCoilInFlight(current)
        checkCoversSettling()
    }

    fun onCoilRequestSuccess(
        source: String,
        durationMs: Long,
        fetchMs: Long,
        decodeMs: Long,
        decodedW: Int,
        decodedH: Int,
        viewW: Int?,
        viewH: Int?,
    ) {
        if (!isEnabled) return
        val remaining = inFlightCovers.decrementAndGet()
        if (firstCoverVisible.compareAndSet(false, true)) {
            val viewStr = if (viewW != null && viewH != null) "${viewW}x${viewH}" else "unknown"
            markStage(
                "First cover visible",
                String.format(
                    Locale.US,
                    "src=%s, latency=%dms, size=%dx%d vs view=%s",
                    source,
                    durationMs,
                    decodedW,
                    decodedH,
                    viewStr,
                ),
            )
        }
        aggregator.recordCoilSuccess(
            source = source,
            durationMs = durationMs,
            fetchMs = fetchMs,
            decodeMs = decodeMs,
            decodedW = decodedW,
            decodedH = decodedH,
            viewW = viewW,
            viewH = viewH,
            inFlight = remaining,
        )
        checkCoversSettling()
    }

    fun onCoilRequestFinished() {
        if (!isEnabled) return
        val remaining = inFlightCovers.decrementAndGet()
        aggregator.updateCoilInFlight(remaining)
        checkCoversSettling()
    }

    private fun checkCoversSettling() {
        if (!isEnabled || !libraryDataReady.get() || coversSettled.get()) return
        val handler = sessionHandler ?: return

        synchronized(this) {
            if (inFlightCovers.get() == 0) {
                if (coversSettlingRunnable == null) {
                    val runnable = Runnable {
                        if (inFlightCovers.get() == 0 && coversSettled.compareAndSet(false, true)) {
                            markStage("Covers settled", "0 in-flight for 1.0s after library data")
                        }
                    }
                    coversSettlingRunnable = runnable
                    handler.postDelayed(runnable, 1000L)
                }
            } else {
                coversSettlingRunnable?.let {
                    handler.removeCallbacks(it)
                    coversSettlingRunnable = null
                }
            }
        }
    }

    fun onTouchEvent(ev: MotionEvent) {
        if (!isEnabled) return
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                touchMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.rawX - touchDownX)
                val dy = abs(ev.rawY - touchDownY)
                if (dx > 16f || dy > 16f) {
                    touchMoved = true
                    scrollActiveUntilUptime = SystemClock.uptimeMillis() + 400L
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (touchMoved) {
                    scrollActiveUntilUptime = SystemClock.uptimeMillis() + 500L
                }
                touchMoved = false
            }
        }
    }

    private fun isScrollActive(): Boolean {
        return SystemClock.uptimeMillis() < scrollActiveUntilUptime
    }

    @PublishedApi
    internal fun emit(message: String) {
        Log.i("TsuzukiPerf", message)
        synchronized(fileLock) {
            try {
                logWriter?.apply {
                    write(message)
                    newLine()
                    flush()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun setupFileSink(application: Application) {
        try {
            val perfDir = File(application.filesDir, "perf")
            if (!perfDir.exists()) {
                perfDir.mkdirs()
            }
            val existing = perfDir.listFiles { _, name ->
                name.startsWith("launch-") && name.endsWith(".log")
            }?.toList() ?: emptyList()

            val toDelete = aggregator.pruneOldLogs(existing, keepCount = 9)
            toDelete.forEach { it.delete() }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(perfDir, "launch-$timestamp.log")
            logFile = file
            val writer = BufferedWriter(FileWriter(file, true))
            logWriter = writer

            val header = String.format(
                Locale.US,
                "# Tsuzuki Launch Trace: %s | Device: %s %s (API %d) | App: %s %s",
                timestamp,
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.SDK_INT,
                BuildConfig.APPLICATION_ID,
                BuildConfig.VERSION_NAME,
            )
            writer.write(header)
            writer.newLine()
            writer.flush()
        } catch (_: Throwable) {
        }
    }

    private fun installLooperMonitor() {
        val printer = Printer { msg ->
            if (msg == null || msg.isEmpty()) return@Printer
            val first = msg[0]
            if (first == '>') {
                looperMsgStartUptime = SystemClock.uptimeMillis()
            } else if (first == '<') {
                val start = looperMsgStartUptime
                if (start > 0L) {
                    val duration = SystemClock.uptimeMillis() - start
                    if (duration >= 50L) {
                        aggregator.recordSlowMessage(duration)
                    }
                    looperMsgStartUptime = 0L
                }
            }
        }
        Looper.getMainLooper().setMessageLogging(printer)
    }

    @Volatile
    private var looperMsgStartUptime = 0L

    private fun startSessionScheduler() {
        val thread = HandlerThread("perf-session-scheduler")
        thread.start()
        val handler = Handler(thread.looper)
        sessionHandler = handler

        var currentSecond = 1
        val ticker = object : Runnable {
            override fun run() {
                if (isStopped.get()) return
                val ratioWrites = coverRatioMutationCount.getAndSet(0)
                repeat(ratioWrites) {
                    aggregator.recordRatioWrite()
                }
                val snapshot = aggregator.snapshotAndReset(currentSecond, isScrollActive())
                emit(snapshot.format())
                if (currentSecond >= 30) {
                    stop()
                } else {
                    currentSecond++
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(ticker, 1000L)
    }

    fun stop() {
        if (!isStopped.compareAndSet(false, true)) return
        markStage("Tracing window ended", "30s cold launch trace complete")

        try {
            Looper.getMainLooper().setMessageLogging(null)
        } catch (_: Throwable) {
        }

        try {
            attachedWindow?.let { win ->
                frameMetricsListener?.let { listener ->
                    win.removeOnFrameMetricsAvailableListener(listener)
                }
            }
        } catch (_: Throwable) {
        }

        try {
            metricsThread?.quitSafely()
        } catch (_: Throwable) {
        }

        try {
            sessionHandler?.looper?.quitSafely()
        } catch (_: Throwable) {
        }

        synchronized(fileLock) {
            try {
                logWriter?.flush()
                logWriter?.close()
                logWriter = null
            } catch (_: Throwable) {
            }
        }
    }

    fun shareLatestLog(context: Context) {
        val perfDir = File(context.filesDir, "perf")
        val files = perfDir.listFiles { _, name ->
            name.startsWith("launch-") && name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() }
        val latest = files?.firstOrNull()

        if (latest == null || !latest.exists()) {
            Toast.makeText(context, "No performance trace log found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", latest)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share performance log"))
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to share log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
