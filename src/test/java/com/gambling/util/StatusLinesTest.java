package com.gambling.util;

import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;
import com.gambling.game.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusLinesTest {
    private static final UUID ID = UUID.randomUUID();

    private static PlayerProfile profile(long chips, long xp) {
        return new PlayerProfile(ID, "Steve", chips, xp, 0L);
    }

    @Test
    void withoutSessionBuildsThreeLinesInSpanish() {
        List<String> lines = StatusLines.build(profile(1500, 0), null, "es_es");
        assertEquals(3, lines.size());
        assertEquals("◆ 1,500 CHIPS", lines.get(0));
        assertTrue(lines.get(1).contains("1"), lines.get(1));
        assertEquals("Estado: Disponible", lines.get(2));
    }

    @Test
    void withSessionIncludesGameBetAndResultAndStaysWithinSixLines() {
        GameSession session = new GameSession(ID, "coinflip", 250L,
                PlayerState.FINALIZING, "HEADS — WIN");
        List<String> lines = StatusLines.build(profile(1000, 0), session, "es_es");
        assertTrue(lines.size() <= 6, "sidebar budget exceeded: " + lines.size());
        assertEquals("Estado: Finalizando", lines.get(2));
        assertTrue(lines.get(3).contains("coinflip"), lines.get(3));
        assertTrue(lines.get(4).contains("250"), lines.get(4));
        assertTrue(lines.get(5).contains("HEADS — WIN"), lines.get(5));
    }

    @Test
    void emptyResultLineIsOmitted() {
        GameSession session = new GameSession(ID, "slots", 10L, PlayerState.BETTING, "");
        List<String> lines = StatusLines.build(profile(100, 0), session, "en_us");
        assertEquals(5, lines.size());
        assertFalse(lines.get(lines.size() - 1).contains("Result"), lines.get(lines.size() - 1));
    }

    @Test
    void noLineStartsWithRawTranslationKey() {
        GameSession session = new GameSession(ID, "roulette", 5L, PlayerState.IN_GAME, "17 BLACK");
        List<String> lines = StatusLines.build(profile(0, 0), null, "fr_fr");
        lines.addAll(StatusLines.build(profile(0, 0), session, "en_us"));
        for (String line : lines) {
            assertFalse(line.startsWith("key."), "raw key leaked: " + line);
        }
    }
}
