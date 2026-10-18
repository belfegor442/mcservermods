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
}
