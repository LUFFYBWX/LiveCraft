package com.livecraft

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LiveGiftEvent(val giftName: String, val diamondTotal: Long, val repeatCount: Int = 1, val isCombo: Boolean = false, val isStreakOver: Boolean = true)
data class GiftAction(val giftName: String, val diamondTotal: Long, val command: String)
data class GiftRule(val giftName: String, val actionType: String, val value: String = "", val amount: Int = 1)

object GiftRuleStore {
    private const val KEY_RULES = "gift_rules_v1"
    val ACTION_TYPES = listOf("SUMMON", "GIVE", "EFFECT", "CLEAR", "KILL", "HEAL", "FEED", "LIGHTNING", "SOUND")
    fun defaults() = listOf(
        GiftRule("Rose", "SUMMON", "zombie"), GiftRule("Rosa", "SUMMON", "zombie"),
        GiftRule("White Rose", "GIVE", "golden_apple"), GiftRule("Rosa Branca", "GIVE", "golden_apple"),
        GiftRule("Mini Dino", "CLEAR"), GiftRule("Capybara", "KILL"), GiftRule("Capivara", "KILL")
    )
    fun load(context: Context): List<GiftRule> {
        val raw = context.getSharedPreferences("livecraft", Context.MODE_PRIVATE).getString(KEY_RULES, null) ?: return defaults()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("giftName").trim()
                    val type = item.optString("actionType").trim().uppercase()
                    if (name.isNotBlank() && type in ACTION_TYPES) add(GiftRule(name, type, item.optString("value"), item.optInt("amount", 1).coerceIn(1, 64)))
                }
            }
        } catch (_: Exception) { defaults() }
    }
    fun save(context: Context, rules: List<GiftRule>) {
        val array = JSONArray()
        rules.forEach { array.put(JSONObject().put("giftName", it.giftName.trim()).put("actionType", it.actionType).put("value", it.value.trim()).put("amount", it.amount.coerceIn(1, 64))) }
        context.getSharedPreferences("livecraft", Context.MODE_PRIVATE).edit().putString(KEY_RULES, array.toString()).apply()
    }
    fun actionLabel(type: String) = when (type) {
        "SUMMON" -> "Invocar entidade"; "GIVE" -> "Dar item"; "EFFECT" -> "Aplicar efeito"
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
            "CLEAR" -> "clear @p"; "KILL" -> "kill @p"
            "HEAL" -> "effect @p instant_health 1 255 true"
            "FEED" -> "effect @p saturation 1 255 true"
            "LIGHTNING" -> "summon lightning_bolt ~ ~ ~"
            "SOUND" -> "playsound random.levelup @p"
            else -> null
        }
    }
}
