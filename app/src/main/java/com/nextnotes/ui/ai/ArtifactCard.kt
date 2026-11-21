package com.nextnotes.ui.ai

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextnotes.ai.Artifact
import com.nextnotes.ai.Artifacts
import com.nextnotes.ui.theme.sticker
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** What an answer's artifacts can do beyond showing themselves. */
class ArtifactHost(
    /** Start a follow-up asking the AI to change this artifact. */
    val onRevise: (Artifact) -> Unit,
    /** Put a PNG of the artifact into the note. */
    val onInsert: (ByteArray) -> Unit,
)

/** The host, and whether the answer holding these artifacts is still being written. */
data class ArtifactScope(val host: ArtifactHost?, val streaming: Boolean)

val LocalArtifactScope = compositionLocalOf { ArtifactScope(null, false) }

@Composable
fun ProvideArtifactScope(scope: ArtifactScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalArtifactScope provides scope, content = content)

/**
 * An artifact in the conversation: a live preview in a sandbox, with its source a tap away, a
 * full-screen view, and ways to revise it or put it into the note.
 */
@Composable
fun ArtifactCard(artifact: Artifact) {
    val scope = LocalArtifactScope.current
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val border = palette.border.toComposeColor()
    val accent = palette.accent.toComposeColor()
    val surface = palette.surface.toComposeColor()
    var showSource by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var shown by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val shape = RoundedCornerShape(10.dp)

    val chrome = com.nextnotes.ui.theme.LocalChrome.current
    Column(
        Modifier.fillMaxWidth().padding(end = chrome.drop, bottom = chrome.drop)
            .then(Modifier.sticker(chrome, palette.panel.toComposeColor(), shape)).clip(shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (chrome.isSticker) palette.materialColors.tertiaryContainer.toComposeColor() else surface).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Widgets, null, tint = accent, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(artifact.title, color = text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (scope.streaming) "生成中… ${artifact.source.lines().size} 行"
                    else if (artifact.kind == Artifact.Kind.SVG) "SVG 图示" else "交互页面 · 离线运行",
                    color = dim, fontSize = 11.sp,
                )
            }
            if (!scope.streaming) {
                CardButton(Icons.Outlined.Code, if (showSource) "预览" else "源码", if (showSource) accent else dim) { showSource = !showSource }
                CardButton(Icons.Outlined.OpenInFull, "全屏", dim) { fullScreen = true }
                scope.host?.let { host ->
                    CardButton(Icons.Outlined.Edit, "修改", dim) { host.onRevise(artifact) }
                    CardButton(Icons.Outlined.AddPhotoAlternate, "插入笔记", dim) {
                        web?.let { snapshotPng(it, shown, host.onInsert) }
                    }
                }
            }
        }
        when {
            // A half-written page would reload with every token; show it once it is whole.
            scope.streaming -> Box(Modifier.fillMaxWidth().height(64.dp).background(Color.White), contentAlignment = Alignment.Center) {
                Text("正在生成…", color = Color(0xFF888888), fontSize = 13.sp)
            }
            showSource -> Box(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()).padding(10.dp),
            ) {
                Text(artifact.source, color = text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            else -> {
                var heightDp by remember(artifact.source) { mutableIntStateOf(240) }
                SandboxView(
                    artifact,
                    onHeight = { heightDp = it.coerceIn(80, 480) },
                    onView = { web = it },
                    onShown = { shown = it },
                    modifier = Modifier.fillMaxWidth().height(heightDp.dp),
                )
            }
        }
    }

    if (fullScreen) {
        Dialog(onDismissRequest = { fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            com.nextnotes.ui.KeepBarsHidden()
            var full by remember { mutableStateOf<WebView?>(null) }
            var fullShown by remember { mutableStateOf<android.graphics.Rect?>(null) }
            Column(Modifier.fillMaxSize().background(palette.panel.toComposeColor())) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(artifact.title, color = text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    scope.host?.let { host ->
                        CardButton(Icons.Outlined.Edit, "修改", dim) { fullScreen = false; host.onRevise(artifact) }
                        CardButton(Icons.Outlined.AddPhotoAlternate, "插入笔记", dim) {
                            full?.let { snapshotPng(it, fullShown) { png -> host.onInsert(png); fullScreen = false } }
                        }
                    }
                    CardButton(Icons.Outlined.Close, "关闭", dim) { fullScreen = false }
                }
                SandboxView(artifact, onHeight = {}, onView = { full = it }, onShown = { fullShown = it }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CardButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * A WebView that can only run the page it was given: no network, no files, no navigation away,
 * no new windows, and no bridge back into the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxView(
    artifact: Artifact,
    onHeight: (Int) -> Unit,
    onView: (WebView) -> Unit,
    /** The part of the page on screen, in window pixels: clipped by the list it scrolls in. */
    onShown: (android.graphics.Rect?) -> Unit,
    modifier: Modifier,
) {
    val html = remember(artifact.source) { Artifacts.document(artifact) }
    AndroidView(
        modifier = modifier.onGloballyPositioned { c ->
            val b = c.boundsInWindow()
            onShown(
                if (b.width < 1f || b.height < 1f) null
                else android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()),
            )
        },
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                with(settings) {
                    javaScriptEnabled = true
                    blockNetworkLoads = true
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    databaseEnabled = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false)
                    mediaPlaybackRequiresUserGesture = true
                    setSupportZoom(false)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url.scheme
                        if (scheme == "data" || scheme == "blob" || request.url.toString() == "about:blank") return null
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // Size the card to the page, once scripts have laid it out.
                        view.postDelayed({
                            view.evaluateJavascript("Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0)") { v ->
                                v?.toFloatOrNull()?.let { onHeight(it.toInt() + 2) }
                            }
                        }, 150)
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                tag = html
                onView(this)
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

/**
 * What the artifact shows right now, as a PNG for the note. Copied from the screen rather than
 * drawn again, because a page's canvas lives on the GPU and a software redraw leaves it blank.
 */
private fun snapshotPng(view: WebView, shown: android.graphics.Rect?, onDone: (ByteArray) -> Unit) {
    val window = windowOf(view) ?: return
    val rect = shown?.takeIf { it.width() > 0 && it.height() > 0 } ?: return
    val bmp = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
    android.view.PixelCopy.request(window, rect, bmp, { result ->
        if (result == android.view.PixelCopy.SUCCESS) {
            val png = ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            onDone(png)
        }
        bmp.recycle()
    }, android.os.Handler(android.os.Looper.getMainLooper()))
}

/** The window the view is drawn in: a dialog's own, or the activity's. */
private fun windowOf(view: View): android.view.Window? {
    var v: android.view.ViewParent? = view.parent
    while (v != null) {
        if (v is androidx.compose.ui.window.DialogWindowProvider) return v.window
        v = v.parent
    }
    var c = view.context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c.window
        c = c.baseContext
    }
    return null
}
