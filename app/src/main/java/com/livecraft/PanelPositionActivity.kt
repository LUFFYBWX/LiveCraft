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
    private lateinit var overlayTextInput: EditText
    private lateinit var overlaySizeInput: EditText
    private lateinit var floatingTextPreview: TextView
    private var textDownX = 0f
    private var textDownY = 0f
    private var textStartX = 0f
    private var textStartY = 0f
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Editor imersivo: a área de arraste corresponde à tela inteira.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        stage = FrameLayout(this).apply {
            setBackgroundColor(0xFF242424.toInt())
        }
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xAA000000.toInt()); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0xFF777777.toInt())
            }
        }
        header.addView(TextView(this).apply {
            text = "POSICIONE O PAINEL DE PRESENTES"
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        header.addView(TextView(this).apply {
            text = "Arraste os elementos livremente por toda a tela."
            textSize = 12f; setTextColor(0xFFCCCCCC.toInt())
        })
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(10), dp(8), dp(10), 0)
        })
        val prefs = getSharedPreferences("livecraft", MODE_PRIVATE)
        preview = buildPreview()
        val previewParams = FrameLayout.LayoutParams(dp(270), dp(210), Gravity.TOP or Gravity.START)
        stage.addView(preview, previewParams)
        floatingTextPreview = TextView(this).apply {
            text = prefs.getString(FloatingOverlayService.KEY_TEXT_VALUE, "").orEmpty().ifBlank { "Texto flutuante" }
            textSize = prefs.getInt(FloatingOverlayService.KEY_TEXT_SIZE, 24).toFloat()
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.WHITE) }
        }
        stage.addView(floatingTextPreview, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        floatingTextPreview.post {
            val fx = prefs.getFloat(FloatingOverlayService.KEY_TEXT_X, .04f)
            val fy = prefs.getFloat(FloatingOverlayService.KEY_TEXT_Y, .04f)
            floatingTextPreview.x = (stage.width - floatingTextPreview.width).coerceAtLeast(0) * fx
            floatingTextPreview.y = (stage.height - floatingTextPreview.height).coerceAtLeast(0) * fy
        }
        floatingTextPreview.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { textDownX = event.rawX; textDownY = event.rawY; textStartX = floatingTextPreview.x; textStartY = floatingTextPreview.y; true }
                MotionEvent.ACTION_MOVE -> {
                    floatingTextPreview.x = (textStartX + event.rawX - textDownX).coerceIn(0f, (stage.width - floatingTextPreview.width).coerceAtLeast(0).toFloat())
                    floatingTextPreview.y = (textStartY + event.rawY - textDownY).coerceIn(0f, (stage.height - floatingTextPreview.height).coerceAtLeast(0).toFloat())
                    true
                }
                else -> true
            }
        }
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
        val textControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        overlayTextInput = EditText(this).apply {
            hint = "Texto flutuante"
            setSingleLine()
            setTextColor(Color.WHITE); setHintTextColor(0xFFAAAAAA.toInt())
            setText(prefs.getString(FloatingOverlayService.KEY_TEXT_VALUE, "").orEmpty())
        }
        overlaySizeInput = EditText(this).apply {
            hint = "Px"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine()
            setTextColor(Color.WHITE); setHintTextColor(0xFFAAAAAA.toInt())
            setText(prefs.getInt(FloatingOverlayService.KEY_TEXT_SIZE, 24).toString())
        }
        textControls.addView(overlayTextInput, LinearLayout.LayoutParams(0, -2, 3f))
        textControls.addView(overlaySizeInput, LinearLayout.LayoutParams(0, -2, 1f))
        val controlsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt()); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0xFF777777.toInt())
            }
        }
        controlsPanel.addView(textControls)
        overlayTextInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { floatingTextPreview.text = s?.toString().orEmpty().ifBlank { "Texto flutuante" } }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        overlaySizeInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { floatingTextPreview.textSize = (s?.toString()?.toIntOrNull() ?: 24).coerceIn(12, 72).toFloat() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val textToggle = makeButton(if (prefs.getBoolean(FloatingOverlayService.KEY_TEXT, false)) "TEXTO: ATIVADO" else "TEXTO: DESATIVADO", 1f) {}
        textToggle.setOnClickListener {
            val enabled = !getSharedPreferences("livecraft", MODE_PRIVATE).getBoolean(FloatingOverlayService.KEY_TEXT, false)
            getSharedPreferences("livecraft", MODE_PRIVATE).edit().putBoolean(FloatingOverlayService.KEY_TEXT, enabled).apply()
            textToggle.text = if (enabled) "TEXTO: ATIVADO" else "TEXTO: DESATIVADO"
        }
        controlsPanel.addView(textToggle)
        val controlsParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2); bottomMargin = dp(6)
        }
        val bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt()); cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF777777.toInt())
            }
        }
        bottomControls.addView(controlsPanel, controlsParams)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(makeButton("SALVAR E ATIVAR", 1f) {
            val maxX = (stage.width - preview.width).coerceAtLeast(1)
            val maxY = (stage.height - preview.height).coerceAtLeast(1)
            getSharedPreferences("livecraft", MODE_PRIVATE).edit()
                .putFloat(FloatingOverlayService.KEY_PANEL_X, (preview.x / maxX).coerceIn(0f, 1f))
                .putFloat(FloatingOverlayService.KEY_PANEL_Y, (preview.y / maxY).coerceIn(0f, 1f))
                .putBoolean(FloatingOverlayService.KEY_PANEL, true)
                .putString(FloatingOverlayService.KEY_TEXT_VALUE, overlayTextInput.text.toString().trim())
                .putInt(FloatingOverlayService.KEY_TEXT_SIZE, (overlaySizeInput.text.toString().toIntOrNull() ?: 24).coerceIn(12, 72))
                .putFloat(FloatingOverlayService.KEY_TEXT_X, (floatingTextPreview.x / (stage.width - floatingTextPreview.width).coerceAtLeast(1)).coerceIn(0f, 1f))
                .putFloat(FloatingOverlayService.KEY_TEXT_Y, (floatingTextPreview.y / (stage.height - floatingTextPreview.height).coerceAtLeast(1)).coerceIn(0f, 1f))
                .apply()
            if (startOverlay(FloatingOverlayService.ACTION_PANEL_ON)) {
                Toast.makeText(this, "Painel ativado. Ele não bloqueia toques.", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        buttons.addView(makeButton("DESATIVAR PAINEL", 1f) {
            getSharedPreferences("livecraft", MODE_PRIVATE).edit().putBoolean(FloatingOverlayService.KEY_PANEL, false).putBoolean(FloatingOverlayService.KEY_TEXT, false).apply()
            if (android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this)) {
                startOverlay(FloatingOverlayService.ACTION_PANEL_OFF)
            }
            finish()
        })
        buttons.addView(makeButton("VOLTAR", 1f) { finish() })
        bottomControls.addView(buttons)
        root.addView(bottomControls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(8), 0, dp(8), dp(8))
        })
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
