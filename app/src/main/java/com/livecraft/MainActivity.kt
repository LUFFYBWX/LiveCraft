package com.livecraft

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var minecraftStatus: TextView
    private lateinit var tiktokStatus: TextView
    private lateinit var generalStatus: TextView
    private lateinit var log: TextView
    private lateinit var tiktokUsername: EditText
    private lateinit var mobModeButton: Button
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != GameBridgeService.ACTION_UPDATE) return
            setConnectionState(
                minecraftStatus,
                intent.getBooleanExtra(GameBridgeService.EXTRA_MINECRAFT_CONNECTED, false),
                "Minecraft"
            )
            setConnectionState(
                tiktokStatus,
                intent.getBooleanExtra(GameBridgeService.EXTRA_TIKTOK_CONNECTED, false),
                "TikTok LIVE"
            )
            generalStatus.text = intent.getStringExtra(GameBridgeService.EXTRA_STATUS) ?: "Aguardando..."
            val line = intent.getStringExtra(GameBridgeService.EXTRA_LOG)
            if (!line.isNullOrBlank()) {
                val previous = log.text.toString().lineSequence().take(11).joinToString("\n")
                log.text = "$line\n$previous"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(Color.BLACK)
        }

        root.addView(label("LIVECRAFT", 28f, Color.WHITE, true))
        root.addView(label("Conecte sua live ao Minecraft Bedrock", 14f, 0xFFCCCCCC.toInt()))
        root.addView(space(20))

        root.addView(sectionTitle("STATUS DAS CONEXÕES"))
        minecraftStatus = connectionRow("Minecraft")
        tiktokStatus = connectionRow("TikTok LIVE")
        root.addView(minecraftStatus)
        root.addView(tiktokStatus)
        root.addView(label("Branco = conectado   •   Cinza = desconectado", 12f, 0xFFAAAAAA.toInt()))
        root.addView(space(14))

        root.addView(sectionTitle("SUA CONTA DO TIKTOK"))
        tiktokUsername = EditText(this).apply {
            hint = "Digite seu usuário, sem @"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            setText(getSharedPreferences("livecraft", MODE_PRIVATE).getString("tiktok_username", "") ?: "")
            background = roundedOutline(16, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            setPadding(16, 12, 16, 12)
        }
        root.addView(tiktokUsername)
        root.addView(space(12))

        root.addView(sectionTitle("CONTROLES"))
        root.addView(makeButton("CONFIGURAR PAINEL DE PRESENTES") {
            startActivity(Intent(this, PanelPositionActivity::class.java))
        })
        root.addView(makeButton("PERSONALIZAR PRESENTES") {
            startActivity(Intent(this, GiftRulesActivity::class.java))
        })
        root.addView(makeButton("CONFIGURAR META DE LIKES ❤️") {
            startActivity(Intent(this, LikeGoalActivity::class.java))
        })

        mobModeButton = makeButton(
            if (getSharedPreferences("livecraft", MODE_PRIVATE).getBoolean(GameBridgeService.KEY_MOB_MODE_ENABLED, false))
                "DESATIVAR MODO MOBS" else "ATIVAR MODO MOBS"
        ) {
            val prefs = getSharedPreferences("livecraft", MODE_PRIVATE)
            val enabled = !prefs.getBoolean(GameBridgeService.KEY_MOB_MODE_ENABLED, false)
            prefs.edit().putBoolean(GameBridgeService.KEY_MOB_MODE_ENABLED, enabled).apply()
            mobModeButton.text = if (enabled) "DESATIVAR MODO MOBS" else "ATIVAR MODO MOBS"
            generalStatus.text = if (enabled)
                "Modo Mobs ativado. O último mob comentado será invocado após o delay configurado."
            else "Modo Mobs desativado."
        }
        root.addView(mobModeButton)
        root.addView(label("Delay do Modo Mobs (segundos)", 14f, Color.WHITE))
        val mobDelayInput = EditText(this).apply {
            hint = "1 a 60 segundos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
            setText(getSharedPreferences("livecraft", MODE_PRIVATE)
                .getInt(GameBridgeService.KEY_MOB_DELAY_SECONDS, GameBridgeService.DEFAULT_MOB_DELAY_SECONDS).toString())
            background = roundedOutline(16, Color.BLACK, Color.WHITE)
            setPadding(16, 12, 16, 12)
        }
        root.addView(mobDelayInput)
        root.addView(makeButton("SALVAR DELAY") {
            val seconds = mobDelayInput.text.toString().toIntOrNull()
            if (seconds == null || seconds !in 1..60) {
                generalStatus.text = "Delay inválido. Escolha entre 1 e 60 segundos."
            } else {
                getSharedPreferences("livecraft", MODE_PRIVATE).edit()
                    .putInt(GameBridgeService.KEY_MOB_DELAY_SECONDS, seconds).apply()
                generalStatus.text = "Delay do Modo Mobs salvo: $seconds segundos."
            }
        })
        root.addView(makeButton("SALVAR USUÁRIO") {
            val username = tiktokUsername.text.toString().trim().removePrefix("@").trim()
            if (username.isBlank()) {
                generalStatus.text = "Digite seu usuário do TikTok antes de salvar."
            } else {
                getSharedPreferences("livecraft", MODE_PRIVATE).edit().putString("tiktok_username", username).apply()
                generalStatus.text = "Usuário configurado: @$username. Agora você pode conectar à LIVE."
            }
        })
        root.addView(makeButton("CONECTAR À LIVE") {
            requestNotificationPermissionIfNeeded()
            val username = tiktokUsername.text.toString().trim().removePrefix("@").trim()
            if (username.isBlank()) {
                generalStatus.text = "Digite seu usuário do TikTok antes de conectar."
            } else {
                getSharedPreferences("livecraft", MODE_PRIVATE).edit().putString("tiktok_username", username).apply()
                val i = Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_START_TIKTOK)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                generalStatus.text = "Procurando a LIVE de @$username..."
            }
        })
        root.addView(makeButton("CONECTAR AO MINECRAFT") {
            requestNotificationPermissionIfNeeded()
            val i = Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_START_MINECRAFT)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            generalStatus.text = "Iniciando servidor local do Minecraft..."
        })
        root.addView(makeButton("DESCONECTAR DA LIVE") {
            startService(Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_STOP_TIKTOK))
            generalStatus.text = "Encerrando conexão com o TikTok..."
        })
        root.addView(makeButton("DESCONECTAR DO MINECRAFT") {
            startService(Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_STOP_MINECRAFT))
            generalStatus.text = "Encerrando conexão com o Minecraft..."
        })
        root.addView(makeButton("PARAR TUDO") {
            startService(Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_STOP))
            generalStatus.text = "Desligando todas as conexões..."
        })
        root.addView(space(16))

        root.addView(sectionTitle("COMO CONECTAR O MINECRAFT"))
        root.addView(label(
            "1. Abra seu mundo com cheats ativados.\\n2. Nas configurações, desative “Conectar somente a WebSockets confiáveis”.\\n3. Abra o chat e digite:\\n/connect 127.0.0.1:8765\\n4. Volte aqui e confira se Minecraft ficou conectado.",
            14f, 0xFFDDDDDD.toInt()
        ))
        root.addView(space(14))

        root.addView(sectionTitle("MENSAGEM DO LIVECRAFT"))
        val lastCheckpoint = getSharedPreferences("livecraft", MODE_PRIVATE)
            .getString("native_checkpoint", null)
        generalStatus = label(
            lastCheckpoint?.let { "Última etapa registrada: $it" } ?: "Aguardando você iniciar.",
            14f, Color.WHITE
        ).apply {
            background = roundedOutline(16, 0xFF000000.toInt(), 0xFF555555.toInt())
            setPadding(16, 14, 16, 14)
        }
        root.addView(generalStatus)
        root.addView(space(14))

        root.addView(sectionTitle("ÚLTIMOS EVENTOS"))
        log = label("Nenhum evento recebido ainda.", 12f, 0xFFBBBBBB.toInt()).apply {
            background = roundedOutline(16, 0xFF000000.toInt(), 0xFF555555.toInt())
            setPadding(16, 14, 16, 14)
        }
        root.addView(log)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
    }

    private fun startOverlayService(action: String) {
        val intent = Intent(this, FloatingOverlayService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun roundedOutline(radiusDp: Int, fill: Int, stroke: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusDp * resources.displayMetrics.density
            setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
        }

    private fun connectionRow(name: String): TextView = TextView(this).apply {
        text = "✕  $name: DESCONECTADO"
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFAAAAAA.toInt())
        background = roundedOutline(16, Color.BLACK, Color.WHITE)
        setPadding(16, 16, 16, 16)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = 10
        layoutParams = params
    }

    private fun setConnectionState(view: TextView, connected: Boolean, name: String) {
        view.text = if (connected) "✓  $name: CONECTADO" else "✕  $name: DESCONECTADO"
        view.setTextColor(if (connected) Color.WHITE else 0xFFAAAAAA.toInt())
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 4)
        }

    private fun sectionTitle(value: String): TextView = label(value, 13f, Color.WHITE, true).apply {
        setPadding(0, 10, 0, 8)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedOutline(24, Color.BLACK, Color.WHITE)
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 10
            layoutParams = params
            setOnClickListener { action() }
        }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(GameBridgeService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        receiverRegistered = true
        GameBridgeService.publishCurrentState(this)
    }

    override fun onStop() {
        if (receiverRegistered) unregisterReceiver(receiver)
        receiverRegistered = false
        super.onStop()
    }
}
