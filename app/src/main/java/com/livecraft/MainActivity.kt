package com.livecraft

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var tiktokUsername: EditText
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != GameBridgeService.ACTION_UPDATE) return
            status.text = intent.getStringExtra(GameBridgeService.EXTRA_STATUS) ?: "Aguardando..."
            val line = intent.getStringExtra(GameBridgeService.EXTRA_LOG)
            if (!line.isNullOrBlank()) log.text = "$line\n" + log.text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF10131D.toInt())
        }

        val title = TextView(this).apply {
            text = "LIVECRAFT"
            textSize = 27f
            setTextColor(0xFFB9A5FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "Minecraft Bedrock • teste de conexão"
            textSize = 14f
            setTextColor(0xFFD5D8E2.toInt())
        }
        status = TextView(this).apply {
            text = "Servidor parado"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 28, 0, 16)
        }

        tiktokUsername = EditText(this).apply {
            hint = "Usuário do TikTok (sem @)"
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8D95A8.toInt())
            setText(getSharedPreferences("livecraft", MODE_PRIVATE).getString("tiktok_username", "") ?: "")
        }

        val start = makeButton("INICIAR CONEXÃO LOCAL") {
            requestNotificationPermissionIfNeeded()
            val username = tiktokUsername.text.toString().trim().removePrefix("@")
            getSharedPreferences("livecraft", MODE_PRIVATE).edit().putString("tiktok_username", username).apply()
            val i = Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "Iniciando servidor..."
        }
        val stop = makeButton("PARAR CONEXÃO") {
            startService(Intent(this, GameBridgeService::class.java).setAction(GameBridgeService.ACTION_STOP))
            status.text = "Servidor parado"
        }
        val test = makeButton("TESTAR: INVOCAR 1 ZUMBI") {
            val ok = GameBridgeService.sendCommand("summon zombie ~ ~1 ~")
            if (!ok) status.text = "Minecraft ainda não está conectado"
            else status.text = "Comando enviado! Confira o Minecraft."
        }

        val instructions = TextView(this).apply {
            text = "PASSOS NO MINECRAFT\n1. Abra um mundo próprio com cheats ativados.\n2. Ative WebSockets nas configurações, se essa opção aparecer.\n3. No chat, conecte usando:\n/connect 127.0.0.1:8765\n4. Volte ao LiveCraft e toque em TESTAR."
            textSize = 14f
            setTextColor(0xFFE0E4EF.toInt())
            setPadding(0, 20, 0, 18)
        }
        val heading = TextView(this).apply {
            text = "REGISTRO"
            textSize = 13f
            setTextColor(0xFFB9A5FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        log = TextView(this).apply {
            text = "Nenhum evento ainda."
            textSize = 12f
            setTextColor(0xFFB8C0D4.toInt())
            setPadding(0, 8, 0, 0)
        }

        listOf(title, subtitle, tiktokUsername, status, start, stop, test, instructions, heading, log).forEach {
            layout.addView(it)
        }
        setContentView(ScrollView(this).apply { addView(layout) })
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
    }

    override fun onStop() {
        if (receiverRegistered) unregisterReceiver(receiver)
        receiverRegistered = false
        super.onStop()
    }
}
