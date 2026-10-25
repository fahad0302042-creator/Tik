package com.tiktv.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.app.Activity
import android.app.AlertDialog
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The whole app: one full-screen WebView pointed at TikTok's website, plus a remote-control
 * layer (D-pad feed navigation, virtual mouse, native text entry) that a TV remote needs.
 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var toastText: TextView
    private lateinit var errorPanel: View
    private lateinit var errorText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var cursorMode = false
    private var cursorX = 0f
    private var cursorY = 0f
    private var lastBackPress = 0L
    private var lastStep = 0L
    private var appliedUa: String? = null
    private var pageLoaded = false

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var centerLongPressFired = false
    private val centerLongPress = Runnable {
        centerLongPressFired = true
        setCursorMode(true)
    }

    private val hideToastRunnable = Runnable { toastText.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        web = findViewById(R.id.web)
        cursor = findViewById(R.id.cursor)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.statusText)
        toastText = findViewById(R.id.toastText)
        errorPanel = findViewById(R.id.errorPanel)
        errorText = findViewById(R.id.errorText)

        findViewById<View>(R.id.retryButton).setOnClickListener {
            errorPanel.visibility = View.GONE
            web.loadUrl(prefs.homeUrl)
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        configureWebView(web)
        hideSystemUi()

        if (!handleIntent(intent)) {
            web.loadUrl(prefs.homeUrl)
        }

        if (prefs.startWithCursor) {
            root.post { setCursorMode(true, announce = false) }
        }

        if (!prefs.seenHelp) {
            root.postDelayed({ showHelp(true) }, 1200)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri = intent?.data ?: return false
        val scheme = data.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            web.loadUrl(data.toString())
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) return
        applyPrefs()
        hideSystemUi()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            root.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ---------------------------------------------------------------- WebView setup

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        view.setBackgroundColor(Color.BLACK)
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        val ua = currentUserAgent()
        appliedUa = ua

        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = ua
            allowFileAccess = false
            allowContentAccess = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        view.addJavascriptInterface(Bridge(), "TvBridge")
        view.webViewClient = makeWebViewClient()
        view.webChromeClient = makeChromeClient()
    }

    private fun currentUserAgent(): String =
        if (prefs.mobileSite) Prefs.MOBILE_UA else Prefs.DESKTOP_UA

    private fun makeWebViewClient(): WebViewClient =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ClientApi26() else BaseClient()

    /**
     * Everything a WebView client normally needs. Split from the API 26 subclass because
     * `RenderProcessGoneDetail` does not exist on older TVs — referencing it in a class that
     * old Android has to verify can crash at load time.
     */
    private open inner class BaseClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = guardUrl(request.url)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            guardUrl(Uri.parse(url))

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            pageLoaded = false
            errorPanel.visibility = View.GONE
            showLoading(true)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            pageLoaded = true
            showLoading(false)
            view.requestFocus()
            injectJs()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                showLoading(false)
                errorText.text = getString(R.string.error_title) + "\n" + error.description
                errorPanel.visibility = View.VISIBLE
            }
        }
    }

    /** Recreates the WebView instead of taking the whole app down when the renderer dies. */
    private inner class ClientApi26 : BaseClient() {

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            toast("The browser engine crashed, reloading TikTok")
            rebuildWebView()
            return true
        }
    }

    /** Blocks app-store / intent links that would otherwise leave the app. */
    private fun guardUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme != "http" && scheme != "https") {
            toast(getString(R.string.blocked_link))
            return true
        }
        return false
    }

    private fun makeChromeClient(): WebChromeClient = object : WebChromeClient() {

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden()
                return
            }
            fullscreenView = view
            fullscreenCallback = callback
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            hideSystemUi()
        }

        override fun onHideCustomView() {
            hideFullscreen()
        }

        /** Login and share pop-ups: show them in the main WebView instead of a new window. */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val popup = WebView(this@MainActivity)
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    web.loadUrl(request.url.toString())
                    return true
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean {
                    web.loadUrl(url)
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Camera / microphone are never needed for watching.
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: android.webkit.GeolocationPermissions.Callback
        ) {
            callback.invoke(origin, false, false)
        }
    }

    private fun hideFullscreen() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        web.visibility = View.VISIBLE
        web.requestFocus()
    }

    private fun rebuildWebView() {
        val url = try {
            web.url
        } catch (t: Throwable) {
            null
        } ?: prefs.homeUrl

        try {
            root.removeView(web)
            web.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "rebuild", t)
        }

        web = WebView(this)
        root.addView(
            web,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        configureWebView(web)
        web.loadUrl(url)
    }

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        statusText.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- JS bridge

    /** Public on purpose: WebView reaches these methods through reflection. */
    inner class Bridge {

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "js: $message")
        }

        @JavascriptInterface
        fun requestText(id: String, hint: String, current: String, isPassword: Boolean) {
            runOnUiThread { showTextDialog(id, hint, current, isPassword) }
        }

        @JavascriptInterface
        fun onToast(message: String) {
            runOnUiThread { toast(message) }
        }
    }

    private fun injectJs() {
        val script = try {
            assets.open("tv.js").bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            Log.w(TAG, "could not read tv.js", t)
            ""
        }
        if (script.isEmpty()) return
        web.evaluateJavascript(script) { applyJsConfig() }
    }

    private fun applyJsConfig() {
        val cfg = JSONObject()
        cfg.put("autoplayNext", prefs.autoplayNext)
        val script = "if(window.Tv){try{Tv.config(${JSONObject.quote(cfg.toString())});" +
            "Tv.cinema(${prefs.cinema});}catch(e){}}"
        web.evaluateJavascript(script, null)
    }

    private fun js(code: String, needsVideo: Boolean = false) {
        if (!pageLoaded) {
            if (needsVideo) toast(getString(R.string.no_video))
            return
        }
        web.evaluateJavascript(
            "try{if(window.Tv){$code}}catch(e){if(window.TvBridge)TvBridge.log('err '+e)}",
            null
        )
    }

    private fun applyPrefs() {
        val ua = currentUserAgent()
        val changed = appliedUa != null && appliedUa != ua
        appliedUa = ua
        web.settings.userAgentString = ua
        if (changed) {
            web.loadUrl(web.url ?: prefs.homeUrl)
        } else {
            applyJsConfig()
        }
    }

    // ---------------------------------------------------------------- key handling

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val repeat = event.repeatCount > 0

        if (fullscreenView != null) {
            if (code == KeyEvent.KEYCODE_BACK && down) {
                hideFullscreen()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        when (code) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(event)

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (down && !repeat) js("Tv.toggle()", needsVideo = true)
                return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (down && !repeat) stepVideo(1)
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (down && !repeat) stepVideo(-1)
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (down && !repeat) js("Tv.play()")
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (down && !repeat) js("Tv.pause()")
                return true
            }

            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_SETTINGS -> {
                if (down && !repeat) showAppMenu()
                return true
            }

            KeyEvent.KEYCODE_SEARCH -> {
                if (down && !repeat) showSearchDialog()
                return true
            }

            KeyEvent.KEYCODE_PROG_RED -> {
                if (down && !repeat) setCursorMode(!cursorMode)
                return true
            }

            KeyEvent.KEYCODE_PROG_GREEN -> {
                if (down && !repeat) toggleCinema()
                return true
            }

            KeyEvent.KEYCODE_PROG_YELLOW -> {
                if (down && !repeat) js("Tv.mute()", needsVideo = true)
                return true
            }

            KeyEvent.KEYCODE_PROG_BLUE -> {
                if (down && !repeat) showSearchDialog()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (down && !repeat) handleBack()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> return handleCenter(event)

            KeyEvent.KEYCODE_DPAD_UP -> return handleVertical(event, -1)

            KeyEvent.KEYCODE_DPAD_DOWN -> return handleVertical(event, 1)

            KeyEvent.KEYCODE_DPAD_LEFT -> return handleHorizontal(event, -1)

            KeyEvent.KEYCODE_DPAD_RIGHT -> return handleHorizontal(event, 1)
        }

        return super.dispatchKeyEvent(event)
    }

    private fun handleCenter(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            centerLongPressFired = false
            handler.postDelayed(centerLongPress, LONG_PRESS_MS)
        } else {
            handler.removeCallbacks(centerLongPress)
            if (!centerLongPressFired) {
                if (cursorMode) {
                    clickAtCursor()
                } else {
                    js("Tv.toggle()", needsVideo = true)
                }
            }
        }
        return true
    }

    private fun handleVertical(event: KeyEvent, direction: Int): Boolean {
        if (cursorMode) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val moved = moveCursor(0f, direction * cursorStep(event.repeatCount).toFloat())
                // Holding up/down at the edge scrolls the page, like a mouse at the screen border.
                if (!moved && event.repeatCount > 4) {
                    web.scrollBy(0, direction * dp(60))
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            stepVideo(direction)
        }
        return true
    }

    private fun handleHorizontal(event: KeyEvent, direction: Int): Boolean {
        if (cursorMode) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                moveCursor(direction * cursorStep(event.repeatCount).toFloat(), 0f)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            js("Tv.seek(${direction * 10})", needsVideo = true)
        }
        return true
    }

    /** Throttled so that a held D-pad button doesn't fly through the whole feed. */
    private fun stepVideo(direction: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastStep < STEP_THROTTLE_MS) return
        lastStep = now
        if (direction > 0) js("Tv.next()", needsVideo = true) else js("Tv.prev()", needsVideo = true)
    }

    private fun handleBack() {
        if (fullscreenView != null) {
            hideFullscreen()
            return
        }
        if (cursorMode) {
            setCursorMode(false)
            return
        }
        if (web.canGoBack()) {
            web.goBack()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackPress < 2000L) {
            finish()
            return
        }
        lastBackPress = now
        toast(getString(R.string.exit_hint))
    }

    // ---------------------------------------------------------------- virtual mouse

    private fun setCursorMode(on: Boolean, announce: Boolean = true) {
        cursorMode = on
        cursor.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            if (cursorX <= 0f && cursorY <= 0f) {
                cursorX = (root.width / 2).toFloat()
                cursorY = (root.height / 2).toFloat()
            }
            clampCursor()
            cursor.x = cursorX
            cursor.y = cursorY
        }
        if (announce) {
            toast(getString(if (on) R.string.cursor_on else R.string.cursor_off))
        }
    }

    private fun cursorStep(repeatCount: Int): Int {
        val base = when (prefs.cursorSpeed) {
            0 -> 8
            2 -> 26
            else -> 15
        }
        val accel = 1f + (repeatCount.coerceAtMost(10) * 0.28f)
        return (base * accel).toInt().coerceAtLeast(base)
    }

    /** Returns true when the cursor actually moved (false means it is pinned to an edge). */
    private fun moveCursor(dx: Float, dy: Float): Boolean {
        cursorX += dx
        cursorY += dy
        val before = cursorX to cursorY
        clampCursor()
        cursor.x = cursorX
        cursor.y = cursorY
        return before.first != cursorX || before.second != cursorY
    }

    private fun clampCursor() {
        val maxX = (root.width - cursor.width).coerceAtLeast(0).toFloat()
        val maxY = (root.height - cursor.height).coerceAtLeast(0).toFloat()
        cursorX = cursorX.coerceIn(0f, maxX)
        cursorY = cursorY.coerceIn(0f, maxY)
    }

    private fun clickAtCursor() {
        val x = cursorX + cursor.width / 2f
        val y = cursorY + cursor.height / 2f
        clickAt(x, y)
    }

    private fun clickAt(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        web.dispatchTouchEvent(down)
        down.recycle()
        web.postDelayed({
            val upTime = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            web.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    // ---------------------------------------------------------------- app menu

    private fun showAppMenu() {
        val items = arrayOf(
            getString(R.string.menu_play_pause),
            getString(R.string.menu_next),
            getString(R.string.menu_previous),
            getString(R.string.menu_rewind),
            getString(R.string.menu_forward),
            cursorModeLabel(),
            cinemaLabel(),
            getString(R.string.menu_mute),
            getString(R.string.menu_search),
            getString(R.string.menu_open_link),
            getString(R.string.menu_home),
            getString(R.string.menu_sign_in),
            getString(R.string.menu_reload),
            getString(R.string.menu_settings),
            getString(R.string.menu_help),
            getString(R.string.menu_diagnostics),
            getString(R.string.menu_exit)
        )
        AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.menu_title)
            .setItems(items) { _, which -> onMenuChoice(which) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cursorModeLabel(): String =
        if (cursorMode) getString(R.string.menu_cursor_on) else getString(R.string.menu_cursor_off)

    private fun cinemaLabel(): String =
        if (prefs.cinema) getString(R.string.menu_cinema_on) else getString(R.string.menu_cinema_off)

    private fun onMenuChoice(which: Int) {
        when (which) {
            0 -> js("Tv.toggle()", needsVideo = true)
            1 -> stepVideo(1)
            2 -> stepVideo(-1)
            3 -> js("Tv.seek(-10)", needsVideo = true)
            4 -> js("Tv.seek(10)", needsVideo = true)
            5 -> setCursorMode(!cursorMode)
            6 -> toggleCinema()
            7 -> js("Tv.mute()", needsVideo = true)
            8 -> showSearchDialog()
            9 -> showOpenLinkDialog()
            10 -> web.loadUrl(prefs.homeUrl)
            11 -> {
                web.loadUrl(LOGIN_URL)
                setCursorMode(true)
            }
            12 -> web.reload()
            13 -> startActivity(Intent(this, SettingsActivity::class.java))
            14 -> showHelp(false)
            15 -> showDiagnostics()
            else -> finish()
        }
    }

    /** Shows what the injected script can currently see on the page — handy when TikTok changes its DOM. */
    private fun showDiagnostics() {
        if (!pageLoaded) {
            toast(getString(R.string.diagnostics_busy))
            return
        }
        web.evaluateJavascript(
            "(function(){try{return window.Tv?Tv.diag():'tv.js not injected';}catch(e){return 'error: '+e;}})()"
        ) { raw ->
            val text = try {
                JSONTokener(raw).nextValue()?.toString() ?: raw
            } catch (t: Throwable) {
                raw
            }
            AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
                .setTitle(R.string.diagnostics_title)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun toggleCinema() {
        prefs.cinema = !prefs.cinema
        js("Tv.cinema(${prefs.cinema})")
        toast(getString(if (prefs.cinema) R.string.cinema_on else R.string.cinema_off))
    }

    private fun showSearchDialog() {
        askText(getString(R.string.search_hint), "", "") { query ->
            if (query.isNotBlank()) {
                web.loadUrl("https://www.tiktok.com/search?q=" + Uri.encode(query))
            }
        }
    }

    private fun showOpenLinkDialog() {
        askText(getString(R.string.open_url_hint), "", "") { raw ->
            var url = raw.trim()
            if (url.isEmpty()) return@askText
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            web.loadUrl(url)
        }
    }

    // ---------------------------------------------------------------- dialogs

    /**
     * TV remotes cannot type into a web page directly, so any <input> the page focuses is
     * mirrored into this native dialog and the result is written back into the page.
     */
    private fun showTextDialog(id: String, hint: String, current: String, isPassword: Boolean) {
        if (isFinishing || isDestroyed) return

        val input = EditText(this)
        input.setText(current)
        input.setSelection(input.text?.length ?: 0)
        if (hint.isNotEmpty()) input.hint = hint
        input.inputType = if (isPassword) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }

        val container = FrameLayout(this)
        container.setPadding(dp(24), dp(8), dp(24), 0)
        container.addView(
            input,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(hint.ifEmpty { getString(R.string.app_name) })
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text?.toString() ?: ""
                js("Tv.setText(${JSONObject.quote(id)}, ${JSONObject.quote(value)});")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
    }

    private fun askText(
        title: String,
        hint: String,
        initial: String,
        onOk: (String) -> Unit
    ) {
        val input = EditText(this)
        input.setText(initial)
        input.setSelection(input.text?.length ?: 0)
        if (hint.isNotEmpty()) input.hint = hint
        input.inputType = InputType.TYPE_CLASS_TEXT

        val container = FrameLayout(this)
        container.setPadding(dp(24), dp(8), dp(24), 0)
        container.addView(
            input,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onOk(input.text?.toString() ?: "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
    }

    private fun showHelp(firstRun: Boolean) {
        val builder = AlertDialog.Builder(this, R.style.Theme_TikTV_Dialog)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_body)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (firstRun) {
                    prefs.seenHelp = true
                }
            }
            .setCancelable(!firstRun)

        if (firstRun) {
            builder.setNegativeButton(R.string.menu_settings) { _, _ ->
                prefs.seenHelp = true
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        builder.show()
    }

    // ---------------------------------------------------------------- helpers

    private fun toast(message: String) {
        toastText.text = message
        toastText.visibility = View.VISIBLE
        handler.removeCallbacks(hideToastRunnable)
        handler.postDelayed(hideToastRunnable, 1800)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    companion object {
        private const val TAG = "TikTV"
        private const val LOGIN_URL = "https://www.tiktok.com/login"
        private const val LONG_PRESS_MS = 600L
        private const val STEP_THROTTLE_MS = 450L
    }
}
