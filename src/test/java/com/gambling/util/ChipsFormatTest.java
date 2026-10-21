package com.gambling.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChipsFormatTest {
    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void formatsThousandsWithCommas() {
        assertEquals("1,000", ChipsFormat.format(1000));
        assertEquals("1,234,567", ChipsFormat.format(1_234_567));
        assertEquals("999", ChipsFormat.format(999));
        assertEquals("1", ChipsFormat.format(1));
    }

    @Test
    void zeroAndNegativeValuesRenderAsZero() {
        assertEquals("0", ChipsFormat.format(0));
        assertEquals("0", ChipsFormat.format(-1));
        assertEquals("0", ChipsFormat.format(Long.MIN_VALUE));
    }

    @Test
    void maximumValueFormatsWithoutOverflow() {
        assertEquals(String.format(Locale.ROOT, "%,d", Long.MAX_VALUE),
                ChipsFormat.format(Long.MAX_VALUE));
    }

    @Test
    void separatorIsLocaleIndependent() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("1,000", ChipsFormat.format(1000),
                "German locales must not switch to '.' as the group separator");

        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals("1,000", ChipsFormat.format(1000),
                "the Turkish locale must not affect the output");
    }

    @Test
    void compactUsesKmbtSuffixes() {
        assertEquals("0", ChipsFormat.compact(0));
        assertEquals("0", ChipsFormat.compact(-5));
        assertEquals("999", ChipsFormat.compact(999));
        assertEquals("1.0K", ChipsFormat.compact(1_000));
        assertEquals("1.5K", ChipsFormat.compact(1_500));
        assertEquals("12.3K", ChipsFormat.compact(12_345));
        assertEquals("1.0M", ChipsFormat.compact(999_999),
                "values that round to 1000 must promote to the next unit");
        assertEquals("1.0M", ChipsFormat.compact(1_000_000));
        assertEquals("2.5B", ChipsFormat.compact(2_500_000_000L));
        assertEquals("1.0T", ChipsFormat.compact(1_000_000_000_000L));
    }

    @Test
    void compactIsLocaleIndependent() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("1.5K", ChipsFormat.compact(1_500));
    }
}
