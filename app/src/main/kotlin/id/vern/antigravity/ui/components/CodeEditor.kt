package id.vern.antigravity.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import org.json.JSONObject
import id.vern.antigravity.R
import id.vern.antigravity.data.OpenTab
import id.vern.antigravity.ui.theme.AntigravityAmber
import id.vern.antigravity.ui.theme.AntigravityBlue
import java.io.File

private class PreviewWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {
    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        isScrollbarFadingEnabled = true
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun CodeEditor(
    tab: OpenTab,
    onSaveFile: (File, String) -> Unit,
    onCloseTab: () -> Unit,
    onToggleDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textContent by remember(tab.id) { mutableStateOf(tab.content) }
    var history by remember(tab.id) { mutableStateOf(listOf(tab.content)) }
    var historyIndex by remember(tab.id) { mutableStateOf(0) }

    val ext = tab.file.extension.lowercase()
    val isMarkdown = ext in listOf("md", "markdown")
    val isHtml = ext in listOf("html", "htm")
    val isSvg = ext == "svg"
    val isImage = ext in listOf("png", "jpg", "jpeg", "webp", "gif", "ico", "bmp")
    val isPreviewable = isMarkdown || isHtml || isSvg || isImage

    var isPreviewMode by remember(tab.id) { mutableStateOf(isPreviewable) }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val markedJs = remember {
        try {
            context.assets.open("marked.min.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    val isDirty = textContent != tab.originalContent
    val lines = textContent.split("\n")
    val lineCount = lines.size
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val blockSheetScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(blockSheetScrollConnection)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isDirty) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "●",
                                color = AntigravityAmber,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Text(
                        text = if (isPreviewMode && isPreviewable) stringResource(R.string.editor_mode_preview) else stringResource(R.string.editor_lines_count, lineCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPreviewable) {
                        Button(
                            onClick = { isPreviewMode = !isPreviewMode },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPreviewMode) AntigravityBlue else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isPreviewMode) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewMode) Icons.Default.Code else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPreviewMode) stringResource(R.string.editor_mode_raw) else stringResource(R.string.editor_mode_preview),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isPreviewMode && tab.originalContent.isNotBlank() && tab.originalContent != textContent) {
                        IconButton(
                            onClick = onToggleDiff,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Difference,
                                contentDescription = stringResource(R.string.editor_btn_diff),
                                modifier = Modifier.size(18.dp),
                                tint = AntigravityBlue
                            )
                        }
                    }

