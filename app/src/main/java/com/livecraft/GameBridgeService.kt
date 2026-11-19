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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopBridge()
            else -> startBridge()
        }
        return START_STICKY
    }

    private fun startBridge() {
        if (server != null) return
        createNotificationChannel()
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("LiveCraft ativo")
                .setContentText("Aguardando conexão do Minecraft")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("LiveCraft ativo")
                .setContentText("Aguardando conexão do Minecraft")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, notification)

        try {
            server = MinecraftSocketServer().also { it.start() }
            update("Servidor ativo em 127.0.0.1:8765", "Servidor WebSocket iniciado.")
        } catch (e: Exception) {
            update("Falha ao iniciar: ${e.message}", "Erro ao iniciar servidor.")
            stopSelf()
        }
    }

    private fun stopBridge() {
        try { server?.stop(500) } catch (_: Exception) {}
        server = null
        update("Servidor parado", "Conexão encerrada.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        try { server?.stop(500) } catch (_: Exception) {}
        server = null
        instance = null
        super.onDestroy()
    }

    private fun update(status: String, line: String? = null) {
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName)
            .putExtra(EXTRA_STATUS, status).putExtra(EXTRA_LOG, line))
    }

    private inner class MinecraftSocketServer :
        WebSocketServer(InetSocketAddress("127.0.0.1", PORT)) {

        override fun onStart() {
            update("Servidor ativo. Abra o Minecraft e conecte.", "Aguardando Minecraft em ws://127.0.0.1:$PORT")
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
        const val ACTION_UPDATE = "com.livecraft.UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LOG = "log"
        private const val CHANNEL_ID = "livecraft_service"
        private const val PORT = 8765

        @Volatile private var connected: WebSocket? = null
        @Volatile private var instance: GameBridgeService? = null

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
            return try {
                socket.send(packet.toString())
                true
            } catch (_: Exception) { false }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
