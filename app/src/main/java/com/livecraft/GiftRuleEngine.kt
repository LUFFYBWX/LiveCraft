package com.livecraft

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LiveGiftEvent(val giftName: String, val diamondTotal: Long, val repeatCount: Int = 1, val isCombo: Boolean = false, val isStreakOver: Boolean = true)
data class GiftAction(val giftName: String, val diamondTotal: Long, val command: String, val commands: List<String> = listOf(command))
data class GiftRule(val giftName: String, val actionType: String, val value: String = "", val amount: Int = 1, val icon: String = "🎁", val imageUrl: String = "")
data class GiftCatalogEntry(val name: String, val coins: Int, val imageUrl: String, val emoji: String = "🎁")

object GiftRuleStore {
    private const val KEY_RULES = "gift_rules_v1"
    private const val KEY_SEEN_GIFTS = "gift_catalog_seen_v1"
    val ACTION_TYPES = listOf("SUMMON", "GIVE", "EFFECT", "COMMAND", "CLEAR", "KILL", "HEAL", "FEED", "LIGHTNING", "SOUND", "KIT_NETHERITE", "MULTI")

    val mobs = listOf(
        "🧟 Zumbi" to "zombie", "💀 Esqueleto" to "skeleton", "💣 Creeper" to "creeper",
        "🕷️ Aranha" to "spider", "👁️ Enderman" to "enderman", "🟩 Slime" to "slime",
        "🧙 Bruxa" to "witch", "🏹 Saqueador" to "pillager", "🗿 Golem de ferro" to "iron_golem",
        "🐺 Lobo" to "wolf", "🐱 Gato" to "cat", "🐝 Abelha" to "bee", "🐔 Galinha" to "chicken",
        "🐄 Vaca" to "cow", "🐷 Porco" to "pig", "🐑 Ovelha" to "sheep", "🐎 Cavalo" to "horse",
        "👹 Warden" to "warden", "🐉 Dragão do End" to "ender_dragon", "🔥 Blaze" to "blaze",
        "👻 Ghast" to "ghast", "🌊 Afogado" to "drowned", "🪽 Phantom" to "phantom",
        "🧟 Husk" to "husk", "🧟 Zumbi aldeão" to "zombie_villager", "🦇 Morcego" to "bat",
        "🐢 Tartaruga" to "turtle", "🐬 Golfinho" to "dolphin", "🐼 Panda" to "panda",
        "🦊 Raposa" to "fox", "🐸 Sapo" to "frog", "🐪 Camelo" to "camel",
        "🦙 Lhama" to "llama", "🐻 Urso polar" to "polar_bear", "🦑 Lula" to "squid",
        "✨ Allay" to "allay", "🧌 Piglin" to "piglin", "💀 Wither" to "wither"
    )
    val items = listOf(
        "💎 Diamante" to "diamond", "💚 Esmeralda" to "emerald", "🍎 Maçã dourada" to "golden_apple",
        "✨ Maçã encantada" to "enchanted_golden_apple", "🔷 Barra de netherita" to "netherite_ingot",
        "🛡️ Totem" to "totem_of_undying", "💣 TNT" to "tnt", "🟣 Pérola do End" to "ender_pearl",
        "🧪 Frasco de XP" to "experience_bottle", "🥕 Cenoura dourada" to "golden_carrot",
        "🥩 Carne assada" to "cooked_beef", "🏹 Arco" to "bow", "🎯 Besta" to "crossbow",
        "🔱 Tridente" to "trident", "🛡️ Escudo" to "shield", "🪽 Elytra" to "elytra",
        "⬛ Obsidiana" to "obsidian", "🎂 Bolo" to "cake", "🎆 Fogo de artifício" to "firework_rocket",
        "🪙 Barra de ouro" to "gold_ingot", "⛏️ Picareta de diamante" to "diamond_pickaxe",
        "🧱 Bloco de diamante" to "diamond_block", "🪓 Machado de netherita" to "netherite_axe",
        "🧱 Bloco de netherita" to "netherite_block", "🧭 Bússola" to "compass",
        "🪽 Asa de phantom" to "phantom_membrane", "🧿 Olho do End" to "ender_eye",
        "🧨 Pólvora" to "gunpowder", "🪄 Vara de blaze" to "blaze_rod",
        "🍯 Favo de mel" to "honeycomb", "🪣 Balde de água" to "water_bucket"
    )
    val effects = listOf(
        "💨 Velocidade" to "speed", "💪 Força" to "strength", "🦘 Super salto" to "jump_boost",
        "🛡️ Resistência" to "resistance", "💚 Regeneração" to "regeneration",
        "👻 Invisibilidade" to "invisibility", "🌙 Visão noturna" to "night_vision",
        "⛏️ Pressa" to "haste", "🔥 Resistência ao fogo" to "fire_resistance",
        "🌊 Respiração aquática" to "water_breathing", "🪶 Queda lenta" to "slow_falling",
        "⬆️ Levitação" to "levitation", "☠️ Veneno" to "poison", "🖤 Wither" to "wither",
        "🙈 Cegueira" to "blindness", "🐌 Lentidão" to "slowness", "🥀 Fraqueza" to "weakness",
        "✨ Brilho" to "glowing", "🍀 Sorte" to "luck", "🫨 Náusea" to "nausea",
        "⛏️ Fadiga de mineração" to "mining_fatigue"
    )
    val commands = listOf(
        "☀️ Mudar para dia" to "time_day",
        "🌙 Meia-noite" to "time_midnight",
        "🌃 Mudar para noite" to "time_night",
        "🌤️ Limpar clima" to "weather_clear",
        "🌧️ Chuva" to "weather_rain",
        "⛈️ Tempestade" to "weather_thunder",
        "⭐ Dar 5 níveis de XP" to "xp_5",
        "🌟 Dar 10 níveis de XP" to "xp_10",
        "⬆️ Jogar o jogador para cima" to "tp_up",
        "🚀 Lançar bem alto" to "tp_high",
        "💥 Invocar TNT" to "tnt",
        "🧨 Dar 16 TNT" to "give_tnt",
        "🧟 Invocar zumbi" to "spawn_zombie",
        "💀 Invocar esqueleto" to "spawn_skeleton",
        "💣 Invocar creeper" to "spawn_creeper",
        "👹 Invocar Warden" to "spawn_warden",
        "⚡ Raio no jogador" to "lightning",
        "🎆 Soltar fogos" to "firework",
        "👻 Invisibilidade por 10s" to "invisible",
        "🦘 Super salto por 10s" to "super_jump",
        "🐌 Deixar lento por 8s" to "slow",
        "🙈 Cegueira por 8s" to "blind",
        "🫨 Náusea por 8s" to "nausea",
        "💚 Curar completamente" to "heal",
        "🥩 Encher a fome" to "feed",
        "🔊 Som de dragão" to "dragon_sound",
        "📢 Anunciar presente" to "announce",
        "🧹 Limpar inventário" to "clear_inventory",
        "☠️ Eliminar jogador" to "kill_player"
    )
    val iconOptions = listOf("🌹", "🤍", "🦖", "🦫", "🎵", "🫶", "💐", "💎", "🍩", "🍦", "❤️", "🎉", "🕊️", "🎁", "⭐", "🐸")

