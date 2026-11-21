package com.livecraft

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var floatingMenu: View? = null
    private var panelGiftIndex = 0
    private val panelHandler = Handler(Looper.getMainLooper())
    private val rotateGift = object : Runnable {
        override fun run() {
            val count = GiftRuleStore.load(this@FloatingOverlayService).size
            if (prefs().getBoolean(KEY_PANEL, false) && count > 1) {
                panelGiftIndex = (panelGiftIndex + 1) % count
                refreshPanel()
            }
            if (prefs().getBoolean(KEY_PANEL, false)) panelHandler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("LiveCraft flutuante ativo")
            .setContentText("Controles sobre outros aplicativos")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_BUBBLE_ON -> { prefs().edit().putBoolean(KEY_BUBBLE, true).apply(); showBubble() }
            ACTION_BUBBLE_OFF -> { prefs().edit().putBoolean(KEY_BUBBLE, false).apply(); removeBubble(); removeMenu() }
            ACTION_PANEL_ON -> { prefs().edit().putBoolean(KEY_PANEL, true).apply(); showPanel() }
            ACTION_PANEL_OFF -> { prefs().edit().putBoolean(KEY_PANEL, false).apply(); removePanel() }
            ACTION_REFRESH -> { if (panel == null && prefs().getBoolean(KEY_PANEL, false)) showPanel() else refreshPanel() }
            else -> {
                if (prefs().getBoolean(KEY_BUBBLE, false)) showBubble()
                if (prefs().getBoolean(KEY_PANEL, false)) showPanel()
            }
        }
        if (prefs().getBoolean(KEY_PANEL, false)) startGiftRotation()
        if (!prefs().getBoolean(KEY_BUBBLE, false) && !prefs().getBoolean(KEY_PANEL, false)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun prefs() = getSharedPreferences("livecraft", MODE_PRIVATE)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Controles flutuantes", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun showBubble() {
        if (bubble != null) return
        val view = DuckBubbleView(this)
        val params = WindowManager.LayoutParams(
            dp(58), dp(58), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs().getInt(KEY_BUBBLE_X, dp(14))
            y = prefs().getInt(KEY_BUBBLE_Y, dp(180))
        }
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    downX = event.rawX; downY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) dragged = true
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(0)
                    try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) prefs().edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    else {
                        if (floatingMenu == null) showMenu() else removeMenu()
                    }
                    true
                }
                else -> true
            }
        }
        try { windowManager.addView(view, params); bubble = view } catch (_: Exception) {}
    }

    private fun showMenu() {
        if (floatingMenu != null) return
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF0000000.toInt()); cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.WHITE)
            }
        }
        menu.addView(TextView(this).apply {
            text = "LIVECRAFT"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        val enabled = prefs().getBoolean(KEY_PANEL, false)
        menu.addView(menuButton(if (enabled) "OCULTAR PRESENTE" else "MOSTRAR PRESENTES") {
            if (prefs().getBoolean(KEY_PANEL, false)) {
                prefs().edit().putBoolean(KEY_PANEL, false).apply()
                removePanel()
            } else {
                prefs().edit().putBoolean(KEY_PANEL, true).apply()
                panelGiftIndex = 0
                showPanel()
                startGiftRotation()
            }
            removeMenu()
        })
        menu.addView(menuButton("FECHAR MENU") { removeMenu() })
        val params = WindowManager.LayoutParams(
            dp(190), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val bx = prefs().getInt(KEY_BUBBLE_X, dp(14))
            val by = prefs().getInt(KEY_BUBBLE_Y, dp(180))
            x = (bx + dp(64)).coerceAtMost((resources.displayMetrics.widthPixels - dp(200)).coerceAtLeast(0))
            y = by
        }
        try { windowManager.addView(menu, params); floatingMenu = menu } catch (_: Exception) {}
    }

    private fun menuButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.BLACK); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.WHITE)
        }
        setOnClickListener { action() }
    }

    private fun removeMenu() {
        floatingMenu?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        floatingMenu = null
    }

    private fun removeBubble() {
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
    }

    private fun showPanel() {
        if (panel != null) { refreshPanel(); return }
        val view = buildGiftPanel()
        val params = panelParams(view)
        try { windowManager.addView(view, params); panel = view } catch (_: Exception) {}
    }

    private fun panelParams(view: View): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            dp(270), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val fractionX = prefs().getFloat(KEY_PANEL_X, 0.04f)
        val fractionY = prefs().getFloat(KEY_PANEL_Y, 0.18f)
        val metrics = resources.displayMetrics
        params.x = ((metrics.widthPixels - dp(270)).coerceAtLeast(0) * fractionX).toInt()
        params.y = ((metrics.heightPixels - dp(180)).coerceAtLeast(0) * fractionY).toInt()
        return params
    }

    private fun buildGiftPanel(): View {
        val rules = GiftRuleStore.load(this)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(0xBF000000.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.WHITE)
            }
        }
        if (rules.isEmpty()) {
            outer.addView(panelText("Nenhum presente configurado", 12f, 0xFFBBBBBB.toInt()))
            return outer
        }
        panelGiftIndex = panelGiftIndex.coerceIn(0, rules.lastIndex)
        val rule = rules[panelGiftIndex]
        val entry = GiftRuleStore.giftCatalog(this).firstOrNull { it.name.equals(rule.giftName, true) }
        val imageUrl = rule.imageUrl.ifBlank { entry?.imageUrl.orEmpty() }
        if (imageUrl.isNotBlank()) {
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                scaleType = ImageView.ScaleType.FIT_CENTER
                tag = imageUrl
            }
            loadImage(imageUrl, image)
            outer.addView(image)
        } else {
            outer.addView(TextView(this).apply {
                text = entry?.emoji ?: rule.icon
                textSize = 29f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        texts.addView(panelText(rule.giftName, 13f, Color.WHITE, true))
        val action = GiftRuleStore.actionLabel(rule.actionType) +
            (rule.value.takeIf { it.isNotBlank() }?.let { " • " + actionName(rule) } ?: "")
        texts.addView(panelText(action, 11f, 0xFFCCCCCC.toInt()))
        outer.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return outer
    }

    private fun startGiftRotation() {
        panelHandler.removeCallbacks(rotateGift)
        if (prefs().getBoolean(KEY_PANEL, false)) panelHandler.postDelayed(rotateGift, 2000L)
    }

    private fun actionName(rule: GiftRule): String {
        val choices = when (rule.actionType) {
            "SUMMON" -> GiftRuleStore.mobs
            "GIVE" -> GiftRuleStore.items
            "EFFECT" -> GiftRuleStore.effects
            "COMMAND" -> GiftRuleStore.commands
            else -> emptyList()
        }
        return choices.firstOrNull { it.second == rule.value }?.first ?: rule.value
    }

    private fun panelText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun loadImage(url: String, target: ImageView) {
        if (!url.startsWith("https://")) return
        Thread {
            val bitmap = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            } catch (_: Exception) { null }
            if (bitmap != null) Handler(Looper.getMainLooper()).post { if (target.tag == url) target.setImageBitmap(bitmap) }
        }.start()
    }

    private fun refreshPanel() {
        if (panel == null) return
        val old = panel!!
        val params = try { old.layoutParams as WindowManager.LayoutParams } catch (_: Exception) { panelParams(old) }
        try { windowManager.removeView(old) } catch (_: Exception) {}
        panel = null
        val fresh = buildGiftPanel()
        try { windowManager.addView(fresh, params); panel = fresh } catch (_: Exception) {}
    }

    private fun removePanel() {
        panelHandler.removeCallbacks(rotateGift)
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    override fun onDestroy() {
        removeBubble(); removeMenu(); removePanel()
        super.onDestroy()
    }

    class DuckBubbleView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = width.coerceAtMost(height).toFloat()
            val cx = width / 2f; val cy = height / 2f
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, s * .48f, paint)
            paint.color = 0xFF202020.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = s * .035f
            canvas.drawCircle(cx, cy, s * .48f, paint); paint.style = Paint.Style.FILL
            paint.color = 0xFFFFD84D.toInt()
            canvas.drawOval(cx - s*.23f, cy - s*.16f, cx + s*.22f, cy + s*.25f, paint)
            canvas.drawCircle(cx - s*.04f, cy - s*.18f, s*.17f, paint)
            paint.color = 0xFFFF9F1C.toInt()
            val bill = Path().apply { moveTo(cx + s*.07f, cy - s*.18f); lineTo(cx + s*.34f, cy - s*.10f); lineTo(cx + s*.08f, cy - s*.03f); close() }
            canvas.drawPath(bill, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(cx + s*.015f, cy - s*.22f, s*.025f, paint)
            paint.color = 0xFFFF9F1C.toInt()
            canvas.drawOval(cx - s*.16f, cy + s*.22f, cx - s*.02f, cy + s*.29f, paint)
            canvas.drawOval(cx + s*.04f, cy + s*.22f, cx + s*.18f, cy + s*.29f, paint)
        }
    }

    companion object {
        const val ACTION_BUBBLE_ON = "com.livecraft.overlay.BUBBLE_ON"
        const val ACTION_BUBBLE_OFF = "com.livecraft.overlay.BUBBLE_OFF"
        const val ACTION_PANEL_ON = "com.livecraft.overlay.PANEL_ON"
        const val ACTION_PANEL_OFF = "com.livecraft.overlay.PANEL_OFF"
        const val ACTION_REFRESH = "com.livecraft.overlay.REFRESH"
        const val KEY_BUBBLE = "overlay_bubble_enabled"
        const val KEY_PANEL = "overlay_panel_enabled"
        const val KEY_BUBBLE_X = "overlay_bubble_x"
        const val KEY_BUBBLE_Y = "overlay_bubble_y"
        const val KEY_PANEL_X = "overlay_panel_x"
        const val KEY_PANEL_Y = "overlay_panel_y"
        const val CHANNEL = "livecraft_overlay"
        const val NOTIFICATION_ID = 42
    }
}
