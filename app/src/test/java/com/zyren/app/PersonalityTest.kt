package com.zyren.app

import com.zyren.app.core.Personality
import org.junit.Assert.*
import org.junit.Test

class PersonalityTest {
    @Test fun humorOffForbidsJokes() {
        val d = Personality.directive(0, 0)
        assertTrue(d.contains("Şaka veya espri yapma"))
        assertFalse(d.contains("ciddi ol"))
    }
    @Test fun humorOnKeepsSeriousTopicSafety() {
        for (h in 1..3) assertTrue(Personality.directive(h, 0).contains("ciddi ol"))
    }
    @Test fun valuesAreClamped() {
        assertEquals(3, Personality.clampHumor(99)); assertEquals(0, Personality.clampHumor(-5))
        assertEquals(3, Personality.clampStyle(42))
        assertNotNull(Personality.directive(-1, 99))
    }
}