    private val giftIconBase = "https://raw.githubusercontent.com/tao-Isaman/tiktok-live-gift/main/public/gifts/"
    val builtInGiftCatalog = listOf(
        GiftCatalogEntry("Rose", 1, giftIconBase + "rose.webp", "🌹"),
        GiftCatalogEntry("TikTok", 1, giftIconBase + "tiktok.webp", "🎵"),
        GiftCatalogEntry("Heart", 1, giftIconBase + "heart.webp", "❤️"),
        GiftCatalogEntry("GG", 1, giftIconBase + "gg.webp", "🆒"),
        GiftCatalogEntry("Like", 1, "", "👍"),
        GiftCatalogEntry("Ice Cream Cone", 1, giftIconBase + "ice-cream.webp", "🍦"),
        GiftCatalogEntry("Finger Heart", 5, giftIconBase + "finger-heart.webp", "🫰"),
        GiftCatalogEntry("Friendship Necklace", 7, giftIconBase + "friendship-necklace.webp", "📿"),
        GiftCatalogEntry("Rosa", 10, giftIconBase + "rosa.webp", "🌷"),
        GiftCatalogEntry("Perfume", 20, giftIconBase + "perfume.webp", "🌸"),
        GiftCatalogEntry("Doughnut", 30, giftIconBase + "doughnut.webp", "🍩"),
        GiftCatalogEntry("Star", 99, giftIconBase + "star.webp", "⭐"),
        GiftCatalogEntry("Cap", 99, giftIconBase + "cap.webp", "🧢"),
        GiftCatalogEntry("Paper Crane", 99, giftIconBase + "paper-crane.webp", "🕊️"),
        GiftCatalogEntry("Hand Hearts", 100, giftIconBase + "hand-hearts.webp", "🫶"),
        GiftCatalogEntry("Confetti", 100, giftIconBase + "confetti.webp", "🎊"),
        GiftCatalogEntry("Gamepad", 100, "", "🎮"),
        GiftCatalogEntry("Hat and Mustache", 199, giftIconBase + "hat-and-mustache.webp", "🎩"),
        GiftCatalogEntry("Sunglasses", 199, giftIconBase + "sunglasses.webp", "🕶️"),
        GiftCatalogEntry("Lock and Key", 199, "", "🔐"),
        GiftCatalogEntry("Corgi", 299, giftIconBase + "corgi.webp", "🐕"),
        GiftCatalogEntry("Money Gun", 500, giftIconBase + "money-gun.webp", "💸"),
        GiftCatalogEntry("Coral", 499, giftIconBase + "coral.webp", "🪸"),
        GiftCatalogEntry("Swan", 699, giftIconBase + "swan.webp", "🦢"),
        GiftCatalogEntry("Train", 899, giftIconBase + "train.webp", "🚂"),
        GiftCatalogEntry("Galaxy", 1000, giftIconBase + "galaxy.webp", "🌌"),
        GiftCatalogEntry("Disco Ball", 1000, giftIconBase + "disco-ball.webp", "🪩"),
        GiftCatalogEntry("Mishka Bear", 1000, giftIconBase + "mishka-bear.webp", "🧸"),
        GiftCatalogEntry("Fireworks", 1088, giftIconBase + "fireworks.webp", "🎆"),
        GiftCatalogEntry("Diamond Ring", 1500, giftIconBase + "diamond-ring.webp", "💍"),
        GiftCatalogEntry("Chasing the Dream", 1500, giftIconBase + "chasing-the-dream.webp", "✨"),
        GiftCatalogEntry("Whale Diving", 2150, giftIconBase + "whale-diving.webp", "🐋"),
        GiftCatalogEntry("Motorcycle", 2988, giftIconBase + "motorcycle.webp", "🏍️"),
        GiftCatalogEntry("Mermaid", 2988, "", "🧜‍♀️"),
        GiftCatalogEntry("Private Jet", 4888, giftIconBase + "private-jet.webp", "🛩️"),
        GiftCatalogEntry("Unicorn Fantasy", 5000, giftIconBase + "unicorn-fantasy.webp", "🦄"),
        GiftCatalogEntry("Airplane", 6000, "", "✈️"),
        GiftCatalogEntry("Sports Car", 7000, giftIconBase + "sports-car.webp", "🏎️"),
        GiftCatalogEntry("Yacht", 8999, "", "🛥️"),
        GiftCatalogEntry("Interstellar", 10000, giftIconBase + "interstellar.webp", "🌠"),
        GiftCatalogEntry("Planet", 15000, "", "🪐"),
        GiftCatalogEntry("Castle Fantasy", 20000, giftIconBase + "castle-fantasy.webp", "🏰"),
        GiftCatalogEntry("Dragon Flame", 26999, "", "🐉"),
        GiftCatalogEntry("Phoenix", 25999, giftIconBase + "phoenix.webp", "🐦‍🔥"),
        GiftCatalogEntry("Lion", 29999, giftIconBase + "lion.webp", "🦁"),
        GiftCatalogEntry("TikTok Universe", 44999, giftIconBase + "tiktok-universe.webp", "🌟")
    )

