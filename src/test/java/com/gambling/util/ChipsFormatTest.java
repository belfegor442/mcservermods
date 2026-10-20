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
}
