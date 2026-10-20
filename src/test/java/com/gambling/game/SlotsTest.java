package com.gambling.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsTest {
    private static int indexOf(String symbol) {
        for (int i = 0; i < Slots.SYMBOLS.length; i++) {
            if (Slots.SYMBOLS[i].equals(symbol)) {
                return i;
            }
        }
        throw new AssertionError("unknown symbol " + symbol);
    }

    @Test
    void sevenTriplePaysTwentyTimesStake() {
        int seven = indexOf("SEVEN");
        Slots.Outcome outcome = Slots.evaluate(seven, seven, seven);
        assertTrue(outcome.won());
        assertEquals(20L, outcome.multiplier());
        assertEquals("SEVEN | SEVEN | SEVEN — WIN x20", outcome.resultText());
    }

    @Test
    void otherTriplePaysEightTimesStake() {
        int cherry = indexOf("CHERRY");
        Slots.Outcome outcome = Slots.evaluate(cherry, cherry, cherry);
        assertTrue(outcome.won());
        assertEquals(8L, outcome.multiplier());
    }

    @Test
    void anyPairPaysTwiceStake() {
        int cherry = indexOf("CHERRY");
        int bell = indexOf("BELL");
        assertEquals(2L, Slots.evaluate(cherry, cherry, bell).multiplier());
        assertEquals(2L, Slots.evaluate(cherry, bell, cherry).multiplier());
        assertEquals(2L, Slots.evaluate(bell, cherry, cherry).multiplier());
    }

    @Test
    void allDistinctLoses() {
        Slots.Outcome outcome = Slots.evaluate(indexOf("CHERRY"), indexOf("BELL"), indexOf("DIAMOND"));
        assertFalse(outcome.won());
        assertEquals(1L, outcome.multiplier());
        assertTrue(outcome.resultText().contains("LOSS"));
    }

    @Test
    void everyComboFollowsThePayoutRules() {
        int n = Slots.SYMBOLS.length;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                for (int c = 0; c < n; c++) {
                    Slots.Outcome outcome = Slots.evaluate(a, b, c);
                    boolean triple = a == b && b == c;
                    boolean pair = a == b || a == c || b == c;
                    assertEquals(pair, outcome.won());
                    long expected = triple ? (a == indexOf("SEVEN") ? 20L : 8L) : pair ? 2L : 1L;
                    assertEquals(expected, outcome.multiplier());
                }
            }
        }
    }

    @Test
    void returnToPlayerKeepsAHouseEdge() {
        int n = Slots.SYMBOLS.length;
        long totalPayout = 0;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                for (int c = 0; c < n; c++) {
                    Slots.Outcome outcome = Slots.evaluate(a, b, c);
                    totalPayout += outcome.won() ? outcome.multiplier() : 0;
                }
            }
        }
        double rtp = totalPayout / (double) (n * n * n);
        assertTrue(rtp > 0.90 && rtp < 1.0,
                "slots must keep a house edge, expected RTP in (0.90, 1.0) but was " + rtp);
    }

    @Test
    void rollProducesKnownSymbols() {
        Random rng = new Random(42);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Slots.Outcome outcome = Slots.roll(rng);
            for (String symbol : new String[]{outcome.first(), outcome.second(), outcome.third()}) {
                assertTrue(Set.of(Slots.SYMBOLS).contains(symbol));
                seen.add(symbol);
            }
        }
        assertTrue(seen.size() > 1, "a seeded RNG should vary the symbols");
    }
}
