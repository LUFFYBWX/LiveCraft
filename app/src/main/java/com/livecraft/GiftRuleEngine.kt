package com.livecraft

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LiveGiftEvent(val giftName: String, val diamondTotal: Long, val repeatCount: Int = 1, val isCombo: Boolean = false, val isStreakOver: Boolean = true)
data class GiftAction(val giftName: String, val diamondTotal: Long, val command: String)
data class GiftRule(val giftName: String, val actionType: String, val value: String = "", val amount: Int = 1, val icon: String = "🎁")

object GiftRuleStore {
    private const val KEY_RULES = "gift_rules_v1"
    val ACTION_TYPES = listOf("SUMMON", "GIVE", "EFFECT", "COMMAND", "CLEAR", "KILL", "HEAL", "FEED", "LIGHTNING", "SOUND")
    val mobs = listOf("🧟 Zumbi" to "zombie", "💀 Esqueleto" to "skeleton", "💣 Creeper" to "creeper", "🕷️ Aranha" to "spider", "👁️ Enderman" to "enderman", "🟩 Slime" to "slime", "🧙 Bruxa" to "witch", "🏹 Saqueador" to "pillager", "🗿 Golem de ferro" to "iron_golem", "🐺 Lobo" to "wolf", "🐱 Gato" to "cat", "🐝 Abelha" to "bee", "🐔 Galinha" to "chicken", "🐄 Vaca" to "cow", "🐷 Porco" to "pig", "🐑 Ovelha" to "sheep", "🐎 Cavalo" to "horse", "👹 Warden" to "warden", "🐉 Dragão do End" to "ender_dragon", "🔥 Blaze" to "blaze", "👻 Ghast" to "ghast", "🌊 Afogado" to "drowned", "🪽 Phantom" to "phantom")
    val items = listOf("💎 Diamante" to "diamond", "💚 Esmeralda" to "emerald", "🍎 Maçã dourada" to "golden_apple", "✨ Maçã encantada" to "enchanted_golden_apple", "🔷 Barra de netherita" to "netherite_ingot", "🛡️ Totem" to "totem_of_undying", "💣 TNT" to "tnt", "🟣 Pérola do End" to "ender_pearl", "🧪 Frasco de XP" to "experience_bottle", "🥕 Cenoura dourada" to "golden_carrot", "🥩 Carne assada" to "cooked_beef", "🏹 Arco" to "bow", "🎯 Besta" to "crossbow", "🔱 Tridente" to "trident", "🛡️ Escudo" to "shield", "🪽 Elytra" to "elytra", "⬛ Obsidiana" to "obsidian", "🎂 Bolo" to "cake", "🎆 Fogo de artifício" to "firework_rocket", "🪙 Barra de ouro" to "gold_ingot", "⛏️ Picareta de diamante" to "diamond_pickaxe", "🧱 Bloco de diamante" to "diamond_block")
    val effects = listOf("💨 Velocidade" to "speed", "💪 Força" to "strength", "🦘 Super salto" to "jump_boost", "🛡️ Resistência" to "resistance", "💚 Regeneração" to "regeneration", "👻 Invisibilidade" to "invisibility", "🌙 Visão noturna" to "night_vision", "⛏️ Pressa" to "haste", "🔥 Resistência ao fogo" to "fire_resistance", "🌊 Respiração aquática" to "water_breathing", "🪶 Queda lenta" to "slow_falling", "⬆️ Levitação" to "levitation", "☠️ Veneno" to "poison", "🖤 Wither" to "wither", "🙈 Cegueira" to "blindness", "🐌 Lentidão" to "slowness", "🥀 Fraqueza" to "weakness")
    val commands = listOf("☀️ Mudar para dia" to "time_day", "🌙 Mudar para noite" to "time_night", "🌤️ Limpar clima" to "weather_clear", "🌧️ Chuva" to "weather_rain", "⛈️ Tempestade" to "weather_thunder", "⭐ Dar 5 níveis de XP" to "xp_5", "⬆️ Teleportar para cima" to "tp_up", "💥 Invocar TNT" to "tnt")
    val iconOptions = listOf("🌹", "🤍", "🦖", "🦫", "🎵", "🫶", "💐", "💎", "🍩", "🍦", "❤️", "🎉", "🕊️", "🎁", "⭐", "🐸")
    fun defaults() = listOf(
        GiftRule("Rose", "SUMMON", "zombie"), GiftRule("Rosa", "SUMMON", "zombie"),
        GiftRule("White Rose", "GIVE", "golden_apple"), GiftRule("Rosa Branca", "GIVE", "golden_apple"),
        GiftRule("Mini Dino", "CLEAR"), GiftRule("Capybara", "KILL", icon = "🦫"), GiftRule("Capivara", "KILL", icon = "🦫"),
        GiftRule("TikTok", "SUMMON", "creeper", icon = "🎵"), GiftRule("Finger Heart", "HEAL", icon = "🫶"),
        GiftRule("Perfume", "EFFECT", "speed", 2, "💐"), GiftRule("GG", "GIVE", "diamond", 1, "💎"),
        GiftRule("Doughnut", "FEED", icon = "🍩"), GiftRule("Ice Cream", "EFFECT", "regeneration", 2, "🍦"),
        GiftRule("Love You", "SUMMON", "wolf", icon = "❤️"), GiftRule("Confetti", "LIGHTNING", icon = "🎉"),
        GiftRule("Paper Crane", "GIVE", "emerald", 1, "🕊️")
    )
    fun load(context: Context): List<GiftRule> {
        val prefs = context.getSharedPreferences("livecraft", Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RULES, null)
        val loaded = if (raw == null) defaults() else try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("giftName").trim()
                    val type = item.optString("actionType").trim().uppercase()
                    if (name.isNotBlank() && type in ACTION_TYPES) add(GiftRule(name, type, item.optString("value"), item.optInt("amount", 1).coerceIn(1, 64), item.optString("icon", "🎁")))
                }
            }
        } catch (_: Exception) { defaults() }
        if (!prefs.getBoolean("gift_catalog_seeded_v1", false)) {
            val merged = loaded.toMutableList()
            defaults().forEach { preset ->
                if (merged.none { it.giftName.equals(preset.giftName, true) }) merged.add(preset)
            }
            save(context, merged)
            prefs.edit().putBoolean("gift_catalog_seeded_v1", true).apply()
            return merged
        }
        return loaded
    }
    fun save(context: Context, rules: List<GiftRule>) {
        val array = JSONArray()
        rules.forEach { array.put(JSONObject().put("giftName", it.giftName.trim()).put("actionType", it.actionType).put("value", it.value.trim()).put("amount", it.amount.coerceIn(1, 64)).put("icon", it.icon)) }
        context.getSharedPreferences("livecraft", Context.MODE_PRIVATE).edit().putString(KEY_RULES, array.toString()).apply()
    }
    fun commandForPreset(key: String): String? = when (key) { "time_day" -> "time set day"; "time_night" -> "time set night"; "weather_clear" -> "weather clear"; "weather_rain" -> "weather rain"; "weather_thunder" -> "weather thunder"; "xp_5" -> "xp 5L @p"; "tp_up" -> "tp @p ~ ~10 ~"; "tnt" -> "summon tnt ~ ~1 ~"; else -> null }

    fun actionLabel(type: String) = when (type) {
        "SUMMON" -> "Invocar entidade"; "GIVE" -> "Dar item"; "EFFECT" -> "Aplicar efeito"; "COMMAND" -> "Executar comando pronto"
        "CLEAR" -> "Limpar inventário"; "KILL" -> "Eliminar jogador"; "HEAL" -> "Curar jogador"
        "FEED" -> "Encher fome"; "LIGHTNING" -> "Invocar raio"; "SOUND" -> "Tocar som"
        else -> "Ação desconhecida"
    }
}

