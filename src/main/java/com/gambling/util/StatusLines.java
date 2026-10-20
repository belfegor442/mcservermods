package com.gambling.util;

import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-text status lines for the server-driven sidebar HUD. Language is
 * resolved by ServerTranslations; styling happens in PlayerSidebar.
 */
public final class StatusLines {
    private StatusLines() {
    }

    public static List<String> build(PlayerProfile profile, GameSession session, String language) {
        List<String> lines = new ArrayList<>(6);
        lines.add("◆ " + ChipsFormat.format(Validation.clampNonNegative(profile.chips()))
                + " " + ServerTranslations.translate("key.gambling.hud.chips", language));
        lines.add(ServerTranslations.translate("key.gambling.hud.level", language,
                String.valueOf(profile.level())));
        String stateId = session != null ? session.state().id() : "available";
        lines.add(ServerTranslations.translate("key.gambling.hud.state", language,
                ServerTranslations.translate("key.gambling.hud.state." + stateId, language)));
        if (session != null) {
            lines.add(ServerTranslations.translate("key.gambling.hud.game.type", language,
                    session.gameType()));
            lines.add(ServerTranslations.translate("key.gambling.hud.game.bet", language,
                    ChipsFormat.format(session.bet())));
            String result = session.result();
            if (result != null && !result.isEmpty()) {
                lines.add(ServerTranslations.translate("key.gambling.hud.game.result", language, result));
            }
        }
        return lines;
    }
}
