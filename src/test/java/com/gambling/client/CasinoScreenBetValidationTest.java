package com.gambling.client;

import com.gambling.util.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CasinoScreenBetValidationTest {

    @Test
    void validBetPasses() {
        assertNull(CasinoScreen.validateBetKey("10", 1000L, true));
        assertNull(CasinoScreen.validateBetKey(" 25 ", 25L, true));
        assertNull(CasinoScreen.validateBetKey(String.valueOf(Validation.MAX_BET),
                Validation.MAX_BET, true));
    }

    @Test
    void balanceCheckSkippedBeforeProfileArrives() {
        assertNull(CasinoScreen.validateBetKey("1000000", 0L, false));
    }

    @Test
    void emptyOrNullBetIsRequired() {
        assertEquals("key.gambling.menu.error.required",
                CasinoScreen.validateBetKey(null, 100L, true));
        assertEquals("key.gambling.menu.error.required",
                CasinoScreen.validateBetKey("", 100L, true));
        assertEquals("key.gambling.menu.error.required",
                CasinoScreen.validateBetKey("   ", 100L, true));
    }

    @Test
    void nonNumericOrOutOfRangeBetIsInvalid() {
        assertEquals("key.gambling.menu.error.invalid",
                CasinoScreen.validateBetKey("abc", 100L, true));
        assertEquals("key.gambling.menu.error.invalid",
                CasinoScreen.validateBetKey("0", 100L, true));
        assertEquals("key.gambling.menu.error.invalid",
                CasinoScreen.validateBetKey("-5", 100L, true));
        assertEquals("key.gambling.menu.error.invalid",
                CasinoScreen.validateBetKey("10.5", 100L, true));
        // Overflows Long.parseLong and must not throw.
        assertEquals("key.gambling.menu.error.invalid",
                CasinoScreen.validateBetKey("99999999999999999999999", 100L, true));
    }

    @Test
    void betAboveServerMaximumIsTooHigh() {
        assertEquals("key.gambling.menu.error.too_high",
                CasinoScreen.validateBetKey(String.valueOf(Validation.MAX_BET + 1),
                        Long.MAX_VALUE, true));
    }

    @Test
    void betAboveBalanceIsInsufficient() {
        assertEquals("key.gambling.menu.error.insufficient",
                CasinoScreen.validateBetKey("100", 99L, true));
        assertNull(CasinoScreen.validateBetKey("100", 100L, true));
    }
}