    fun giftCatalog(context: Context): List<GiftCatalogEntry> {
        val all = mutableMapOf<String, GiftCatalogEntry>()
        try {
            val catalogJson = context.assets.open("gift_catalog.json").bufferedReader().use { it.readText() }
            val catalogArray = JSONArray(catalogJson)
            for (i in 0 until catalogArray.length()) {
                val entry = catalogArray.optJSONObject(i) ?: continue
                val name = entry.optString("name").trim()
                val image = entry.optString("imageUrl").trim()
                if (name.isNotBlank() && image.startsWith("https://")) {
                    val coins = entry.optInt("coins", 0)
                    val existing = all[name.lowercase()]
                    all[name.lowercase()] = GiftCatalogEntry(name, coins, image, existing?.emoji ?: "🎁")
                }
            }
        } catch (_: Exception) {}
        val raw = context.getSharedPreferences("livecraft", Context.MODE_PRIVATE).getString(KEY_SEEN_GIFTS, null)
        if (!raw.isNullOrBlank()) try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                val url = item.optString("imageUrl")
                val old = all[name.lowercase()]
                all[name.lowercase()] = GiftCatalogEntry(name, item.optInt("coins", old?.coins ?: 0), url.ifBlank { old?.imageUrl.orEmpty() }, old?.emoji ?: "🎁")
            }
        } catch (_: Exception) {}
        return all.values.sortedWith(compareBy<GiftCatalogEntry> { it.coins }.thenBy { it.name.lowercase() })
    }

    fun rememberGift(context: Context, name: String, imageUrl: String) {
        val cleanName = name.trim().take(100)
        val cleanUrl = imageUrl.trim().takeIf { it.startsWith("https://") && it.length <= 2048 }.orEmpty()
        if (cleanName.isBlank()) return
        val prefs = context.getSharedPreferences("livecraft", Context.MODE_PRIVATE)
        val entries = giftCatalog(context).associateBy { it.name.lowercase() }.toMutableMap()
        val previous = entries[cleanName.lowercase()]
        entries[cleanName.lowercase()] = GiftCatalogEntry(cleanName, previous?.coins ?: 0, cleanUrl.ifBlank { previous?.imageUrl.orEmpty() }, previous?.emoji ?: "🎁")
        val array = JSONArray()
        entries.values.filter { entry ->
            val builtIn = builtInGiftCatalog.firstOrNull { it.name.equals(entry.name, true) }
            builtIn == null || entry.imageUrl != builtIn.imageUrl || entry.coins != builtIn.coins
        }.forEach {
            array.put(JSONObject().put("name", it.name).put("coins", it.coins).put("imageUrl", it.imageUrl))
        }
        prefs.edit().putString(KEY_SEEN_GIFTS, array.toString()).apply()

        val rules = load(context).toMutableList()
        val index = rules.indexOfFirst { it.giftName.equals(cleanName, true) }
        if (index >= 0 && cleanUrl.isNotBlank()) {
            rules[index] = rules[index].copy(imageUrl = cleanUrl)
            save(context, rules)
        }
    }

    fun defaults() = listOf(
        GiftRule("Rose", "SUMMON", "zombie", icon = "🌹"), GiftRule("Rosa", "SUMMON", "zombie", icon = "🌹"),
        GiftRule("White Rose", "GIVE", "golden_apple", icon = "🤍"), GiftRule("Rosa Branca", "GIVE", "golden_apple", icon = "🤍"),
        GiftRule("Mini Dino", "CLEAR", icon = "🦖"), GiftRule("Capybara", "KILL", icon = "🦫"), GiftRule("Capivara", "KILL", icon = "🦫"),
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
                    if (name.isNotBlank() && type in ACTION_TYPES) add(
                        GiftRule(name, type, item.optString("value"), item.optInt("amount", 1).coerceIn(1, 64), item.optString("icon", "🎁"), item.optString("imageUrl"))
                    )
                }
            }
        } catch (_: Exception) { defaults() }
        if (!prefs.getBoolean("gift_catalog_seeded_v1", false)) {
            val merged = loaded.toMutableList()
            defaults().forEach { preset ->
                val existingIndex = merged.indexOfFirst { it.giftName.equals(preset.giftName, true) }
                if (existingIndex < 0) merged.add(preset)
                else if (merged[existingIndex].icon == "🎁") merged[existingIndex] = merged[existingIndex].copy(icon = preset.icon)
            }
            save(context, merged)
            prefs.edit().putBoolean("gift_catalog_seeded_v1", true).apply()
            return merged
        }
        return loaded
    }

    fun save(context: Context, rules: List<GiftRule>) {
        val array = JSONArray()
        rules.forEach {
            array.put(JSONObject().put("giftName", it.giftName.trim()).put("actionType", it.actionType).put("value", it.value.trim())
                .put("amount", it.amount.coerceIn(1, 64)).put("icon", it.icon).put("imageUrl", it.imageUrl))
        }
        context.getSharedPreferences("livecraft", Context.MODE_PRIVATE).edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun commandForPreset(key: String): String? = when (key) {
        "time_day" -> "time set day"
        "time_midnight" -> "time set midnight"
        "time_night" -> "time set night"
        "weather_clear" -> "weather clear"
        "weather_rain" -> "weather rain"
        "weather_thunder" -> "weather thunder"
        "xp_5" -> "xp 5L @p"
        "xp_10" -> "xp 10L @p"
        "tp_up" -> "tp @p ~ ~10 ~"
        "tp_high" -> "tp @p ~ ~40 ~"
        "tnt" -> "summon tnt ~ ~1 ~"
        "give_tnt" -> "give @p tnt 16"
        "spawn_zombie" -> "summon zombie ~ ~1 ~"
        "spawn_skeleton" -> "summon skeleton ~ ~1 ~"
        "spawn_creeper" -> "summon creeper ~ ~1 ~"
        "spawn_warden" -> "summon warden ~ ~1 ~"
        "lightning" -> "summon lightning_bolt ~ ~ ~"
        "firework" -> "summon firework_rocket ~ ~2 ~"
        "invisible" -> "effect @p invisibility 10 0 true"
        "super_jump" -> "effect @p jump_boost 10 4 true"
        "slow" -> "effect @p slowness 8 3 true"
        "blind" -> "effect @p blindness 8 0 true"
        "nausea" -> "effect @p nausea 8 0 true"
        "heal" -> "effect @p instant_health 1 255 true"
        "feed" -> "effect @p saturation 1 255 true"
        "dragon_sound" -> "playsound mob.enderdragon.growl @a ~ ~ ~ 1 1"
        "announce" -> "tellraw @a {\"rawtext\":[{\"text\":\"§l§cSURPRESA! §fUm presente chegou!\"}]}"
        "clear_inventory" -> "clear @p"
        "kill_player" -> "kill @p"
        else -> null
    }

    fun actionLabel(type: String) = when (type) {
        "SUMMON" -> "Invocar entidade"
        "GIVE" -> "Dar item"
        "EFFECT" -> "Aplicar efeito"
        "COMMAND" -> "Comando de entretenimento"
        "CLEAR" -> "Limpar inventário"
        "KILL" -> "Eliminar jogador"
        "HEAL" -> "Curar jogador"
        "FEED" -> "Encher fome"
        "LIGHTNING" -> "Invocar raio"
        "SOUND" -> "Tocar som"
        "KIT_NETHERITE" -> "Kit de netherita encantado"
        "MULTI" -> "Várias ações"
        else -> "Ação desconhecida"
    }
}

