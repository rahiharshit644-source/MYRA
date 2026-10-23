package com.soltini.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "CurlNoiseOrb"

/** A WebView that never takes touch events, so the LazyColumn around it keeps scrolling smoothly. */
@SuppressLint("ViewConstructor")
private class PassThroughWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/** Lets the orb page tell us when WebGL doesn't work on this device (so we can draw a native orb instead). */
private class OrbBridge(private val onFail: () -> Unit) {
    @JavascriptInterface
    fun onFailed(why: String?) {
        Log.w(TAG, "WebGL orb failed: $why")
        Handler(Looper.getMainLooper()).post { onFail() }
    }
}

/**
 * Main-screen status orb.
 *
 * Layer 1 (always, native): a coloured halo + pulsing rings that show Myra's state even if WebGL fails.
 * Layer 2: the Curl Noise Orb (WebGL, offline) which changes colour/speed with [state] and swells with [level].
 *          If WebGL fails on this phone, a native particle sphere is drawn instead.
 */
private fun isRunningOnEmulator(): Boolean {
    val fp = android.os.Build.FINGERPRINT ?: ""
    val model = android.os.Build.MODEL ?: ""
    val hw = android.os.Build.HARDWARE ?: ""
    val product = android.os.Build.PRODUCT ?: ""
    return fp.startsWith("generic") ||
           fp.startsWith("unknown") ||
           model.contains("google_sdk", ignoreCase = true) ||
           model.contains("Emulator", ignoreCase = true) ||
           model.contains("Android SDK built for x86", ignoreCase = true) ||
           hw.contains("goldfish", ignoreCase = true) ||
           hw.contains("ranchu", ignoreCase = true) ||
           product.contains("sdk_gphone", ignoreCase = true)
}

@Composable
fun MyraStatusOrb(
    state: MyraOrbState,
    level: Float,
    modifier: Modifier = Modifier
) {
    var webglFailed by remember { mutableStateOf(isRunningOnEmulator()) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        StateHalo(state = state, level = level, modifier = Modifier.matchParentSize())
        if (webglFailed) {
            NativeFallbackOrb(state = state, level = level, modifier = Modifier.matchParentSize())
        } else {
            CurlNoiseOrb(
                state = state,
                level = level,
                onFailed = { webglFailed = true },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/** Soft glow + expanding rings in the state colour. Cheap native drawing, always visible. */
@Composable
private fun StateHalo(state: MyraOrbState, level: Float, modifier: Modifier = Modifier) {
    val currentMode = com.soltini.app.ui.theme.LocalMyraTokens.current.mode
    val isBlueGlass = currentMode == com.soltini.app.ui.theme.MyraThemeMode.BLUE_GLASS
    val color by animateColorAsState(state.colorFor(currentMode), tween(350), label = "haloColor")
    val periodMs = when (state) {
        MyraOrbState.SPEAKING -> 900
        MyraOrbState.LISTENING -> 2400
        MyraOrbState.CONNECTING -> 1400
        else -> 4000
    }
    val transition = rememberInfiniteTransition(label = "halo")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "haloPhase"
    )
    val hudAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "hudAngle"
    )
    val counterAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "counterAngle"
    )
    val animated = state == MyraOrbState.LISTENING || state == MyraOrbState.SPEAKING || state == MyraOrbState.CONNECTING
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * 0.30f
        val glowAlpha = when (state) {
            MyraOrbState.OFF, MyraOrbState.PAUSED -> 0.10f
            MyraOrbState.MUTED -> 0.16f
            else -> 0.24f + level * 0.16f
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                center = c,
                radius = base * 2.1f
            ),
            radius = base * 2.1f,
            center = c
        )

        // Futuristic JARVIS / EDITH HUD reticle elements in Blue Glass mode
        if (isBlueGlass) {
            val hudRadius = base * (1.30f + level * 0.14f)
            val arcLen = 60f
            for (i in 0 until 4) {
                val startAngle = hudAngle + i * 90f + 15f
                drawArc(
                    color = Color(0xFF00E5FF).copy(alpha = 0.50f),
                    startAngle = startAngle,
                    sweepAngle = arcLen,
                    useCenter = false,
                    style = Stroke(width = 2.4f)
                )
            }

            // Counter-rotating tick ring
            val innerRadius = base * 1.15f
            for (i in 0 until 8) {
                val tickAngle = (counterAngle + i * 45f) * (PI / 180f).toFloat()
                val x1 = c.x + cos(tickAngle) * (innerRadius - 3.5f)
                val y1 = c.y + sin(tickAngle) * (innerRadius - 3.5f)
                val x2 = c.x + cos(tickAngle) * (innerRadius + 3.5f)
                val y2 = c.y + sin(tickAngle) * (innerRadius + 3.5f)
                drawLine(
                    color = Color(0xFF1E6FFB).copy(alpha = 0.55f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2f
                )
            }

            // Cardinal crosshair ticks
            val reticleLen = 7f
            val reticleDist = base * 1.48f
            val cardinals = listOf(0f, 90f, 180f, 270f)
            for (deg in cardinals) {
                val rad = deg * (PI / 180f).toFloat()
                val x = c.x + cos(rad) * reticleDist
                val y = c.y + sin(rad) * reticleDist
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.40f),
                    start = Offset(x - reticleLen / 2, y),
                    end = Offset(x + reticleLen / 2, y),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.40f),
                    start = Offset(x, y - reticleLen / 2),
                    end = Offset(x, y + reticleLen / 2),
                    strokeWidth = 1.5f
                )
            }
        }

        if (animated) {
            for (i in 0..1) {
                val p = (phase + i * 0.5f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.35f),
                    radius = base * (1.05f + p * 0.75f + level * 0.15f),
                    center = c,
                    style = Stroke(width = 2.5f)
                )
            }
        } else {
            drawCircle(
                color = color.copy(alpha = 0.22f),
                radius = base * 1.12f,
                center = c,
                style = Stroke(width = 2.5f)
            )
        }
    }
}

