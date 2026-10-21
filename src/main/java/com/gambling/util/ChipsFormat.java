package com.gambling.util;

import java.util.Locale;

public final class ChipsFormat {
    private ChipsFormat() {
    }

    public static String format(long chips) {
        if (chips <= 0) {
            return "0";
        }
        return String.format(Locale.ROOT, "%,d", chips);
    }

    /** Compact sidebar form: 999 → "999", 1500 → "1.5K", 2_500_000_000 → "2.5B". */
    public static String compact(long chips) {
        if (chips <= 0) {
            return "0";
        }
        long[] divisors = {1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L};
        String[] suffixes = {"K", "M", "B", "T"};
        for (int i = divisors.length - 1; i >= 0; i--) {
            if (chips >= divisors[i]) {
                double scaled = (double) chips / divisors[i];
                double rounded = Math.round(scaled * 10.0) / 10.0;
                if (rounded >= 1000.0 && i + 1 < divisors.length) {
                    i++;
                    scaled = (double) chips / divisors[i];
                }
                return String.format(Locale.ROOT, "%.1f%s", scaled, suffixes[i]);
            }
        }
        return Long.toString(chips);
    }
}
