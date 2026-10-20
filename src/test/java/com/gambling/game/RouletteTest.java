package com.gambling.game;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouletteTest {
    @Test
    void zeroIsGreen() {
        assertEquals(Roulette.GREEN, Roulette.colorFor(0));
        Roulette.Outcome outcome = Roulette.evaluate(0, Roulette.GREEN);
        assertTrue(outcome.won());
        assertEquals(36L, outcome.multiplier());
    }

    @Test
    void knownRedAndBlackNumbers() {
        assertEquals(Roulette.RED, Roulette.colorFor(1));
        assertEquals(Roulette.RED, Roulette.colorFor(36));
        assertEquals(Roulette.BLACK, Roulette.colorFor(2));
        assertEquals(Roulette.BLACK, Roulette.colorFor(35));
        assertEquals(18, Roulette.RED_NUMBERS.size());
    }

    @Test
    void evenMoneyWinPaysTwiceStake() {
        Roulette.Outcome outcome = Roulette.evaluate(1, Roulette.RED);
        assertTrue(outcome.won());
        assertEquals(2L, outcome.multiplier());
        assertTrue(outcome.resultText().contains("1 RED"));
    }

    @Test
    void wrongColorLoses() {
        Roulette.Outcome outcome = Roulette.evaluate(1, Roulette.BLACK);
        assertFalse(outcome.won());
        assertEquals(1L, outcome.multiplier());
    }

    @Test
    void spinStaysOnTheWheel() {
        Random rng = new Random(7);
        for (int i = 0; i < 1000; i++) {
            Roulette.Outcome outcome = Roulette.spin(Roulette.RED, rng);
            assertTrue(outcome.number() >= 0 && outcome.number() <= 36);
            assertEquals(Roulette.colorFor(outcome.number()), outcome.resultColor());
        }
    }

    @Test
    void everyWheelPositionHasExactlyOneColor() {
        int red = 0;
        int black = 0;
        int green = 0;
        for (int number = 0; number <= 36; number++) {
            String color = Roulette.colorFor(number);
            switch (color) {
                case Roulette.RED -> red++;
                case Roulette.BLACK -> black++;
                case Roulette.GREEN -> green++;
                default -> throw new AssertionError("unexpected color " + color);
            }
        }
        assertEquals(18, red);
        assertEquals(18, black);
        assertEquals(1, green);
    }
}
