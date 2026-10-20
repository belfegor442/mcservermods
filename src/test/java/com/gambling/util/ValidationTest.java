package com.gambling.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {
    @Test
    void sanitizeGameTypeAcceptsValid() {
        assertEquals("coinflip", Validation.sanitizeGameType("coinflip"));
        assertEquals("Black_Jack-2", Validation.sanitizeGameType("  Black_Jack-2  "));
    }

    @Test
    void sanitizeGameTypeRejectsInvalid() {
        assertNull(Validation.sanitizeGameType(null));
        assertNull(Validation.sanitizeGameType(""));
        assertNull(Validation.sanitizeGameType("   "));
        assertNull(Validation.sanitizeGameType("bad type"));
        assertNull(Validation.sanitizeGameType("bad!type"));
        assertNull(Validation.sanitizeGameType("x".repeat(33)));
    }

    @Test
    void sanitizeResultStripsControlCharsAndTruncates() {
        assertEquals("", Validation.sanitizeResult(null));
        assertEquals("", Validation.sanitizeResult("   "));
        assertEquals("hello", Validation.sanitizeResult("hello"));
        String withControl = "a" + (char) 7 + "b";
        assertEquals("ab", Validation.sanitizeResult(withControl));
        assertEquals(Validation.MAX_RESULT_LENGTH, Validation.sanitizeResult("y".repeat(500)).length());
    }

    @Test
    void betValidation() {
        assertTrue(Validation.isValidBet(1));
        assertTrue(Validation.isValidBet(Validation.MAX_BET));
        assertFalse(Validation.isValidBet(0));
        assertFalse(Validation.isValidBet(-1));
        assertFalse(Validation.isValidBet(Validation.MAX_BET + 1));
    }

    @Test
    void adminAmountValidation() {
        assertTrue(Validation.isValidAdminAmount(0, true));
        assertFalse(Validation.isValidAdminAmount(0, false));
        assertTrue(Validation.isValidAdminAmount(Validation.MAX_ADMIN_AMOUNT, false));
        assertFalse(Validation.isValidAdminAmount(Validation.MAX_ADMIN_AMOUNT + 1, true));
        assertFalse(Validation.isValidAdminAmount(-1, true));
    }

    @Test
    void safeAddClampsAtMaxValue() {
        assertEquals(Long.MAX_VALUE, Validation.safeAdd(Long.MAX_VALUE, 1));
        assertEquals(5L, Validation.safeAdd(2L, 3L));
        assertEquals(0L, Validation.clampNonNegative(-5));
        assertEquals(7L, Validation.clampNonNegative(7));
    }

    @Test
    void safeAddHandlesNegativeOperands() {
        assertEquals(Long.MIN_VALUE, Validation.safeAdd(Long.MIN_VALUE, -1));
        assertEquals(-5L, Validation.safeAdd(-2L, -3L));
        assertEquals(1L, Validation.safeAdd(5L, -4L));
    }

    @Test
    void chipsFormatIsReadable() {
        assertEquals("0", ChipsFormat.format(0));
        assertEquals("0", ChipsFormat.format(-100));
        assertEquals("1", ChipsFormat.format(1));
        assertEquals("1,234", ChipsFormat.format(1234));
        assertEquals("1,000,000", ChipsFormat.format(1_000_000));
        assertEquals("9,223,372,036,854,775,807", ChipsFormat.format(Long.MAX_VALUE));
    }
}
