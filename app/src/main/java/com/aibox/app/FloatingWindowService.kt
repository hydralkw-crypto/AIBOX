package com.aibox.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * خدمة النافذة العائمة.
 * تحوّل AIBOX إلى نافذة صغيرة تعرض الموقع الرسمي للخدمة (TikTok أو Spotify)
 * داخل WebView، مع دعم: السحب، التكبير/التصغير (تغيير الحجم)، التصغير لأيقونة،
 * إعادة الفتح، الإغلاق، وحفظ آخر مكان وحجم.
 */
class FloatingWindowService : Service() {

    companion object {
        const val EXTRA_TARGET = "extra_target"
        const val ACTION_MINIMIZE = "com.aibox.app.ACTION_MINIMIZE_FLOATING"
        const val ACTION_CLOSE = "com.aibox.app.ACTION_CLOSE_FLOATING"
        const val RUNNING_PREFS = "aibox_service_state"
        const val KEY_RUNNING = "is_running"
        private const val CHANNEL_ID = "aibox_floating_channel"
        private const val NOTIF_ID = 1
        private const val PREFS = "aibox_floating_prefs"
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var bubbleView: View? = null
    private var currentWebView: WebView? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var target: ServiceTarget

    // يستقبل أوامر التصغير/الإغلاق المُرسلة من شاشة Settings حتى تقدر تتحكم
    // بالنافذة العائمة من داخل AIBOX نفسه، مو بس من شريطها العلوي
    private val controlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CLOSE -> stopSelf()
                ACTION_MINIMIZE -> floatingView?.let { minimizeFromExternal() }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_MINIMIZE)
            addAction(ACTION_CLOSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }
        setRunningFlag(true)
    }

    private fun setRunningFlag(running: Boolean) {
        getSharedPreferences(RUNNING_PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, running).apply()
    }

    private fun minimizeFromExternal() {
        val root = floatingView ?: return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        minimize(params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetName = intent?.getStringExtra(EXTRA_TARGET) ?: ServiceTarget.TIKTOK.name
        target = ServiceTarget.valueOf(targetName)

        startForeground(NOTIF_ID, buildNotification())

        if (floatingView == null) showFloatingWindow()
        return START_NOT_STICKY
    }

    // ---------- بناء النافذة العائمة ----------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_window, null)
        floatingView = view

        val savedW = prefs.getInt("${target.name}_w", dp(360))
        val savedH = prefs.getInt("${target.name}_h", dp(560))
        val savedX = prefs.getInt("${target.name}_x", 0)
        val savedY = prefs.getInt("${target.name}_y", dp(100))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = savedX
        params.y = savedY

        view.findViewById<TextView>(R.id.serviceNameLabel).text = target.label

        val contentFrame = view.findViewById<FrameLayout>(R.id.contentFrame)
        contentFrame.layoutParams = contentFrame.layoutParams.apply {
            width = savedW
            height = savedH
        }

        setupWebView(view)
        setupDrag(view, params)
        setupResize(view, contentFrame, params)

        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { stopSelf() }
        view.findViewById<ImageButton>(R.id.btnMinimize).setOnClickListener { minimize(params) }
        view.findViewById<ImageButton>(R.id.btnFullscreen).setOnClickListener {
            // نفتح الرابط الرسمي في المتصفح الافتراضي (أو تطبيق الخدمة إن كان مثبتًا) لعرضه بملء الشاشة
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target.url))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(browserIntent)
            stopSelf()
        }

        windowManager.addView(view, params)
    }

    private fun setupWebView(root: View) {
        val webView = root.findViewById<WebView>(R.id.floatingWebView)
        currentWebView = webView
        val spinner = root.findViewById<ProgressBar>(R.id.loadingSpinner)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        // تخفيف استهلاك الذاكرة: لا كاش ضخم غير ضروري
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                spinner.visibility = View.GONE
            }
        }
        webView.loadUrl(target.url)
    }

    /** يوقف كل نشاط الـ WebView ويحرر ذاكرته بالكامل - يُستدعى عند التصغير أو الإغلاق */
    private fun releaseWebView() {
        currentWebView?.apply {
            stopLoading()
            onPause()
            pauseTimers()
            clearHistory()
            removeAllViews()
            destroy()
        }
        currentWebView = null
    }

    // ---------- السحب ----------

    private fun setupDrag(root: View, params: WindowManager.LayoutParams) {
        val dragBar = root.findViewById<View>(R.id.dragBar)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        dragBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit()
                        .putInt("${target.name}_x", params.x)
                        .putInt("${target.name}_y", params.y)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- تغيير الحجم ----------

    private fun setupResize(root: View, contentFrame: FrameLayout, params: WindowManager.LayoutParams) {
        val handle = root.findViewById<View>(R.id.resizeHandle)
        var startW = 0
        var startH = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = contentFrame.layoutParams.width
                    startH = contentFrame.layoutParams.height
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newW = (startW + (event.rawX - touchX)).toInt().coerceAtLeast(dp(200))
                    val newH = (startH + (event.rawY - touchY)).toInt().coerceAtLeast(dp(300))
                    contentFrame.layoutParams = contentFrame.layoutParams.apply {
                        width = newW
                        height = newH
                    }
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit()
                        .putInt("${target.name}_w", contentFrame.layoutParams.width)
                        .putInt("${target.name}_h", contentFrame.layoutParams.height)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- التصغير إلى أيقونة ----------

    private fun minimize(lastParams: WindowManager.LayoutParams) {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        // نحرر WebView بالكامل أثناء التصغير - صفر استهلاك CPU/RAM طالما الأيقونة فقط ظاهرة
        releaseWebView()

        val inflater = LayoutInflater.from(this)
        val bubble = inflater.inflate(R.layout.floating_bubble, null)
        bubbleView = bubble

        val bubbleParams = WindowManager.LayoutParams(
            dp(60), dp(60),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = lastParams.x
        bubbleParams.y = lastParams.y

        var initialX = 0; var initialY = 0; var touchX = 0f; var touchY = 0f; var moved = false

        bubble.findViewById<ImageView>(R.id.bubbleIcon).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x; initialY = bubbleParams.y
                    touchX = event.rawX; touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = initialX + (event.rawX - touchX).toInt()
                    bubbleParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(bubble, bubbleParams)
                    moved = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) restoreFromBubble(bubbleParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, bubbleParams)
    }

    private fun restoreFromBubble(bubbleParams: WindowManager.LayoutParams) {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
        prefs.edit()
            .putInt("${target.name}_x", bubbleParams.x)
            .putInt("${target.name}_y", bubbleParams.y)
            .apply()
        showFloatingWindow()
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    // ---------- الإشعار (مطلوب لخدمة Foreground) ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AIBOX Floating Window", NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AIBOX")
            .setContentText("النافذة العائمة نشطة")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWebView()
        floatingView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        floatingView = null
        bubbleView = null
        runCatching { unregisterReceiver(controlReceiver) }
        setRunningFlag(false)
    }
}
