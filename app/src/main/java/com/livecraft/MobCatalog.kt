package com.livecraft

import java.text.Normalizer
import java.util.Locale

/**
 * Resolves Minecraft mob names mentioned in TikTok comments to Bedrock summon identifiers.
 * Bosses and the Warden are intentionally excluded.
 */
object MobCatalog {
    private val aliases: List<Pair<String, String>> = listOf(
        "aranha das cavernas" to "cave_spider", "cave spider" to "cave_spider",
        "aranha" to "spider", "spider" to "spider",
        "aranha venenosa" to "cave_spider",
        "cavalo esqueleto" to "skeleton_horse", "skeleton horse" to "skeleton_horse",
        "cavalo zumbi" to "zombie_horse", "zombie horse" to "zombie_horse",
        "cavalo" to "horse", "horse" to "horse",
        "golem de cobre" to "copper_golem", "copper golem" to "copper_golem",
        "golem de ferro" to "iron_golem", "iron golem" to "iron_golem",
        "golem de neve" to "snow_golem", "snow golem" to "snow_golem",
        "ghast feliz" to "happy_ghast", "happy ghast" to "happy_ghast",
        "ghast" to "ghast",
        "nautilo zumbi" to "zombie_nautilus", "zombie nautilus" to "zombie_nautilus",
        "nautilo" to "nautilus", "nautilus" to "nautilus",
        "peixe tropical" to "tropical_fish", "tropical fish" to "tropical_fish",
        "peixe baiacu" to "pufferfish", "baiacu" to "pufferfish", "pufferfish" to "pufferfish",
        "peixe-palhaco" to "tropical_fish", "peixe palhaco" to "tropical_fish",
        "urso polar" to "polar_bear", "polar bear" to "polar_bear",
        "comerciante errante" to "wandering_trader", "wandering trader" to "wandering_trader",
        "lhama comerciante" to "trader_llama", "trader llama" to "trader_llama",
        "aldeao zumbi" to "zombie_villager", "zombie villager" to "zombie_villager",
        "piglin bruto" to "piglin_brute", "piglin brute" to "piglin_brute",
        "piglin zumbi" to "zombified_piglin", "zombified piglin" to "zombified_piglin",
        "esqueleto wither" to "wither_skeleton", "wither skeleton" to "wither_skeleton",
        "cubo de magma" to "magma_cube", "magma cube" to "magma_cube",
        "lula brilhante" to "glow_squid", "glow squid" to "glow_squid",
        "vaca cogumelo" to "mooshroom", "mooshroom" to "mooshroom",
        "abelha" to "bee", "bee" to "bee",
        "allay" to "allay", "tatu" to "armadillo", "armadillo" to "armadillo",
        "axolote" to "axolotl", "axolotl" to "axolotl",
        "morcego" to "bat", "bat" to "bat",
        "blaze" to "blaze", "bogged" to "bogged", "breeze" to "breeze",
        "camelo husk" to "camel_husk", "camel husk" to "camel_husk",
        "camelo" to "camel", "camel" to "camel",
        "gato" to "cat", "cat" to "cat",
        "galinha" to "chicken", "chicken" to "chicken",
        "bacalhau" to "cod", "cod" to "cod",
        "vaca" to "cow", "cow" to "cow",
        "creaking" to "creaking", "creeper" to "creeper",
        "golfinho" to "dolphin", "dolphin" to "dolphin",
        "burro" to "donkey", "donkey" to "donkey",
        "afogado" to "drowned", "drowned" to "drowned",
        "guardiao anciao" to "elder_guardian", "elder guardian" to "elder_guardian",
        "enderman" to "enderman", "endermite" to "endermite",
        "evocador" to "evoker", "evoker" to "evoker",
        "raposa" to "fox", "fox" to "fox",
        "sapo" to "frog", "frog" to "frog",
        "bruxa do pantano" to "witch", "witch" to "witch",
        "cabrito" to "goat", "goat" to "goat",
        "lula" to "squid", "squid" to "squid",
        "guardiao" to "guardian", "guardian" to "guardian",
        "hoglin" to "hoglin", "zoglin" to "zoglin",
        "javali" to "hoglin",
        "porco" to "pig", "pig" to "pig",
        "husk" to "husk", "zumbi" to "zombie", "zombie" to "zombie",
        "aldeao" to "villager", "villager" to "villager",
        "vex" to "vex", "vindicador" to "vindicator", "vindicator" to "vindicator",
        "saqueador" to "pillager", "pillager" to "pillager",
        "devastador" to "ravager", "ravager" to "ravager",
        "vendedor ambulante" to "wandering_trader",
        "bruxa" to "witch", "witch" to "witch",
        "esqueleto" to "skeleton", "skeleton" to "skeleton",
        "errante" to "stray", "stray" to "stray",
        "afogado" to "drowned",
        "aranha" to "spider",
        "fantasma" to "phantom", "phantom" to "phantom",
        "endermite" to "endermite", "silverfish" to "silverfish",
        "traça" to "silverfish", "traca" to "silverfish",
        "slime" to "slime", "lodo" to "slime",
        "shulker" to "shulker",
        "sniffer" to "sniffer", "farejador" to "sniffer",
        "strider" to "strider", "lavagante" to "strider",
        "girino" to "tadpole", "tadpole" to "tadpole",
        "tartaruga" to "turtle", "turtle" to "turtle",
        "salmão" to "salmon", "salmao" to "salmon", "salmon" to "salmon",
        "bacalhau" to "cod",
        "coelho" to "rabbit", "rabbit" to "rabbit",
        "ovelha" to "sheep", "sheep" to "sheep",
        "papagaio" to "parrot", "parrot" to "parrot",
        "panda" to "panda", "urso panda" to "panda",
        "ocelote" to "ocelot", "ocelot" to "ocelot",
        "mula" to "mule", "mule" to "mule",
        "lhama" to "llama", "llama" to "llama",
        "vaca cogumelo" to "mooshroom",
        "cabra" to "goat",
        "pufferfish" to "pufferfish",
        "zumbi mumificado" to "husk",
        "parched" to "parched",
        "zumbi do deserto" to "husk",
        "zumbi piglin" to "zombified_piglin",
        "vaca" to "cow",
        "lobo" to "wolf", "wolf" to "wolf",
        "pinguim" to "penguin"
    )

    private val normalizedAliases = aliases
        .map { normalize(it.first) to it.second }
        .distinct()
        .sortedByDescending { it.first.length }

    fun findMob(comment: String): String? {
        val normalized = " " + normalize(comment) + " "
        for ((alias, id) in normalizedAliases) {
            if (normalized.contains(" $alias ")) return id
        }
        return null
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
