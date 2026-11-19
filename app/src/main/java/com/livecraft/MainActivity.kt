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
            setBackgroundColor(Color.rgb(15, 18, 28))
        }

        root.addView(label("LIVECRAFT", 27f, 0xFFB9A5FF.toInt(), true))
        root.addView(label("Conecte sua live ao Minecraft Bedrock", 14f, 0xFFD5D8E2.toInt()))
        root.addView(space(18))

        root.addView(sectionTitle("STATUS DAS CONEXÕES"))
        minecraftStatus = connectionRow("Minecraft")
        tiktokStatus = connectionRow("TikTok LIVE")
        root.addView(minecraftStatus)
        root.addView(tiktokStatus)
        root.addView(label("✓ Verde = conectado     ✕ Vermelho = desconectado", 12f, 0xFFB8C0D4.toInt()))
        root.addView(space(12))

        root.addView(sectionTitle("SUA CONTA DO TIKTOK"))
        tiktokUsername = EditText(this).apply {
            hint = "Digite seu usuário, sem @"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8D95A8.toInt())
            setText(getSharedPreferences("livecraft", MODE_PRIVATE).getString("tiktok_username", "") ?: "")
        }
        root.addView(tiktokUsername)
        root.addView(space(8))

        root.addView(sectionTitle("CONTROLES"))
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
                val i = Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                generalStatus.text = "Usuário @$username salvo. Procurando a LIVE..."
            }
        })
        root.addView(makeButton("PARAR LIVECRAFT") {
            startService(Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_STOP))
            generalStatus.text = "Desligando conexões..."
        })
        root.addView(space(8))

        root.addView(sectionTitle("TESTES"))
        root.addView(makeButton("TESTAR ZUMBI NO MINECRAFT") {
            val ok = GameBridgeService.sendCommand("summon zombie ~ ~1 ~")
            generalStatus.text = if (ok) "Comando enviado. Confira seu mundo." else "Minecraft não conectado. Veja o status acima."
        })
        root.addView(makeButton("TESTAR MENSAGEM NO CHAT") {
            val ok = GameBridgeService.sendChatMessageForTest()
            generalStatus.text = if (ok) "Mensagem enviada. Confira o chat do Minecraft." else "Minecraft não conectado. Veja o status acima."
        })
        root.addView(space(8))

        root.addView(sectionTitle("COMO CONECTAR O MINECRAFT"))
        root.addView(label(
            "1. Abra seu mundo com cheats ativados.\n2. Nas configurações, desative “Conectar somente a WebSockets confiáveis”.\n3. Abra o chat e digite:\n/connect 127.0.0.1:8765\n4. Volte aqui e confira se Minecraft ficou verde.",
            14f, 0xFFE0E4EF.toInt()
        ))
        root.addView(space(12))

        root.addView(sectionTitle("MENSAGEM DO LIVECRAFT"))
        generalStatus = label("Aguardando você iniciar.", 14f, Color.WHITE)
        root.addView(generalStatus)
        root.addView(space(12))

        root.addView(sectionTitle("ÚLTIMOS EVENTOS"))
        log = label("Nenhum evento recebido ainda.", 12f, 0xFFB8C0D4.toInt())
        root.addView(log)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun connectionRow(name: String): TextView = TextView(this).apply {
        text = "✕  $name: DESCONECTADO"
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFF6B6B.toInt())
        setPadding(16, 16, 16, 16)
        setBackgroundColor(0xFF252B3B.toInt())
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = 8
        layoutParams = params
    }

    private fun setConnectionState(view: TextView, connected: Boolean, name: String) {
        view.text = if (connected) "✓  $name: CONECTADO" else "✕  $name: DESCONECTADO"
        view.setTextColor(if (connected) 0xFF55D68B.toInt() else 0xFFFF6B6B.toInt())
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 4)
        }

    private fun sectionTitle(value: String): TextView = label(value, 13f, 0xFFB9A5FF.toInt(), true).apply {
        setPadding(0, 10, 0, 8)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
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
