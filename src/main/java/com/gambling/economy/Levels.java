package com.gambling.economy;

public final class Levels {
    public static final int MAX_LEVEL = 10_000;

    private Levels() {
    }

    public static int levelForXp(long xp) {
        if (xp <= 0) {
            return 1;
        }
        long level = 1L + (long) Math.floor(Math.sqrt(xp / 100.0));
        return (int) Math.min(MAX_LEVEL, level);
    }
}
