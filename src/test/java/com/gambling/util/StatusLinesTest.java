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
    void withoutSessionBuildsGroupedLinesWithCompactBalance() {
        List<String> lines = StatusLines.build(profile(1500, 0), null, "es_es");
        assertEquals(5, lines.size());
        assertTrue(lines.get(0).isBlank(), "spacer expected below the title");
        assertEquals("◆ 1.5K CHIPS", lines.get(1));
        assertTrue(lines.get(2).contains("1"), lines.get(2));
        assertEquals("  ", lines.get(3));
        assertEquals("Estado: Disponible", lines.get(4));
    }

    @Test
    void spacersAreDistinctSoScoreboardRowsDoNotCollide() {
        GameSession session = new GameSession(ID, "coinflip", 250L,
                PlayerState.FINALIZING, "HEADS — WIN");
        List<String> lines = StatusLines.build(profile(1000, 0), session, "es_es");
        List<String> spacers = lines.stream().filter(String::isBlank).toList();
        assertEquals(3, spacers.size());
        assertEquals(3, spacers.stream().distinct().count(),
                "each spacer needs a unique owner string");
    }

    @Test
    void withSessionIncludesGameBetAndResultAndStaysWithinSidebarBudget() {
        GameSession session = new GameSession(ID, "coinflip", 250L,
                PlayerState.FINALIZING, "HEADS — WIN");
        List<String> lines = StatusLines.build(profile(1000, 0), session, "es_es");
        assertEquals(9, lines.size());
        assertEquals("Estado: Finalizando", lines.get(4));
        assertTrue(lines.get(6).contains("coinflip"), lines.get(6));
        assertEquals("Apuesta: 250", lines.get(7));
        assertTrue(lines.get(8).contains("HEADS — WIN"), lines.get(8));
        assertTrue(lines.size() <= 15, "sidebar budget exceeded: " + lines.size());
    }

    @Test
    void emptyResultLineIsOmitted() {
        GameSession session = new GameSession(ID, "slots", 10L, PlayerState.BETTING, "");
        List<String> lines = StatusLines.build(profile(100, 0), session, "en_us");
        assertEquals(8, lines.size());
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
