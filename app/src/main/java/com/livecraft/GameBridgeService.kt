package com.livecraft

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.UUID

class GameBridgeService : Service() {
    private var server: MinecraftSocketServer? = null
    private var activeTikTokUsername: String? = null
    private var pendingLikes: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopBridge()
            ACTION_START_MINECRAFT -> startMinecraftOnly()
            ACTION_START_TIKTOK -> startTikTokOnly()
            ACTION_STOP_MINECRAFT -> stopMinecraftOnly()
            ACTION_STOP_TIKTOK -> stopTikTokOnly()
            else -> startBridge()
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        createNotificationChannel()
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("LiveCraft ativo")
                .setContentText("Conexões do LiveCraft em execução")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("LiveCraft ativo")
                .setContentText("Conexões do LiveCraft em execução")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, notification)
    }

    private fun startMinecraftOnly() {
        ensureForeground()
        try {
            if (server == null) server = MinecraftSocketServer().also { it.start() }
            update("Servidor Minecraft iniciado. Use /connect 127.0.0.1:8765 no jogo.", "Conexão Minecraft iniciada.")
        } catch (error: Exception) {
            update("Falha ao iniciar Minecraft: ${error.message}", "Erro no servidor local.")
        }
    }

    private fun startTikTokOnly() {
        ensureForeground()
        val username = getSharedPreferences("livecraft", MODE_PRIVATE)
            .getString("tiktok_username", "")?.trim()?.removePrefix("@").orEmpty()
        if (username.isBlank()) {
            update("Informe e salve seu usuário do TikTok antes de conectar.", "Usuário do TikTok não configurado.")
            return
        }
        if (activeTikTokUsername == username) {
            update("Conector TikTok já está em execução para @$username.", "TikTok já iniciado.")
            return
        }
        stopTikTokConnector()
        try {
            saveNativeCheckpoint("Kotlin: antes de chamar JNI para @$username")
            TikTokNative.start(username)
            saveNativeCheckpoint("Kotlin: JNI retornou sem fechar")
            activeTikTokUsername = username
            tiktokConnected = false
            update("Procurando a LIVE de @$username...", "Conector TikTok iniciado.")
        } catch (error: Throwable) {
            activeTikTokUsername = null
            tiktokConnected = false
            update("Falha ao iniciar o conector TikTok: ${error.message ?: error.javaClass.simpleName}", "Verifique a instalação do aplicativo.")
        }
    }

    private fun stopMinecraftOnly() {
        try { server?.stop(500) } catch (_: Exception) {}
        server = null
        connected = null
        update("Conexão Minecraft encerrada.", "Servidor Minecraft parado.")
        stopIfIdle()
    }

    private fun stopTikTokOnly() {
        stopTikTokConnector()
        update("Conexão TikTok encerrada.", "Conector TikTok parado.")
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (server == null && activeTikTokUsername == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startBridge() {
        ensureForeground()
        try {
            if (server == null) {
                server = MinecraftSocketServer().also { it.start() }
            }

            val username = getSharedPreferences("livecraft", MODE_PRIVATE)
                .getString("tiktok_username", "")?.trim()?.removePrefix("@").orEmpty()

            if (username.isBlank()) {
                stopTikTokConnector()
                update("Servidor ativo. Informe seu usuário do TikTok.", "Usuário do TikTok não configurado.")
                return
            }

            if (activeTikTokUsername != username) {
                stopTikTokConnector()
                try {
                    TikTokNative.start(username)
                    activeTikTokUsername = username
                    tiktokConnected = false
                    update("Servidor ativo. Localizando a LIVE de @$username...", "Conector TikTok iniciado.")
                } catch (error: Throwable) {
                    activeTikTokUsername = null
                    tiktokConnected = false
                    update("Falha ao iniciar o conector TikTok: ${error.message ?: error.javaClass.simpleName}", "Verifique a instalação do aplicativo.")
                }
            } else {
                update("LiveCraft ativo. Mantendo conexão de @$username.", "Conector TikTok já está em execução.")
            }
        } catch (e: Exception) {
            update("Falha ao iniciar: ${e.message}", "Erro ao iniciar servidor.")
            stopSelf()
        }
    }

    private fun stopBridge() {
        stopTikTokConnector()
        tiktokConnected = false
        try { server?.stop(500) } catch (_: Exception) {}
        server = null
        connected = null
        update("Servidor parado", "Conexão encerrada.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTikTokConnector() {
        try {
            TikTokNative.nativeStop()
        } catch (_: Throwable) {
            // The app may have been installed without the native library.
        }
        activeTikTokUsername = null
        tiktokConnected = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LiveCraft", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        stopTikTokConnector()
        tiktokConnected = false
        try { server?.stop(500) } catch (_: Exception) {}
        server = null
        instance = null
        super.onDestroy()
    }

    fun handleTikTokGift(giftName: String, diamondTotal: Long, isCombo: Boolean = false, isStreakOver: Boolean = true): Boolean {
        val action = GiftRuleEngine().handle(
            LiveGiftEvent(giftName = giftName, diamondTotal = diamondTotal, isCombo = isCombo, isStreakOver = isStreakOver)
        ) ?: return false
        val sent = sendCommand(action.command)
        update(
            if (sent) "Presente recebido: ${action.giftName}" else "Presente recebido, mas Minecraft desconectado",
            "${action.giftName} (${action.diamondTotal} moedas) → ${action.command}"
        )
        return sent
    }

    @Synchronized fun handleTikTokLikes(count: Int) {
        if (count <= 0) return
        pendingLikes += count.toLong()
        while (pendingLikes >= 100) {
            pendingLikes -= 100
            val sent = sendCommand("summon zombie ~ ~1 ~")
            update(if (sent) "Meta de 100 likes atingida: 1 Zumbi!" else "100 likes atingidos, mas Minecraft está desconectado", "100 likes → summon zombie")
        }
    }

    fun handleTikTokChat(username: String, comment: String): Boolean {
        val safeUser = username.replace('\r', ' ').replace('\n', ' ').take(32)
        val safeComment = comment.replace('\r', ' ').replace('\n', ' ').take(180)
        if (safeComment.isBlank()) return false
        val text = "§b[$safeUser] §f$safeComment"
        val raw = JSONObject().put("rawtext", org.json.JSONArray().put(JSONObject().put("text", text)))
        val sent = sendCommand("tellraw @a $raw")
        update(
            if (sent) "Comentário recebido: $safeUser" else "Comentário recebido, mas Minecraft desconectado",
            "$safeUser: $safeComment"
        )
        return sent
    }

    private fun update(status: String, line: String? = null) {
        currentStatus = status
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_LOG, line)
            .putExtra(EXTRA_MINECRAFT_CONNECTED, connected?.isOpen == true)
            .putExtra(EXTRA_TIKTOK_CONNECTED, tiktokConnected))
    }

    private inner class MinecraftSocketServer : WebSocketServer(InetSocketAddress("127.0.0.1", PORT)) {
        override fun onStart() {
            update("Servidor local iniciado. Abra o Minecraft e conecte.", "Aguardando Minecraft em ws://127.0.0.1:$PORT")
        }
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            connected = conn
            update("Minecraft conectado!", "Minecraft conectou ao LiveCraft.")
        }
        override fun onMessage(conn: WebSocket, message: String) {
            update("Minecraft conectado!", "Resposta do Minecraft: ${message.take(180)}")
        }
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            if (connected === conn) connected = null
            update("Minecraft desconectado", "Conexão encerrada ($code).")
        }
        override fun onError(conn: WebSocket?, ex: Exception) {
            update("Erro na conexão", ex.message ?: "Erro WebSocket")
        }
    }

    companion object {
        const val ACTION_START = "com.livecraft.START"
        const val ACTION_STOP = "com.livecraft.STOP"
        const val ACTION_START_MINECRAFT = "com.livecraft.START_MINECRAFT"
        const val ACTION_START_TIKTOK = "com.livecraft.START_TIKTOK"
        const val ACTION_STOP_MINECRAFT = "com.livecraft.STOP_MINECRAFT"
        const val ACTION_STOP_TIKTOK = "com.livecraft.STOP_TIKTOK"
        const val ACTION_UPDATE = "com.livecraft.UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LOG = "log"
        const val EXTRA_MINECRAFT_CONNECTED = "minecraft_connected"
        const val EXTRA_TIKTOK_CONNECTED = "tiktok_connected"
        private const val CHANNEL_ID = "livecraft_service"
        private const val PORT = 8765

        @Volatile private var currentStatus: String = "Aguardando você iniciar."
        @Volatile private var connected: WebSocket? = null
        @Volatile private var instance: GameBridgeService? = null
        @Volatile private var tiktokConnected: Boolean = false

        fun publishCurrentState(context: android.content.Context) {
            context.sendBroadcast(
                Intent(ACTION_UPDATE).setPackage(context.packageName)
                    .putExtra(EXTRA_STATUS, currentStatus)
                    .putExtra(EXTRA_MINECRAFT_CONNECTED, connected?.isOpen == true)
                    .putExtra(EXTRA_TIKTOK_CONNECTED, tiktokConnected)
            )
        }

        @JvmStatic
        fun onNativeTikTokGift(giftName: String, diamondTotal: Long, isCombo: Boolean, isStreakOver: Boolean) {
            instance?.handleTikTokGift(giftName, diamondTotal, isCombo, isStreakOver)
        }

        @JvmStatic
        fun onNativeTikTokLikes(count: Int) {
            instance?.handleTikTokLikes(count)
        }

        @JvmStatic
        fun onNativeTikTokChat(username: String, comment: String) {
            instance?.handleTikTokChat(username, comment)
        }

        private fun saveNativeCheckpoint(message: String) {
            instance?.getSharedPreferences("livecraft", MODE_PRIVATE)?.edit()
                ?.putString("native_checkpoint", message)
                ?.putLong("native_checkpoint_time", System.currentTimeMillis())
                ?.commit()
        }

        @JvmStatic
        fun onNativeTikTokStatus(message: String) {
            saveNativeCheckpoint("Rust: $message")
            val lower = message.lowercase()
            tiktokConnected = lower.startsWith("conectado à live") || lower.startsWith("conectado a live")
            instance?.update(message, message)
        }

        @JvmStatic
        fun onNativeTikTokDiagnostic(eventType: String) {
            instance?.update(currentStatus, "Evento TikTok recebido, mas ainda não mapeado: $eventType")
        }

        fun sendChatMessageForTest(): Boolean {
            val raw = JSONObject().put("rawtext", org.json.JSONArray().put(JSONObject().put("text", "§a[LiveCraft] §fComentário de teste")))
            return sendCommand("tellraw @a $raw")
        }

        fun sendCommand(command: String): Boolean {
            val socket = connected ?: return false
            if (!socket.isOpen) return false
            val packet = JSONObject().apply {
                put("header", JSONObject().apply {
                    put("version", 1)
                    put("requestId", UUID.randomUUID().toString())
                    put("messageType", "commandRequest")
                    put("messagePurpose", "commandRequest")
                })
                put("body", JSONObject().apply {
                    put("version", 1)
                    put("commandLine", command.removePrefix("/"))
                    put("origin", JSONObject().put("type", "player"))
                })
            }
            return try { socket.send(packet.toString()); true } catch (_: Exception) { false }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
