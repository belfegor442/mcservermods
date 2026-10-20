package com.gambling.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTranslationsTest {

    @Test
    void resolvesExactEnglishLanguage() {
        assertEquals("Available",
                ServerTranslations.translate("key.gambling.hud.state.available", "en_us"));
    }

    @Test
    void resolvesExactSpanishLanguage() {
        assertEquals("Disponible",
                ServerTranslations.translate("key.gambling.hud.state.available", "es_es"));
    }

    @Test
    void regionalVariantFallsBackToSamePrimaryLanguage() {
        assertEquals("Disponible",
                ServerTranslations.translate("key.gambling.hud.state.available", "es_mx"));
        assertEquals("Available",
                ServerTranslations.translate("key.gambling.hud.state.available", "en_gb"));
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        assertEquals("Available",
                ServerTranslations.translate("key.gambling.hud.state.available", "fr_fr"));
    }

    @Test
    void missingKeyReturnsKeyUnchanged() {
        assertEquals("key.gambling.does.not.exist",
                ServerTranslations.translate("key.gambling.does.not.exist", "en_us"));
    }

    @Test
    void nullOrBlankLanguageUsesEnglish() {
        assertEquals("Available",
                ServerTranslations.translate("key.gambling.hud.state.available", null));
        assertEquals("Available",
                ServerTranslations.translate("key.gambling.hud.state.available", "  "));
    }

    @Test
    void substitutesArgumentsInOrder() {
        assertEquals("Lv 7",
                ServerTranslations.translate("key.gambling.hud.level", "en_us", "7"));
        assertEquals("Nv 7",
                ServerTranslations.translate("key.gambling.hud.level", "es_es", "7"));
    }

    @Test
    void argumentContainingPlaceholderDoesNotShiftLaterArguments() {
        // en_us: "%s has %s CHIPS (Level %s)" - the first arg itself holds a "%s".
        assertEquals("a%s has b CHIPS (Level c)",
                ServerTranslations.translate("key.gambling.cmd.balance.other", "en_us", "a%s", "b", "c"));
    }

    @Test
    void missingArgumentLeavesRemainingPlaceholderVisible() {
        // en_us needs three args; only two supplied: the last "%s" must stay visible.
        assertEquals("X has Y CHIPS (Level %s)",
                ServerTranslations.translate("key.gambling.cmd.balance.other", "en_us", "X", "Y"));
    }

    @Test
    void extraArgumentsAreIgnored() {
        assertEquals("Lv 7",
                ServerTranslations.translate("key.gambling.hud.level", "en_us", "7", "ignored", "extra"));
    }

    @Test
    void englishAndSpanishDefineTheSameKeys() {
        try (var en = ServerTranslations.class.getResourceAsStream("/assets/gambling/lang/en_us.json");
             var es = ServerTranslations.class.getResourceAsStream("/assets/gambling/lang/es_es.json")) {
            assertTrue(en != null && es != null, "lang resources must be on the test classpath");
            Set<String> enKeys = keysOf(en);
            Set<String> esKeys = keysOf(es);
            assertEquals(enKeys, esKeys, "es_es.json must define exactly the same keys as en_us.json");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void everyValueHasMatchingPlaceholderCountAcrossLanguages() {
        try (var en = ServerTranslations.class.getResourceAsStream("/assets/gambling/lang/en_us.json");
             var es = ServerTranslations.class.getResourceAsStream("/assets/gambling/lang/es_es.json")) {
            Map<String, String> enValues = valuesOf(en);
            Map<String, String> esValues = valuesOf(es);
            for (String key : enValues.keySet()) {
                assertEquals(placeholderCount(enValues.get(key)), placeholderCount(esValues.get(key)),
                        "placeholder count differs for " + key);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void englishValuesContainNoOrphanPercentTokens() {
        try (var en = ServerTranslations.class.getResourceAsStream("/assets/gambling/lang/en_us.json")) {
            for (Map.Entry<String, String> entry : valuesOf(en).entrySet()) {
                String withoutSubstitutions = entry.getValue().replace("%s", "");
                assertFalse(withoutSubstitutions.contains("%"),
                        "orphan '%' token (not %s) in " + entry.getKey());
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Set<String> keysOf(java.io.InputStream in) throws Exception {
        return valuesOf(in).keySet();
    }

    private static Map<String, String> valuesOf(java.io.InputStream in) throws Exception {
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String key : root.keySet()) {
            values.put(key, root.get(key).getAsString());
        }
        return values;
    }

    private static int placeholderCount(String value) {
        int count = 0;
        int at = 0;
        while ((at = value.indexOf("%s", at)) >= 0) {
            count++;
            at += 2;
        }
        return count;
    }
}
