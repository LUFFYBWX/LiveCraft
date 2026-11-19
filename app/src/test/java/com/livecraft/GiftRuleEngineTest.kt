package com.livecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiftRuleEngineTest {
    private val engine = GiftRuleEngine()

    @Test fun roseSummonsOneZombie() {
        assertEquals(
            "summon zombie ~ ~1 ~",
            engine.handle(LiveGiftEvent("Rose", 1))?.command
        )
    }

    @Test fun dinosaurSummonsWither() {
        assertEquals(
            "summon wither ~ ~1 ~",
            engine.handle(LiveGiftEvent("Dinosaur", 299))?.command
        )
    }

    @Test fun giftsAboveOneHundredDiamondsKillNearestPlayer() {
        assertEquals(
            "kill @p",
            engine.handle(LiveGiftEvent("Any gift", 101))?.command
        )
    }

    @Test fun exactlyOneHundredDiamondsDoesNotTriggerKill() {
        assertNull(engine.handle(LiveGiftEvent("Any gift", 100)))
    }

    @Test fun unfinishedComboIsIgnored() {
        assertNull(
            engine.handle(
                LiveGiftEvent("Rose", 1, repeatCount = 4, isCombo = true, isStreakOver = false)
            )
        )
    }

    @Test fun finalizedComboTriggersOnlyOneAction() {
        assertEquals(
            "summon zombie ~ ~1 ~",
            engine.handle(
                LiveGiftEvent("Rose", 4, repeatCount = 4, isCombo = true, isStreakOver = true)
            )?.command
        )
    }
}
