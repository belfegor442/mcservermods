package com.gambling.game;

import java.util.random.RandomGenerator;

/**
 * Pure three-reel slots logic. Seven uniform symbols give a return-to-player
 * of about 93.3% (SEVEN triple 20x, other triples 8x, any pair 2x), which
 * keeps a small house edge comparable to the roulette wheel.
 */
public final class Slots {
    public static final String[] SYMBOLS = {"CHERRY", "BELL", "DIAMOND", "CROWN", "STAR", "SEVEN", "CLOVER"};
    private static final int SEVEN_INDEX = 5;
    private static final long TRIPLE_SEVEN_MULTIPLIER = 20L;
    private static final long TRIPLE_MULTIPLIER = 8L;
    private static final long PAIR_MULTIPLIER = 2L;
    private static final long LOSS_MULTIPLIER = 1L;

    private Slots() {
    }

    public record Outcome(String first, String second, String third, boolean won, long multiplier) {
        public String resultText() {
            return first + " | " + second + " | " + third
                    + (won ? " — WIN x" + multiplier : " — LOSS");
        }
    }

    public static Outcome roll(RandomGenerator rng) {
        return evaluate(rng.nextInt(SYMBOLS.length),
                rng.nextInt(SYMBOLS.length),
                rng.nextInt(SYMBOLS.length));
    }

    public static Outcome evaluate(int firstIndex, int secondIndex, int thirdIndex) {
        String first = SYMBOLS[firstIndex];
        String second = SYMBOLS[secondIndex];
        String third = SYMBOLS[thirdIndex];
        boolean triple = firstIndex == secondIndex && secondIndex == thirdIndex;
        boolean pair = firstIndex == secondIndex || firstIndex == thirdIndex || secondIndex == thirdIndex;
        boolean won = pair;
        long multiplier;
        if (triple) {
            multiplier = firstIndex == SEVEN_INDEX ? TRIPLE_SEVEN_MULTIPLIER : TRIPLE_MULTIPLIER;
        } else if (pair) {
            multiplier = PAIR_MULTIPLIER;
        } else {
            multiplier = LOSS_MULTIPLIER;
        }
        return new Outcome(first, second, third, won, multiplier);
    }
}