class GiftRuleEngine(private val rules: List<GiftRule> = GiftRuleStore.defaults()) {
    fun handle(event: LiveGiftEvent): GiftAction? {
        if (event.isCombo && !event.isStreakOver) return null
        val rule = rules.firstOrNull { it.giftName.trim().equals(event.giftName.trim(), ignoreCase = true) } ?: return null
        val commands = commandsFor(rule)
        if (commands.isEmpty()) return null
        return GiftAction(event.giftName, event.diamondTotal, commands.first(), commands)
    }
    private fun identifier(value: String): String? {
        val clean = value.trim().lowercase()
        return clean.takeIf { it.matches(Regex("[a-z0-9_.:-]{1,64}")) }
    }
    private fun kit(): List<String> {
        fun give(item: String, levels: String): String {
            val components = """{"minecraft:enchantments":{"levels":{$levels}}}"""
            return "give @p $item 1 0 $components"
        }
        return listOf(
            give("netherite_helmet", """"protection":4,"unbreaking":3,"mending":1"""),
            give("netherite_chestplate", """"protection":4,"unbreaking":3,"mending":1"""),
            give("netherite_leggings", """"protection":4,"unbreaking":3,"mending":1"""),
            give("netherite_boots", """"protection":4,"feather_falling":4,"unbreaking":3,"mending":1"""),
            give("netherite_sword", """"sharpness":5,"looting":3,"unbreaking":3,"mending":1"""),
            give("netherite_pickaxe", """"efficiency":5,"fortune":3,"unbreaking":3,"mending":1"""),
            give("netherite_axe", """"efficiency":5,"sharpness":5,"unbreaking":3,"mending":1"""),
            give("netherite_shovel", """"efficiency":5,"unbreaking":3,"mending":1""")
        )
    }
    private fun single(type: String, value: String, rawAmount: Int): List<String> {
        val amount = rawAmount.coerceIn(1, 64)
        val command = when (type) {
            "SUMMON" -> identifier(value)?.let { "summon " + it + " ~ ~1 ~" }
            "GIVE" -> identifier(value)?.let { "give @p " + it + " " + amount }
            "EFFECT" -> identifier(value)?.let { "effect @p " + it + " 10 " + (amount - 1).coerceAtMost(4) + " true" }
            "COMMAND" -> GiftRuleStore.commandForPreset(value)
            "CLEAR" -> "clear @p"
            "KILL" -> "kill @p"
            "HEAL" -> "effect @p instant_health 1 255 true"
            "FEED" -> "effect @p saturation 1 255 true"
            "LIGHTNING" -> "summon lightning_bolt ~ ~ ~"
            "SOUND" -> "playsound random.levelup @p"
            "KIT_NETHERITE" -> return kit()
            else -> null
        }
        return listOfNotNull(command)
    }
    private fun commandsFor(rule: GiftRule): List<String> {
        if (rule.actionType != "MULTI") return single(rule.actionType, rule.value, rule.amount)
        return try {
            val array = JSONArray(rule.value)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    addAll(single(item.optString("type"), item.optString("value"), item.optInt("amount", 1)))
                }
            }
        } catch (_: Exception) { emptyList() }
    }
}