/** Used only when WebGL is unavailable: rotating particle sphere in the state colour. */
@Composable
private fun NativeFallbackOrb(state: MyraOrbState, level: Float, modifier: Modifier = Modifier) {
    val color by animateColorAsState(state.colorFor(com.soltini.app.ui.theme.LocalMyraTokens.current.mode), tween(350), label = "fallbackColor")
    val speedMs = when (state) {
        MyraOrbState.SPEAKING -> 5000
        MyraOrbState.OFF, MyraOrbState.PAUSED -> 40000
        else -> 14000
    }
    val transition = rememberInfiniteTransition(label = "fallbackSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(speedMs, easing = LinearEasing), RepeatMode.Restart),
        label = "fallbackAngle"
    )
    val points = remember {
        val n = 220
        val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
        List(n) { i ->
            val y = 1f - (i / (n - 1f)) * 2f
            val r = sqrt(1f - y * y)
            val th = golden * i
            Triple(cos(th) * r, y, sin(th) * r)
        }
    }
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.30f * (1f + level * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.06f)),
                center = c,
                radius = radius
            ),
            radius = radius,
            center = c
        )
        val ca = cos(angle)
        val sa = sin(angle)
        for ((x, y, z) in points) {
            val rx = x * ca + z * sa
            val rz = -x * sa + z * ca
            val depth = (rz + 1f) / 2f
            drawCircle(
                color = color.copy(alpha = 0.25f + 0.65f * depth),
                radius = 1.4f + 2.2f * depth,
                center = Offset(c.x + rx * radius, c.y + y * radius)
            )
        }
    }
}

/**
 * The WebGL "Curl Noise Orb" (self-contained, offline). Colour/speed follow [state];
 * [level] (0..1, mic or speaker amplitude) makes it swell.
 * Rendering pauses automatically when the app is in the background.
 *
 * NOTE: we deliberately do NOT call WebView.pauseTimers() — that is process-wide and would also freeze the
 * in-app browser's background music playback.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun CurlNoiseOrb(
    state: MyraOrbState,
    level: Float,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    val onFailedState by rememberUpdatedState(onFailed)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PassThroughWebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                settings.apply {
                    javaScriptEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                    allowContentAccess = false
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = true
                }
                addJavascriptInterface(OrbBridge { onFailedState() }, "OrbBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageReady = true
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        Log.w(TAG, "Orb WebView render process gone (didCrash=${detail?.didCrash()}). Falling back to NativeFallbackOrb.")
                        onFailedState()
                        try {
                            view?.let {
                                (it.parent as? android.view.ViewGroup)?.removeView(it)
                                it.destroy()
                            }
                        } catch (_: Exception) {}
                        return true
                    }
                }
                // Load the page from a string instead of file:// so no file-access permission is needed.
                // "?n=192" = grid size (lighter than the 256 desktop default, still smooth on phones).
                val html = try {
                    ctx.assets.open("orb/curl_noise_orb.html").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    Log.e(TAG, "orb asset missing", e)
                    null
                }
                if (html == null) {
                    Handler(Looper.getMainLooper()).post { onFailedState() }
                } else {
                    loadDataWithBaseURL("https://myra.local/orb/index.html?n=192", html, "text/html", "UTF-8", null)
                }
                webView = this
            }
        }
    )

    // If the page never finishes loading, fall back to the native orb.
    LaunchedEffect(Unit) {
        delay(6000)
        if (!pageReady) onFailedState()
    }

    // Pause drawing when the app isn't visible; resume when it comes back.
    DisposableEffect(lifecycleOwner, webView, pageReady) {
        val wv = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> wv?.evaluateJavascript("window.orbSetRunning && window.orbSetRunning(true)", null)
                Lifecycle.Event.ON_PAUSE -> wv?.evaluateJavascript("window.orbSetRunning && window.orbSetRunning(false)", null)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Free the WebView when the screen leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.removeAllViews()
                it.destroy()
            }
            webView = null
        }
    }

    // Push the state (colour / speed) whenever it changes.
    LaunchedEffect(state, pageReady, webView) {
        if (pageReady) {
            webView?.evaluateJavascript("window.orbSetState && window.orbSetState('${state.js}')", null)
        }
    }

    // Feed the live voice amplitude (quantised so we don't flood the JS bridge).
    val quantised = (level.coerceIn(0f, 1f) * 20f).toInt() / 20f
    LaunchedEffect(quantised, pageReady, webView) {
        if (pageReady) {
            webView?.evaluateJavascript("window.orbSetLevel && window.orbSetLevel($quantised)", null)
        }
    }
}
