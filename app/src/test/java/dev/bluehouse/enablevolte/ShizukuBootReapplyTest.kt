package dev.bluehouse.enablevolte

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuBootReapplyTest {
    @Test fun reappliesOnceAfterEachReboot() {
        assertTrue(ShizukuBootReapply.shouldReapply(true, true, appliedBoot = 41, currentBoot = 42))
        assertFalse(ShizukuBootReapply.shouldReapply(true, true, appliedBoot = 42, currentBoot = 42))
    }

    @Test fun reappliesWhenNeverAppliedBefore() {
        assertTrue(ShizukuBootReapply.shouldReapply(true, true, appliedBoot = Int.MIN_VALUE, currentBoot = 7))
    }

    @Test fun unknownBootCountStillReapplies() {
        assertTrue(ShizukuBootReapply.shouldReapply(true, true, appliedBoot = -1, currentBoot = -1))
    }

    @Test fun neverReappliesWhenOffOrInRootMode() {
        assertFalse(ShizukuBootReapply.shouldReapply(false, true, appliedBoot = 1, currentBoot = 2))
        assertFalse(ShizukuBootReapply.shouldReapply(true, false, appliedBoot = 1, currentBoot = 2))
    }
}