                    if (!isPreviewMode) {
                        IconButton(
                            onClick = {
                                if (historyIndex > 0) {
                                    historyIndex--
                                    textContent = history[historyIndex]
                                }
                            },
                            enabled = historyIndex > 0,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.editor_btn_undo),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (historyIndex < history.size - 1) {
                                    historyIndex++
                                    textContent = history[historyIndex]
                                }
                            },
                            enabled = historyIndex < history.size - 1,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.editor_btn_redo),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveFile(tab.file, textContent)
                            tab.content = textContent
                            tab.originalContent = textContent
                        },
                        enabled = isDirty,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_btn_save), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedContent(
            targetState = isPreviewMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            },
            label = "EditorPreviewContent",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { inPreview ->
            if (inPreview && isPreviewable) {
                when {
                    isMarkdown -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isDark) Color(0xFF0D1117) else Color.White)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewWebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = true
                                        settings.allowContentAccess = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.builtInZoomControls = false
                                        settings.displayZoomControls = false
                                        setOnTouchListener { v, event ->
                                            when (event.actionMasked) {
                                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                                }
                                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                                }
                                            }
                                            false
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                val url = request?.url?.toString() ?: return false
                                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                                    try {
                                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                        return true
                                                    } catch (e: Exception) {}
                                                }
                                                return false
                                            }
                                        }
                                    }
                                },
                                update = { webView ->
                                    val contentKey = "${tab.id}_${tab.file.lastModified()}_${textContent.hashCode()}_$isDark"
                                    if (webView.tag != contentKey) {
                                        webView.tag = contentKey
                                        val escapedMarkdown = JSONObject.quote(textContent)
                                        val htmlData = """
                                            <!DOCTYPE html>
                                            <html>
                                            <head>
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                                            <style>
                                              :root { color-scheme: ${if (isDark) "dark" else "light"}; }
                                              html, body {
                                                margin: 0;
                                                padding: 0;
                                                min-height: 100%;
                                                overflow-x: hidden;
                                                overflow-y: auto;
                                                -webkit-overflow-scrolling: touch;
                                              }
                                              body {
                                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                                padding: 16px 16px 80px 16px;
                                                line-height: 1.6;
                                                background-color: ${if (isDark) "#0D1117" else "#FFFFFF"};
                                                color: ${if (isDark) "#E6EDF3" else "#1F2328"};
                                                word-wrap: break-word;
                                              }
                                              h1, h2, h3, h4, h5, h6 {
                                                margin-top: 24px;
                                                margin-bottom: 16px;
                                                font-weight: 600;
                                                line-height: 1.25;
                                                border-bottom: ${if (isDark) "1px solid #21262D" else "1px solid #D0D7DE"};
                                                padding-bottom: 0.3em;
                                              }
                                              a { color: #2F81F7; text-decoration: none; }
                                              a:hover { text-decoration: underline; }
                                              code {
                                                padding: 0.2em 0.4em;
                                                margin: 0;
                                                font-size: 85%;
                                                background-color: ${if (isDark) "#161B22" else "#AFB8C133"};
                                                border-radius: 6px;
                                                font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
                                              }
                                              pre {
                                                padding: 16px;
                                                overflow: auto;
                                                font-size: 85%;
                                                line-height: 1.45;
                                                background-color: ${if (isDark) "#161B22" else "#F6F8FA"};
                                                border-radius: 6px;
                                              }
                                              pre code { padding: 0; margin: 0; background: transparent; }
                                              blockquote {
                                                padding: 0 1em;
                                                color: ${if (isDark) "#7D8590" else "#656D76"};
                                                border-left: 0.25em solid ${if (isDark) "#30363D" else "#D0D7DE"};
                                                margin: 0 0 16px 0;
                                              }
                                              table {
                                                border-spacing: 0;
                                                border-collapse: collapse;
                                                margin-top: 0;
                                                margin-bottom: 16px;
                                                width: 100%;
                                                overflow: auto;
                                                display: block;
                                              }
                                              table th, table td {
                                                padding: 6px 13px;
                                                border: 1px solid ${if (isDark) "#30363D" else "#D0D7DE"};
                                              }
                                              table tr:nth-child(2n) {
                                                background-color: ${if (isDark) "#161B22" else "#F6F8FA"};
                                              }
                                              img { max-width: 100%; height: auto; box-sizing: content-box; }
                                              hr {
                                                height: 0.25em;
                                                padding: 0;
                                                margin: 24px 0;
                                                background-color: ${if (isDark) "#30363D" else "#D0D7DE"};
                                                border: 0;
                                              }
                                              ul, ol { padding-left: 2em; }
                                            </style>
                                            <script>
                                            $markedJs
                                            </script>
                                            </head>
                                            <body>
                                            <div id="content"></div>
                                            <script>
                                              try {
                                                const raw = $escapedMarkdown;
                                                document.getElementById('content').innerHTML = (typeof marked !== 'undefined' && marked.parse) ? marked.parse(raw) : raw;
                                              } catch (e) {
                                                document.getElementById('content').innerText = e.message;
                                              }
                                            </script>
                                            </body>
                                            </html>
                                        """.trimIndent()
                                        val baseUrl = tab.file.parentFile?.let { "file://${it.absolutePath}/" }
                                        webView.loadDataWithBaseURL(baseUrl, htmlData, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    isHtml || isSvg -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewWebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = true
                                        settings.allowContentAccess = true
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.builtInZoomControls = false
                                        settings.displayZoomControls = false
                                        setOnTouchListener { v, event ->
                                            when (event.actionMasked) {
                                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                                }
                                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                                }
                                            }
                                            false
                                        }
                                        webViewClient = WebViewClient()
                                    }
                                },
                                update = { webView ->
                                    val contentKey = "${tab.id}_${tab.file.lastModified()}_${textContent.hashCode()}"
                                    if (webView.tag != contentKey) {
                                        webView.tag = contentKey
                                        val mimeType = if (isSvg) "image/svg+xml" else "text/html"
                                        val baseUrl = tab.file.parentFile?.let { "file://${it.absolutePath}/" }
                                        webView.loadDataWithBaseURL(
                                            baseUrl,
                                            textContent,
                                            mimeType,
                                            "UTF-8",
                                            null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    isImage -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D1117)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = tab.file,
                                contentDescription = tab.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(text = textContent, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D1117))
                ) {
                    Column(
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                            .verticalScroll(vScroll)
                            .background(Color(0xFF161B22))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = lineNumbersText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF6E7681),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll)
                            .padding(vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = textContent,
                            onValueChange = { newText ->
                                textContent = newText
                                if (history.isEmpty() || history.last() != newText) {
                                    history = history.take(historyIndex + 1) + newText
                                    historyIndex = history.size - 1
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFE6EDF3),
                                lineHeight = 18.sp
                            ),
                            visualTransformation = SyntaxHighlighterTransformation(tab.file.extension),
                            cursorBrush = SolidColor(AntigravityBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isPreviewMode,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = Color(0xFF161B22),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val symbols = listOf("Tab", "{", "}", "(", ")", "[", "]", "\"", "'", ":", ";", "=", "->", ".", "$")
                    symbols.forEach { sym ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF21262D),
                            modifier = Modifier.clickable {
                                val insertion = if (sym == "Tab") "    " else sym
                                textContent += insertion
                            }
                        ) {
                            Text(
                                text = sym,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC9D1D9),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

class SyntaxHighlighterTransformation(private val ext: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedString {
            val str = text.text
            append(str)

            val keywords = when (ext.lowercase()) {
                "kt", "kts" -> listOf("fun", "val", "var", "class", "object", "interface", "package", "import", "return", "if", "else", "when", "for", "while", "override", "private", "public", "suspend", "data")
                "py" -> listOf("def", "class", "import", "from", "return", "if", "elif", "else", "for", "while", "try", "except", "with", "as", "lambda", "yield", "async", "await")
                "js", "ts" -> listOf("function", "const", "let", "var", "class", "import", "export", "return", "if", "else", "switch", "case", "for", "while", "async", "await", "default")
                else -> listOf("fun", "def", "function", "class", "import", "return", "if", "else")
            }

            keywords.forEach { kw ->
                val regex = "\\b$kw\\b".toRegex()
                regex.findAll(str).forEach { match ->
                    addStyle(
                        style = SpanStyle(color = Color(0xFFFF7B72), fontWeight = FontWeight.Bold),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }

            val stringRegex = "\"([^\"]*)\"|'([^']*)'".toRegex()
            stringRegex.findAll(str).forEach { match ->
                addStyle(
                    style = SpanStyle(color = Color(0xFFA5D6FF)),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            val commentRegex = "(//[^\n]*|#[^\n]*)".toRegex()
            commentRegex.findAll(str).forEach { match ->
                addStyle(
                    style = SpanStyle(color = Color(0xFF8B949E)),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

