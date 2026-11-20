package com.livecraft

/**
 * Converts finalized TikTok gift events into Minecraft commands.
 * Combo gifts are processed only after the streak ends.
 */
data class LiveGiftEvent(
    val giftName: String,
    val diamondTotal: Long,
    val repeatCount: Int = 1,
    val isCombo: Boolean = false,
    val isStreakOver: Boolean = true
)

data class GiftAction(
    val giftName: String,
    val diamondTotal: Long,
    val command: String
)

class GiftRuleEngine(
    private val roseNames: Set<String> = setOf("rose", "rosa"),
    private val whiteRoseNames: Set<String> = setOf("white rose", "rosa branca"),
    private val miniDinoNames: Set<String> = setOf("mini dino"),
    private val capybaraNames: Set<String> = setOf("capybara", "capivara"),
    private val disabledWorldDeleteNames: Set<String> = setOf("hat and mustache", "chapéu e bigode", "chapeu e bigode")
) {
    /**
     * Returns null for unfinished combos, unsupported destructive actions,
     * and gifts without a configured action.
     */
    fun handle(event: LiveGiftEvent): GiftAction? {
        if (event.isCombo && !event.isStreakOver) return null

        val normalizedName = event.giftName.trim().lowercase()
        val command = when {
            normalizedName in roseNames -> "summon zombie ~ ~1 ~"
            normalizedName in whiteRoseNames -> "give @p golden_apple 1"
            normalizedName in miniDinoNames -> "clear @p"
            normalizedName in capybaraNames -> "kill @p"
            // Deleting a world is not a Minecraft command and is intentionally disabled.
            normalizedName in disabledWorldDeleteNames -> return null
            else -> return null
        }

        return GiftAction(event.giftName, event.diamondTotal, command)
    }
}
