package com.gambling.game;

import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Pure single-zero European roulette logic. The wheel has 37 slots, so both
 * even-money bets and green pay the real casino odds (return-to-player about
 * 97.3%).
 */
public final class Roulette {
    public static final Set<Integer> RED_NUMBERS =
            Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    public static final String RED = "red";
    public static final String BLACK = "black";
    public static final String GREEN = "green";
    private static final int WHEEL_SIZE = 37;
    private static final long GREEN_MULTIPLIER = 36L;
    private static final long EVEN_MONEY_MULTIPLIER = 2L;
    private static final long LOSS_MULTIPLIER = 1L;

    private Roulette() {
    }

    public record Outcome(int number, String resultColor, String chosenColor, boolean won, long multiplier) {
        public String resultText() {
            return number + " " + resultColor.toUpperCase(java.util.Locale.ROOT)
                    + (won ? " — WIN" : " — LOSS");
        }
    }

    public static Outcome spin(String chosenColor, RandomGenerator rng) {
        return evaluate(rng.nextInt(WHEEL_SIZE), chosenColor);
    }

    public static Outcome evaluate(int number, String chosenColor) {
        String resultColor = colorFor(number);
        boolean won = chosenColor != null && chosenColor.equals(resultColor);
        long multiplier;
        if (!won) {
            multiplier = LOSS_MULTIPLIER;
        } else {
            multiplier = GREEN.equals(chosenColor) ? GREEN_MULTIPLIER : EVEN_MONEY_MULTIPLIER;
        }
        return new Outcome(number, resultColor, chosenColor == null ? "" : chosenColor, won, multiplier);
    }

    public static String colorFor(int number) {
        if (number == 0) {
            return GREEN;
        }
        return RED_NUMBERS.contains(number) ? RED : BLACK;
    }
}
