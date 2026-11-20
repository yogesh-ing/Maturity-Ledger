package dev.deskdeck.panel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * The whole panel lives in a WebView fed from app assets. Loading it through the asset loader gives
 * it a real https origin, so fetch() works without touching file:// privileges, and no remote
 * content can ever be loaded by accident.
 *
 * The transport and the device token stay native: the panel only asks this activity for its state,
 * its layout, and to pair. A token kept in SharedPreferences never enters JavaScript.
 *
 * Widget state arrives over the agent's SSE stream (`/api/stream`), which pushes only the widgets
 * that changed. When the stream drops, the poller below keeps the connection status honest.
 *
 * Two transports carry the same API: the USB loopback (`adb reverse`, plain HTTP on 127.0.0.1) and
 * the agent's LAN listener (TLS, self-signed). The LAN one is trusted only because the certificate's
 * public key matches the pin this phone learned while pairing over USB; nothing else is accepted.
 */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val polling = AtomicBoolean(false)
    private val streaming = AtomicBoolean(false)
    private var poller: Thread? = null
    private var streamer: Thread? = null

    @Volatile private var reachable = false
    @Volatile private var base: Base? = null
    @Volatile private var pinnedTls: Pair<String, SSLContext>? = null
    @Volatile private var lastError: String? = null
    @Volatile private var lastStatus = ""
    @Volatile private var panelJson: String? = null
    private var lastBatteryReport = 0L
    private var dimmed = false

    private val assets by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val dimTask = Runnable { dim() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this).apply {
            setBackgroundColor(BACKGROUND)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                textZoom = 100
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
            }
            addJavascriptInterface(Bridge(), "DeskDeck")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    // Only the panel's own asset origin is served; anything else is refused.
                    if (request.url.host != ASSET_HOST) return FORBIDDEN
                    val response = assets.shouldInterceptRequest(request.url)
                    if (response != null && request.url.path?.endsWith(".js") == true) {
                        response.mimeType = "text/javascript"
                        response.encoding = "utf-8"
                    }
                    return response ?: FORBIDDEN
                }
            }
        }

        setContentView(web)
        web.loadUrl("https://$ASSET_HOST/assets/panel/index.html")
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        scheduleDim()
        startPolling()
    }

    override fun onPause() {
        stopPolling()
        stopStreaming()
        main.removeCallbacks(dimTask)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onDestroy() {
        stopPolling()
        stopStreaming()
        main.removeCallbacks(dimTask)
        web.destroy()
        super.onDestroy()
    }

    /** Any touch wakes the panel and restarts the idle countdown. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) wake()
        return super.dispatchTouchEvent(event)
    }

    /** What the panel may ask for: state, layout, pairing, forgetting the PC. Nothing else. */
    inner class Bridge {
        @JavascriptInterface
        fun status(): String = statusJson()

        @JavascriptInterface
        fun panel(): String = panelJson ?: ""

        @JavascriptInterface
        fun pair(code: String) {
            requestPair(code)
        }

        @JavascriptInterface
        fun rotate() {
            requestRotate()
        }

        @JavascriptInterface
        fun action(id: String, confirmed: Boolean) {
            requestAction(id, confirmed)
        }

        @JavascriptInterface
        fun unpair() {
            clearPairing()
            lastError = null
            panelJson = null
            stopStreaming()
            synchronized(this@MainActivity) { lastStatus = "" }
            publishStatus()
        }

        @JavascriptInterface
        fun model(): String = modelName()
    }

    private fun requestPair(code: String) {
        val normalized = code.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.length != PAIR_CODE_LENGTH) {
            emitPair("bad_code")
            return
        }
        Thread {
            val body = JSONObject()
                .put("code", normalized)
                .put(
                    "device",
                    JSONObject().put("model", modelName()).put("app", versionName()),
                )
            // Pairing is USB only: there is no way to reach it from the network.
            val reply = call(Base(BASE_URL, null), "POST", "/api/pair/claim", body.toString(), null)
            if (reply == null) {
                emitPair("unreachable")
                return@Thread
            }
            when (reply.code) {
                200 -> {
                    val json = runCatching { JSONObject(reply.body) }.getOrNull()
                    val token = json?.optString("token").orEmpty()
                    if (token.isEmpty()) {
                        emitPair("bad_answer")
                        return@Thread
                    }
                    val device = json?.optJSONObject("device")
                    prefs.edit()
                        .putString(KEY_TOKEN, token)
                        .putString(KEY_DEVICE, device?.optString("id").orEmpty())
                        .putString(KEY_PC, json?.optString("pc").orEmpty())
                        .putString(KEY_CAPS, capsString(device?.optJSONArray("capabilities")))
                        .apply()
                    lastError = null
                    emitPair(null)
                    refreshOnce()
                }
                409, 410 -> emitPair("expired")
                429 -> emitPair("too_many")
                else -> emitPair("bad_code")
            }
        }.start()
    }

    /** One request: returns null when the agent is unreachable, otherwise its status and body. */
    private fun call(target: Base, method: String, path: String, body: String?, token: String?): Reply? {
        var connection: HttpURLConnection? = null
        return try {
            val opened = URL(target.url + path).openConnection() as HttpURLConnection
            connection = opened
            if (opened is HttpsURLConnection) {
                val pin = target.pin ?: return null
                val context = pinnedContext(pin) ?: return null
                opened.sslSocketFactory = context.socketFactory
                // The pin is a stronger check than a hostname: the certificate is self-signed by
                // this agent, and the phone reaches it by address. Nothing else is trusted.
                opened.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            opened.requestMethod = method
            opened.connectTimeout = CONNECT_TIMEOUT_MS
            opened.readTimeout = READ_TIMEOUT_MS
            opened.useCaches = false
            opened.setRequestProperty("accept", "application/json")
            if (token != null) opened.setRequestProperty("authorization", "Bearer $token")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                opened.doOutput = true
                opened.setRequestProperty("content-type", "application/json")
                opened.setFixedLengthStreamingMode(bytes.size)
                opened.outputStream.use { it.write(bytes) }
            }
            val code = opened.responseCode
            val stream = if (code in 200..299) opened.inputStream else opened.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Reply(code, text)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            // A pin mismatch or a certificate the platform refuses: treat it as unreachable.
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * TLS that trusts exactly one key: the SHA-256 of the leaf certificate's SubjectPublicKeyInfo,
     * base64 encoded, exactly as the agent reports it. A new key on the PC means pairing again.
     */
    private fun pinnedContext(pin: String): SSLContext? {
        pinnedTls?.let { cached -> if (cached.first == pin) return cached.second }
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("client certificates are not used")
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("no certificate")
                val digest = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
                val seen = Base64.encodeToString(digest, Base64.NO_WRAP)
                if (!sameSecret(seen, pin)) throw CertificateException("certificate pin mismatch")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = try {
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        } catch (_: Exception) {
            return null
        }
        pinnedTls = pin to context
        return context
    }

    private fun sameSecret(a: String, b: String): Boolean {
        val left = a.toByteArray(Charsets.UTF_8)
        val right = b.toByteArray(Charsets.UTF_8)
        if (left.size != right.size) return false
        var difference = 0
        for (index in left.indices) difference = difference or (left[index].toInt() xor right[index].toInt())
        return difference == 0
    }

    private fun startPolling() {
        if (!polling.compareAndSet(false, true)) return
        val thread = Thread {
            var delay = RETRY_MIN_MS
            while (polling.get()) {
                val healthy = refreshOnce()
                delay = if (healthy) POLL_MS else minOf(delay * 2, RETRY_MAX_MS)
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        thread.isDaemon = true
        thread.name = "deskdeck-transport"
        poller = thread
        thread.start()
    }

    private fun stopPolling() {
        polling.set(false)
        poller?.interrupt()
        poller = null
    }

    /** The transports to try, best first: the LAN listener, then the USB loopback. */
    private fun candidates(): List<Base> {
        val list = ArrayList<Base>(2)
        val url = prefs.getString(KEY_WIFI_URL, null)
        val pin = prefs.getString(KEY_WIFI_PIN, null)
        if (!url.isNullOrEmpty() && !pin.isNullOrEmpty()) list.add(Base(url, pin))
        list.add(Base(BASE_URL, null))
        return list
    }

    /**
     * Picks the transport to use. The one already working is preferred, so a healthy Wi-Fi link is
     * not abandoned for USB on every poll; otherwise each candidate is probed in order.
     */
    private fun resolveBase(): Base? {
        val current = base
        if (current != null && alive(current)) return current
        for (candidate in candidates()) {
            if (!alive(candidate)) continue
            base = candidate
            return candidate
        }
        base = null
        return null
    }

    private fun alive(target: Base): Boolean {
        val health = call(target, "GET", "/api/health", null, null) ?: return false
        return health.code == 200
    }

    /** Polls the agent; returns true when it answered, so retries can back off. */
    private fun refreshOnce(): Boolean {
        val target = resolveBase()
        if (target == null) {
            reachable = false
            publishStatus()
            return false
        }
        reachable = true

        val token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            publishStatus()
            return true
        }

        val session = call(target, "GET", "/api/session", null, token)
        if (session == null) {
            reachable = false
            publishStatus()
            return false
        }
        if (session.code == 401 || session.code == 403) {
            // The PC revoked this phone: forget the credential instead of retrying forever.
            stopStreaming()
            clearPairing()
            lastError = "revoked"
            publishStatus()
            return true
        }
        if (session.code == 200) {
            val json = runCatching { JSONObject(session.body) }.getOrNull()
            val capabilities = capsString(json?.optJSONObject("device")?.optJSONArray("capabilities"))
            val editor = prefs.edit()
                .putString(KEY_PC, json?.optString("pc").orEmpty())
                .putString(KEY_CAPS, capabilities)
            // The agent hands out its LAN address and key pin over an already trusted transport.
            learnWifi(json)?.let { (url, pin) ->
                if (url != prefs.getString(KEY_WIFI_URL, null) || pin != prefs.getString(KEY_WIFI_PIN, null)) {
                    editor.putString(KEY_WIFI_URL, url).putString(KEY_WIFI_PIN, pin)
                }
            }
            editor.apply()
            lastError = null
            loadPanel(target, token)
            reportBattery(target, token)
            startStreaming()
        }
        publishStatus()
        return true
    }

    /**
     * `wifi: {urls, pin}` from the session call. The address already in use is kept when it is still
     * listed, so a working link does not flap between interfaces.
     */
    private fun learnWifi(json: JSONObject?): Pair<String, String>? {
        val wifi = json?.optJSONObject("wifi") ?: return null
        val pin = wifi.optString("pin")
        val urls = wifi.optJSONArray("urls") ?: return null
        if (pin.isEmpty() || urls.length() == 0) return null
        val current = base?.url
        var chosen = urls.optString(0)
        if (current != null) {
            for (index in 0 until urls.length()) {
                if (urls.optString(index) == current) {
                    chosen = current
                    break
                }
            }
        }
        return chosen to pin
    }

    /**
     * The board and the deck reach the panel together. Compared as a whole payload, because the deck
     * can change (an edited `deck.json`) without the layout version moving.
     */
    private fun loadPanel(target: Base, token: String) {
        val reply = call(target, "GET", "/api/panel", null, token) ?: return
        if (reply.code != 200) return
        if (reply.body == panelJson) return
        panelJson = reply.body
        emit("panel", reply.body)
    }

    /** At most once a minute: the phone widget does not need second-level battery precision. */
    private fun reportBattery(target: Base, token: String) {
        val now = System.currentTimeMillis()
        if (now - lastBatteryReport < BATTERY_REPORT_MS) return
        val battery = batteryFields() ?: return
        lastBatteryReport = now
        val body = JSONObject()
            .put(
                "battery",
                JSONObject().put("level", battery.first).put("charging", battery.second),
            )
        call(target, "POST", "/api/device/self", body.toString(), token)
    }

    private fun batteryFields(): Pair<Int, Boolean>? {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return (level * 100 / scale) to charging
    }

    private fun startStreaming() {
        if (prefs.getString(KEY_TOKEN, null) == null) return
        if (!streaming.compareAndSet(false, true)) return
        val thread = Thread {
            var delay = RETRY_MIN_MS
            while (streaming.get()) {
                val token = prefs.getString(KEY_TOKEN, null) ?: break
                val target = base
                if (target != null && readStream(target, token)) {
                    delay = RETRY_MIN_MS
                } else {
                    delay = minOf(delay * 2, RETRY_MAX_MS)
                }
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        thread.isDaemon = true
        thread.name = "deskdeck-stream"
        streamer = thread
        thread.start()
    }

    private fun stopStreaming() {
        streaming.set(false)
        streamer?.interrupt()
        streamer = null
    }

    /**
     * Reads the SSE stream until it ends. Every event is `event: state` with one JSON line, so a
     * blank line is the boundary. Returns false when the agent was unreachable, so the caller backs
     * off instead of hammering a closed port.
     */
    private fun readStream(target: Base, token: String): Boolean {
        var connection: HttpURLConnection? = null
        var answered = false
        try {
            val opened = URL(target.url + "/api/stream").openConnection() as HttpURLConnection
            connection = opened
            if (opened is HttpsURLConnection) {
                val pin = target.pin ?: return false
                val context = pinnedContext(pin) ?: return false
                opened.sslSocketFactory = context.socketFactory
                opened.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            opened.requestMethod = "GET"
            opened.connectTimeout = CONNECT_TIMEOUT_MS
            opened.readTimeout = STREAM_READ_TIMEOUT_MS
            opened.useCaches = false
            opened.setRequestProperty("accept", "text/event-stream")
            opened.setRequestProperty("authorization", "Bearer $token")
            if (opened.responseCode != 200) return opened.responseCode in 400..499
            answered = true
            opened.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var event = ""
                val data = StringBuilder()
                while (streaming.get()) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                        line.isEmpty() -> {
                            if (event == "state" && data.isNotEmpty()) emit("state", data.toString())
                            event = ""
                            data.setLength(0)
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // Timeout or a dropped USB link: the loop reconnects with backoff.
        } finally {
            connection?.disconnect()
        }
        return answered
    }

    private fun clearPairing() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DEVICE)
            .remove(KEY_CAPS)
            .remove(KEY_PC)
            .remove(KEY_WIFI_URL)
            .remove(KEY_WIFI_PIN)
            .apply()
        base = null
        panelJson = null
    }

    /**
     * Replaces the device token in place. The agent forgets the old one immediately, so the new one
     * is committed synchronously before anything else runs; a failed write means pairing again.
     */
    private fun requestRotate() {
        Thread {
            val target = base
            val token = prefs.getString(KEY_TOKEN, null)
            if (target == null || token == null) {
                emitRotate("unreachable")
                return@Thread
            }
            val reply = call(target, "POST", "/api/device/rotate", null, token)
            if (reply == null) {
                emitRotate("unreachable")
                return@Thread
            }
            val fresh = runCatching { JSONObject(reply.body).optString("token") }.getOrNull().orEmpty()
            if (reply.code != 200 || fresh.isEmpty()) {
                emitRotate("failed")
                return@Thread
            }
            val stored = prefs.edit().putString(KEY_TOKEN, fresh).commit()
            emitRotate(if (stored) null else "failed")
        }.start()
    }

    /**
     * Runs one control-deck action. The id is a fixed vocabulary that the agent validates again;
     * this check only keeps arbitrary strings out of the request path.
     */
    private fun requestAction(id: String, confirmed: Boolean) {
        if (!ACTION_ID.matches(id)) {
            emitAction(id, "unknown_action")
            return
        }
        Thread {
            val target = base
            val token = prefs.getString(KEY_TOKEN, null)
            if (target == null || token == null) {
                emitAction(id, "unreachable")
                return@Thread
            }
            // Only a confirmed press carries a body; the agent asks for one before sleep and friends.
            val body = if (confirmed) "{\"confirm\":true}" else null
            val reply = call(target, "POST", "/api/action/$id", body, token)
            if (reply == null) {
                emitAction(id, "unreachable")
                return@Thread
            }
            if (reply.code == 200) {
                emitAction(id, null)
                return@Thread
            }
            val code = runCatching {
                JSONObject(reply.body).optJSONObject("error")?.optString("code")
            }.getOrNull().orEmpty()
            emitAction(id, code.ifEmpty { "failed" })
        }.start()
    }

    private fun emitAction(id: String, error: String?) {
        val json = JSONObject()
        json.put("id", id)
        json.put("ok", error == null)
        json.put("error", error ?: JSONObject.NULL)
        emit("action", json.toString())
    }

    private fun emitRotate(error: String?) {
        val json = JSONObject()
        json.put("ok", error == null)
        json.put("error", error ?: JSONObject.NULL)
        emit("rotate", json.toString())
    }

    @Synchronized
    private fun publishStatus() {
        val json = statusJson()
        if (json == lastStatus) return
        lastStatus = json
        emit("status", json)
    }

    private fun statusJson(): String {
        val root = JSONObject()
        val active = base
        root.put(
            "transport",
            when {
                !reachable || active == null -> "none"
                active.pin != null -> "wifi"
                else -> "usb"
            },
        )
        root.put("paired", prefs.getString(KEY_TOKEN, null) != null)
        root.put("pc", prefs.getString(KEY_PC, null) ?: JSONObject.NULL)
        root.put("device", prefs.getString(KEY_DEVICE, null) ?: JSONObject.NULL)
        root.put("model", modelName())
        root.put("capabilities", capabilityArray())
        root.put("error", lastError ?: JSONObject.NULL)
        return root.toString()
    }

    private fun capsString(array: JSONArray?): String {
        if (array == null) return ""
        val parts = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (value.isNotEmpty()) parts.add(value)
        }
        return parts.joinToString(",")
    }

    private fun capabilityArray(): JSONArray {
        val stored = prefs.getString(KEY_CAPS, "").orEmpty()
        val array = JSONArray()
        for (capability in stored.split(",")) {
            if (capability.isNotBlank()) array.put(capability.trim())
        }
        return array
    }

    private fun emit(type: String, json: String) {
        val script = "window.onDeskDeckEvent&&window.onDeskDeckEvent(${JSONObject.quote(type)},${JSONObject.quote(json)})"
        main.post { web.evaluateJavascript(script, null) }
    }

    private fun emitPair(error: String?) {
        val json = JSONObject()
        json.put("ok", error == null)
        json.put("error", error ?: JSONObject.NULL)
        json.put("pc", prefs.getString(KEY_PC, null) ?: JSONObject.NULL)
        emit("pair", json.toString())
    }

    /** A desk panel is left alone for hours: dim it, then restore brightness on the next touch. */
    private fun scheduleDim() {
        main.removeCallbacks(dimTask)
        val idle = prefs.getLong(KEY_IDLE_MS, IDLE_DIM_MS)
        if (idle > 0) main.postDelayed(dimTask, idle)
    }

    private fun dim() {
        if (dimmed) return
        dimmed = true
        window.attributes = window.attributes.apply { screenBrightness = DIM_BRIGHTNESS }
    }

    private fun wake() {
        if (dimmed) {
            dimmed = false
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        scheduleDim()
    }

    private fun modelName(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }.orEmpty()
        return "$manufacturer ${Build.MODEL}".trim()
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private class Reply(val code: Int, val body: String)

    /** A transport: a base URL plus the key pin every certificate must match (null over USB). */
    private class Base(val url: String, val pin: String?)

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val BACKGROUND = 0xFF0B0F14.toInt()
        const val PREFS = "deskdeck"
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE = "device"
        const val KEY_PC = "pc"
        const val KEY_CAPS = "capabilities"
        const val KEY_IDLE_MS = "idle_dim_ms"
        const val KEY_WIFI_URL = "wifi_url"
        const val KEY_WIFI_PIN = "wifi_pin"
        // adb reverse maps this device port to the same port on the PC.
        const val BASE_URL = "http://127.0.0.1:8741"
        const val PAIR_CODE_LENGTH = 8
        // `media.next`, `mute`, `lock`, and custom ids like `zoom-mute`: the agent validates again.
        val ACTION_ID = Regex("[a-z0-9_-]{1,32}")
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 4_000
        const val STREAM_READ_TIMEOUT_MS = 45_000
        const val POLL_MS = 3_000L
        const val RETRY_MIN_MS = 2_000L
        const val RETRY_MAX_MS = 15_000L
        const val BATTERY_REPORT_MS = 60_000L
        const val IDLE_DIM_MS = 90_000L
        const val DIM_BRIGHTNESS = 0.03f

        val FORBIDDEN: WebResourceResponse
            get() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
    }
}
