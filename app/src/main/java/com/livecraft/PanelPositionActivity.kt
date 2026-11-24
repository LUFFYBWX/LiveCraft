package com.livecraft

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.max
import kotlin.math.min

class PanelPositionActivity : Activity() {
    private lateinit var stage: FrameLayout
    private lateinit var preview: LinearLayout
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "POSICIONE O PAINEL DE PRESENTES"
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Arraste o retângulo para escolher onde ele ficará durante a live."
            textSize = 13f; setTextColor(0xFFCCCCCC.toInt())
        })
        stage = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF242424.toInt()); setStroke(dp(1), 0xFF777777.toInt()); cornerRadius = dp(12).toFloat()
            }
        }
        val stageParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(10) }
        root.addView(stage, stageParams)
        preview = buildPreview()
        val previewParams = FrameLayout.LayoutParams(dp(270), dp(210), Gravity.TOP or Gravity.START)
        stage.addView(preview, previewParams)
        preview.post {
            val prefs = getSharedPreferences("livecraft", MODE_PRIVATE)
            val x = prefs.getFloat(FloatingOverlayService.KEY_PANEL_X, .04f)
            val y = prefs.getFloat(FloatingOverlayService.KEY_PANEL_Y, .18f)
            preview.x = (stage.width - preview.width).coerceAtLeast(0) * x
            preview.y = (stage.height - preview.height).coerceAtLeast(0) * y
        }
        preview.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = preview.x; startY = preview.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = startX + event.rawX - downX
                    val ny = startY + event.rawY - downY
                    preview.x = min(max(0f, nx), (stage.width - preview.width).coerceAtLeast(0).toFloat())
                    preview.y = min(max(0f, ny), (stage.height - preview.height).coerceAtLeast(0).toFloat())
                    true
                }
                else -> true
            }
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(makeButton("SALVAR E ATIVAR", 1f) {
            val maxX = (stage.width - preview.width).coerceAtLeast(1)
            val maxY = (stage.height - preview.height).coerceAtLeast(1)
            getSharedPreferences("livecraft", MODE_PRIVATE).edit()
                .putFloat(FloatingOverlayService.KEY_PANEL_X, (preview.x / maxX).coerceIn(0f, 1f))
                .putFloat(FloatingOverlayService.KEY_PANEL_Y, (preview.y / maxY).coerceIn(0f, 1f))
                .putBoolean(FloatingOverlayService.KEY_PANEL, true).apply()
            if (startOverlay(FloatingOverlayService.ACTION_PANEL_ON)) {
                Toast.makeText(this, "Painel ativado. Ele não bloqueia toques.", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        buttons.addView(makeButton("DESATIVAR PAINEL", 1f) {
            getSharedPreferences("livecraft", MODE_PRIVATE).edit().putBoolean(FloatingOverlayService.KEY_PANEL, false).apply()
            if (android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)) {
                startOverlay(FloatingOverlayService.ACTION_PANEL_OFF)
            }
            finish()
        })
        buttons.addView(makeButton("VOLTAR", 1f) { finish() })
        root.addView(buttons)
        setContentView(root)
    }

    private fun buildPreview(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(0xBF000000.toInt()); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.WHITE)
            }
        }
        val firstRule = GiftRuleStore.load(this).firstOrNull()
        if (firstRule == null) {
            box.addView(TextView(this).apply {
                text = "Nenhum presente configurado"; textSize = 12f; setTextColor(Color.WHITE)
            })
        } else {
            val entry = GiftRuleStore.giftCatalog(this).firstOrNull { it.name.equals(firstRule.giftName, true) }
            val image = firstRule.imageUrl.ifBlank { entry?.imageUrl.orEmpty() }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = if (image.isBlank()) entry?.emoji ?: firstRule.icon else "◉"
                textSize = 26f; setTextColor(Color.WHITE); setPadding(0, dp(4), dp(8), dp(4))
            })
            val detail = GiftRuleStore.actionLabel(firstRule.actionType) +
                (firstRule.value.takeIf { it.isNotBlank() }?.let { " • " + it } ?: "")
            row.addView(TextView(this).apply {
                text = firstRule.giftName + "\n" + detail; textSize = 12f; setTextColor(Color.WHITE)
            })
            box.addView(row)
        }
        return box
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("livecraft", MODE_PRIVATE)
        if (prefs.getBoolean("pending_panel_overlay_permission", false) &&
            android.os.Build.VERSION.SDK_INT >= 23 && android.provider.Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("pending_panel_overlay_permission", false).apply()
            val intent = Intent(this, FloatingOverlayService::class.java).setAction(FloatingOverlayService.ACTION_PANEL_ON)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            finish()
        }
    }

    private fun startOverlay(action: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            getSharedPreferences("livecraft", MODE_PRIVATE).edit().putBoolean("pending_panel_overlay_permission", true).apply()
            Toast.makeText(this, "Ative a permissão de sobreposição na próxima tela.", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return false
        }
        val intent = Intent(this, FloatingOverlayService::class.java).setAction(action)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        return true
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun makeButton(text: String, weight: Float, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.WHITE) }
        layoutParams = LinearLayout.LayoutParams(0, -2, weight).apply { marginEnd = dp(5) }
        setOnClickListener { action() }
    }
}
