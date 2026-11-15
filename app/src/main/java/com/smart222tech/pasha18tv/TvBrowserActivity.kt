package com.smart222tech.pasha18tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * TV browser for user-authorized HTTP(S) websites.
 *
 * The D-pad moves a visible pointer; OK dispatches a click at that position.
 * It deliberately does not bypass DRM, authentication, paywalls or site controls.
 */
class TvBrowserActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_URL = "com.smart222tech.pashxxx.browser.START_URL"
    }

    private lateinit var root: FrameLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var addressField: EditText
    private lateinit var webView: WebView
    private lateinit var cursor: CursorOverlay

    private var pointerX = 0f
    private var pointerY = 0f
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val toolbarHeight: Int
        get() = dp(58)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setBackgroundColor(Color.rgb(20, 27, 39))
        }

        addressField = EditText(this).apply {
            hint = "URL разрешённого сайта (https://...)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 16f
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.rgb(37, 47, 64))
        }

        val goButton = Button(this).apply {
            text = "ОТКРЫТЬ"
            contentDescription = "Открыть введённый адрес"
            setOnClickListener { loadAddress() }
        }

        toolbar.addView(
            addressField,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
        toolbar.addView(
            goButton,
            LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.MATCH_PARENT)
        )

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                mediaPlaybackRequiresUserGesture = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    showMessage("Разрешены только HTTP и HTTPS адреса")
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    if (!addressField.hasFocus()) addressField.setText(url)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        showMessage("Не удалось открыть страницу: " + error.description)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    if (request.isForMainFrame) {
                        showMessage("Сайт вернул HTTP " + errorResponse.statusCode)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: android.net.http.SslError
                ) {
                    handler.cancel()
                    showMessage("Ошибка сертификата сайта. Соединение остановлено.")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenView != null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    toolbar.visibility = View.GONE
                    webView.visibility = View.GONE
                    (view.parent as? ViewGroup)?.removeView(view)
                    root.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    cursor.bringToFront()
                }

                override fun onHideCustomView() {
                    exitFullscreen()
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    request.deny()
                }
            }

            setDownloadListener { _, _, _, _, _ ->
                showMessage("Загрузка файлов из браузера отключена")
            }
        }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { topMargin = toolbarHeight }
        )
        root.addView(
            toolbar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                toolbarHeight,
                Gravity.TOP
            )
        )

        cursor = CursorOverlay(this)
        root.addView(
            cursor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        addressField.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                loadAddress()
                true
            } else {
                false
            }
        }

        root.post {
            pointerX = root.width / 2f
            pointerY = root.height / 2f
            cursor.updatePointer(pointerX, pointerY)
            root.requestFocus()
        }

        val startUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()
        if (startUrl.isNotBlank()) {
            addressField.setText(startUrl)
            loadAddress()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    requestHtml5Playback(fullscreen = true, toggle = false)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    requestHtml5Playback(fullscreen = true, toggle = true)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    pauseHtml5Video()
                    return true
                }
            }

            if (!addressField.hasFocus()) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { movePointer(0, -1); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { movePointer(0, 1); return true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { movePointer(-1, 0); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { movePointer(1, 0); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        clickAtPointer()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun movePointer(horizontal: Int, vertical: Int) {
        val step = dp(30).toFloat()
        pointerX = (pointerX + horizontal * step).coerceIn(dp(14).toFloat(), (root.width - dp(14)).coerceAtLeast(dp(14)).toFloat())
        pointerY = (pointerY + vertical * step).coerceIn(dp(14).toFloat(), (root.height - dp(14)).coerceAtLeast(dp(14)).toFloat())
        cursor.updatePointer(pointerX, pointerY)
    }

    private fun clickAtPointer() {
        val target = when {
            fullscreenView != null -> fullscreenView
            pointerY < toolbarHeight -> toolbar
            else -> webView
        } ?: return

        val localX = pointerX - target.left
        val localY = pointerY - target.top
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY, 0
        )
        val up = MotionEvent.obtain(
            downTime, downTime + 70, MotionEvent.ACTION_UP, localX, localY, 0
        )
        target.dispatchTouchEvent(down)
        target.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun loadAddress() {
        val raw = addressField.text.toString().trim()
        if (raw.isBlank()) {
            showMessage("Введи адрес разрешённого сайта")
            return
        }

        val candidate = if (raw.contains("://")) raw else "https://" + raw
        val uri = runCatching { Uri.parse(candidate) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || uri.host.isNullOrBlank() || (scheme != "http" && scheme != "https")) {
            showMessage("Некорректный адрес. Используй HTTP или HTTPS.")
            return
        }

        addressField.setText(uri.toString())
        (getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(addressField.windowToken, 0)
        root.requestFocus()
        webView.loadUrl(uri.toString())
    }

    private fun requestHtml5Playback(fullscreen: Boolean, toggle: Boolean) {
        val script = if (toggle) {
            "(function(){var v=document.querySelector('video');if(!v)return 'NO_HTML5_VIDEO';if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});if(v.requestFullscreen){var f=v.requestFullscreen();if(f&&f.catch)f.catch(function(){});}else if(v.webkitEnterFullscreen){v.webkitEnterFullscreen();}}else{v.pause();}return 'OK';})()"
        } else {
            "(function(){var v=document.querySelector('video');if(!v)return 'NO_HTML5_VIDEO';var p=v.play();if(p&&p.catch)p.catch(function(){});if(v.requestFullscreen){var f=v.requestFullscreen();if(f&&f.catch)f.catch(function(){});}else if(v.webkitEnterFullscreen){v.webkitEnterFullscreen();}return 'PLAY_REQUESTED';})()"
        }

        webView.evaluateJavascript(script) { result ->
            if (result?.contains("NO_HTML5_VIDEO") == true) {
                showMessage("На странице не найден HTML5-видеоплеер")
            }
        }
    }

    private fun pauseHtml5Video() {
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v){v.pause();return 'PAUSED';}return 'NO_HTML5_VIDEO';})()",
            null
        )
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        toolbar.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
        cursor.bringToFront()
        root.requestFocus()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private class CursorOverlay(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private var pointerX = 0f
        private var pointerY = 0f
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val crosshair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 1.5f * density
        }
        private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 107, 69)
            style = Paint.Style.FILL
        }

        init {
            isClickable = false
            isFocusable = false
            setWillNotDraw(false)
        }

        fun updatePointer(x: Float, y: Float) {
            pointerX = x
            pointerY = y
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = 15f * density
            canvas.drawCircle(pointerX, pointerY, radius, ring)
            canvas.drawLine(pointerX - radius - 5f * density, pointerY, pointerX + radius + 5f * density, pointerY, crosshair)
            canvas.drawLine(pointerX, pointerY - radius - 5f * density, pointerX, pointerY + radius + 5f * density, crosshair)
            canvas.drawCircle(pointerX, pointerY, 3.5f * density, center)
        }
    }
}
