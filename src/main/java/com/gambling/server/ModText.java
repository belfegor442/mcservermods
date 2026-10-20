package com.gambling.server;

import com.gambling.util.ServerTranslations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds chat components from the mod's keys entirely on the server, using
 * the language the player's client reported (console output uses en_us).
 * Vanilla clients therefore see finished text instead of raw translation keys.
 */
public final class ModText {
    private ModText() {
    }

    public static Component of(ServerPlayer player, String key, Object... args) {
        return Component.literal(ServerTranslations.translate(key, player.clientInformation().language(), args));
    }

    public static Component of(CommandSourceStack source, String key, Object... args) {
        ServerPlayer player = source.getPlayer();
        String language = player != null
                ? player.clientInformation().language()
                : ServerTranslations.DEFAULT_LANGUAGE;
        return Component.literal(ServerTranslations.translate(key, language, args));
    }
}