class GiftRuleEngine(private val rules: List<GiftRule> = GiftRuleStore.defaults()) {
    fun handle(event: LiveGiftEvent): GiftAction? {
        if (event.isCombo && !event.isStreakOver) return null
        val rule = rules.firstOrNull { it.giftName.trim().equals(event.giftName.trim(), ignoreCase = true) } ?: return null
        val command = commandFor(rule) ?: return null
        return GiftAction(event.giftName, event.diamondTotal, command)
    }
    private fun identifier(value: String): String? {
        val clean = value.trim().lowercase()
        return clean.takeIf { it.matches(Regex("[a-z0-9_.:-]{1,64}")) }
    }
    private fun commandFor(rule: GiftRule): String? {
        val amount = rule.amount.coerceIn(1, 64)
        return when (rule.actionType) {
            "SUMMON" -> identifier(rule.value)?.let { "summon $it ~ ~1 ~" }
            "GIVE" -> identifier(rule.value)?.let { "give @p $it $amount" }
            "EFFECT" -> identifier(rule.value)?.let { "effect @p $it 10 ${(amount - 1).coerceAtMost(4)} true" }
            "COMMAND" -> GiftRuleStore.commandForPreset(rule.value)
            "CLEAR" -> "clear @p"; "KILL" -> "kill @p"
            "HEAL" -> "effect @p instant_health 1 255 true"
            "FEED" -> "effect @p saturation 1 255 true"
            "LIGHTNING" -> "summon lightning_bolt ~ ~ ~"
            "SOUND" -> "playsound random.levelup @p"
            else -> null
        }
    }
}
