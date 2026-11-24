package com.livecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobCatalogTest {
    @Test fun findsPortugueseAndEnglishMobNamesInsideComments() {
        assertEquals("creeper", MobCatalog.findMob("quero um CREEPER aqui"))
        assertEquals("zombie", MobCatalog.findMob("manda um zumbi"))
        assertEquals("cave_spider", MobCatalog.findMob("CAVE SPIDER"))
        assertEquals("copper_golem", MobCatalog.findMob("golem de cobre por favor"))
    }

    @Test fun prefersTheMostSpecificName() {
        assertEquals("cave_spider", MobCatalog.findMob("aranha das cavernas"))
        assertEquals("zombie_horse", MobCatalog.findMob("cavalo zumbi"))
    }

    @Test fun doesNotMatchPartialWords() {
        assertNull(MobCatalog.findMob("creepers"))
        assertNull(MobCatalog.findMob("quero um creeperzinho"))
    }

    @Test fun excludesBossesAndWarden() {
        assertNull(MobCatalog.findMob("ender dragon"))
        assertNull(MobCatalog.findMob("ender dragon"))
        assertNull(MobCatalog.findMob("wither"))
        assertNull(MobCatalog.findMob("warden"))
    }

    @Test fun recognizesRegularHostileAndPassiveMobs() {
        assertEquals("enderman", MobCatalog.findMob("enderman"))
        assertEquals("sheep", MobCatalog.findMob("ovelha"))
        assertEquals("happy_ghast", MobCatalog.findMob("happy ghast"))
    }
}
