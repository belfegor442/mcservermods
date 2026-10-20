package com.gambling.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the mod's message keys on the server. Vanilla clients do not have
 * the mod's lang files, so every message sent to a player is translated here
 * with the language that client reported and sent as literal text. A language
 * without its own file falls back to any file sharing the same primary
 * language (es_mx to es_es) and finally to en_us; a key missing everywhere is
 * returned unchanged so broken messages stay visible instead of blank.
 */
public final class ServerTranslations {
    public static final String DEFAULT_LANGUAGE = "en_us";
    private static final String RESOURCE = "/assets/gambling/lang/%s.json";
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-lang");

    private static final Map<String, Map<String, String>> TABLES = load();

    private ServerTranslations() {
    }

    public static String translate(String key, String language, Object... args) {
        String template = tableFor(language).get(key);
        if (template == null) {
            template = tableFor(DEFAULT_LANGUAGE).get(key);
        }
        if (template == null) {
            return key;
        }
        return substitute(template, args);
    }

    private static Map<String, String> tableFor(String language) {
        String tag = normalize(language);
        Map<String, String> table = TABLES.get(tag);
        if (table != null) {
            return table;
        }
        int separator = tag.indexOf('_');
        String primary = separator > 0 ? tag.substring(0, separator) : tag;
        for (String candidate : TABLES.keySet()) {
            if (candidate.startsWith(primary + "_")) {
                return TABLES.get(candidate);
            }
        }
        Map<String, String> fallback = TABLES.get(DEFAULT_LANGUAGE);
        return fallback != null ? fallback : Map.of();
    }

    private static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * Substitutes %s placeholders against the original template positions, so
     * an argument that itself contains "%s" can never shift later arguments.
     */
    private static String substitute(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length() + 16);
        int copied = 0;
        int used = 0;
        while (used < args.length) {
            int at = template.indexOf("%s", copied);
            if (at < 0) {
                break;
            }
            out.append(template, copied, at);
            Object arg = args[used];
            out.append(arg == null ? "null" : String.valueOf(arg));
            copied = at + 2;
            used++;
        }
        out.append(template, copied, template.length());
        return out.toString();
    }

    private static Map<String, Map<String, String>> load() {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        for (String tag : List.of(DEFAULT_LANGUAGE, "es_es")) {
            String path = RESOURCE.formatted(tag);
            try (InputStream in = ServerTranslations.class.getResourceAsStream(path)) {
                if (in == null) {
                    LOGGER.error("Missing language resource {}.", path);
                    continue;
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                Map<String, String> entries = new HashMap<>();
                for (String key : root.keySet()) {
                    entries.put(key, root.get(key).getAsString());
                }
                tables.put(tag, entries);
            } catch (Exception e) {
                LOGGER.error("Could not load language {}.", tag, e);
            }
        }
        return tables;
    }
}
