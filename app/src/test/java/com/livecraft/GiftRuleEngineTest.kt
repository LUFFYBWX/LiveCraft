package com.livecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiftRuleEngineTest {
    private val engine = GiftRuleEngine()

    @Test fun roseSummonsOneZombie() {
        assertEquals("summon zombie ~ ~1 ~", engine.handle(LiveGiftEvent("Rose", 1))?.command)
        assertEquals("summon zombie ~ ~1 ~", engine.handle(LiveGiftEvent("Rosa", 1))?.command)
    }

    @Test fun whiteRoseGivesGoldenApple() {
        assertEquals("give @p golden_apple 1", engine.handle(LiveGiftEvent("Rosa Branca", 1))?.command)
    }

    @Test fun miniDinoClearsInventory() {
        assertEquals("clear @p", engine.handle(LiveGiftEvent("Mini Dino", 1))?.command)
    }

    @Test fun capybaraKillsNearestPlayer() {
        assertEquals("kill @p", engine.handle(LiveGiftEvent("Capivara", 1))?.command)
    }

    @Test fun worldDeleteGiftIsSafelyDisabled() {
        assertNull(engine.handle(LiveGiftEvent("Chapéu e Bigode", 1)))
    }

    @Test fun oldTestGiftsAndGenericCoinRuleAreRemoved() {
        assertNull(engine.handle(LiveGiftEvent("Dinosaur", 299)))
        assertNull(engine.handle(LiveGiftEvent("Any gift", 101)))
    }

    @Test fun customGiftCanRunConfiguredAction() {
        val custom = GiftRuleEngine(listOf(GiftRule("Galaxy", "GIVE", "diamond", 12)))
        assertEquals("give @p diamond 12", custom.handle(LiveGiftEvent("Galaxy", 5))?.command)
    }

    @Test fun customEffectUsesSelectedLevel() {
        val custom = GiftRuleEngine(listOf(GiftRule("Magic", "EFFECT", "speed", 3)))
        assertEquals("effect @p speed 10 2 true", custom.handle(LiveGiftEvent("Magic", 5))?.command)
    }

    @Test fun invalidMinecraftIdentifierIsRejected() {
        val custom = GiftRuleEngine(listOf(GiftRule("Bad", "GIVE", "diamond;kill @a", 1)))
        assertNull(custom.handle(LiveGiftEvent("Bad", 5)))
    }

    @Test fun presetCommandCanChangeWorldTime() {
        val custom = GiftRuleEngine(listOf(GiftRule("Night", "COMMAND", "time_night")))
        assertEquals("time set night", custom.handle(LiveGiftEvent("Night", 5))?.command)
    }

    @Test fun presetCommandRejectsUnknownKeys() {
        val custom = GiftRuleEngine(listOf(GiftRule("Bad", "COMMAND", "kill_all")))
        assertNull(custom.handle(LiveGiftEvent("Bad", 5)))
    }

    @Test fun unfinishedComboIsIgnored() {
        assertNull(engine.handle(LiveGiftEvent("Rose", 1, repeatCount = 4, isCombo = true, isStreakOver = false)))
    }
}
