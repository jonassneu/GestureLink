package de.gesturelink.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {
    @Test fun smoothsAndEventuallyApproachesStep() {
        val filter = OneEuroFilter(minCutoff = 1.0, beta = 0.0)
        assertEquals(0.0, filter.filter(0.0, 0.0), 0.0)
        val first = filter.filter(1.0, 1.0 / 60.0)
        var value = first
        repeat(180) { value = filter.filter(1.0, (it + 2) / 60.0) }
        assertTrue(first in 0.0..1.0)
        assertTrue(value > 0.99)
    }

    @Test fun resetRemovesOldState() {
        val filter = OneEuroFilter()
        filter.filter(0.0, 0.0)
        filter.filter(1.0, 0.01)
        filter.reset()
        assertEquals(5.0, filter.filter(5.0, 1.0), 0.0)
    }
}

