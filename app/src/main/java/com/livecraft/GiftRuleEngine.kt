package com.livecraft

/**
 * Converts finalized TikTok gift events into Minecraft commands.
 *
 * The TikTok connector must call [handle] only for a finalized gift event.
 * Combo gifts are ignored until the connector marks the streak as finished.
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
    private val roseNames: Set<String> = setOf("rose"),
    private val dinosaurNames: Set<String> = setOf("dinosaur")
) {
    /**
     * Returns null for unfinished combo events or gifts without a configured action.
     * The threshold is strictly greater than 100 diamonds, as requested.
     */
    fun handle(event: LiveGiftEvent): GiftAction? {
        if (event.isCombo && !event.isStreakOver) return null

        val normalizedName = event.giftName.trim().lowercase()
        // Named special gifts take priority over the generic value rule.
        val command = when {
            normalizedName in roseNames -> "summon zombie ~ ~1 ~"
            normalizedName in dinosaurNames -> "summon wither ~ ~1 ~"
            event.diamondTotal > 100L -> "kill @p"
            else -> return null
        }

        return GiftAction(
            giftName = event.giftName,
            diamondTotal = event.diamondTotal,
            command = command
        )
    }
}
