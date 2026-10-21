package com.gambling.server;

import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;
import com.gambling.game.JsonSessionStorage;
import com.gambling.game.PlayerState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStateJournalTest {
    @Test
    void recoversPartialBetCommitAsOneSnapshot(@TempDir Path dir) {
        Path profilesFile = dir.resolve("profiles.json");
        Path sessionsFile = dir.resolve("sessions.json");
        JsonProfileStorage profiles = new JsonProfileStorage(profilesFile);
        JsonSessionStorage sessions = new JsonSessionStorage(sessionsFile);
        TransactionStateJournal journal = new TransactionStateJournal(dir.resolve("state-journal.json"));
        UUID player = UUID.randomUUID();

        assertTrue(profiles.save(Map.of(player, new PlayerProfile(player, "Steve", 1_000, 0, 0))));
        assertTrue(sessions.save(Map.of(player,
                new GameSession(player, "coinflip", 100, PlayerState.IN_GAME, ""))));

        Map<UUID, PlayerProfile> committedProfiles =
                Map.of(player, new PlayerProfile(player, "Steve", 900, 0, 0));
        Map<UUID, GameSession> committedSessions =
                Map.of(player, new GameSession(player, "coinflip", 100, PlayerState.BETTING, ""));
        assertTrue(journal.save(committedProfiles, committedSessions));

        // Simulate a crash after profiles.json reached disk but before sessions.json did.
        assertTrue(profiles.save(committedProfiles));
        assertTrue(journal.recover(profiles, sessions));

        assertEquals(900L, profiles.load().get(player).chips());
        assertEquals(PlayerState.BETTING, sessions.load().get(player).state());
        assertFalse(Files.exists(dir.resolve("state-journal.json")));
    }

    @Test
    void recoversDisconnectRefundWithoutRefundingTwice(@TempDir Path dir) {
        Path profilesFile = dir.resolve("profiles.json");
        Path sessionsFile = dir.resolve("sessions.json");
        JsonProfileStorage profiles = new JsonProfileStorage(profilesFile);
        JsonSessionStorage sessions = new JsonSessionStorage(sessionsFile);
        TransactionStateJournal journal = new TransactionStateJournal(dir.resolve("state-journal.json"));
        UUID player = UUID.randomUUID();

        assertTrue(profiles.save(Map.of(player, new PlayerProfile(player, "Steve", 900, 0, 0))));
        assertTrue(sessions.save(Map.of(player,
                new GameSession(player, "coinflip", 100, PlayerState.BETTING, ""))));

        Map<UUID, PlayerProfile> refundedProfiles =
                Map.of(player, new PlayerProfile(player, "Steve", 1_000, 0, 0));
        assertTrue(journal.save(refundedProfiles, Map.of()));

        // Simulate the first file being updated before the server crashes.
        assertTrue(profiles.save(refundedProfiles));
        assertTrue(journal.recover(profiles, sessions));

        assertEquals(1_000L, profiles.load().get(player).chips());
        assertTrue(sessions.load().isEmpty());
        assertTrue(journal.recover(profiles, sessions), "a clean second recovery must be a no-op");
        assertEquals(1_000L, profiles.load().get(player).chips());
        assertTrue(sessions.load().isEmpty());
    }

    @Test
    void unreadableJournalFailsClosedAndIsPreserved(@TempDir Path dir) throws Exception {
        Path profilesFile = dir.resolve("profiles.json");
        Path sessionsFile = dir.resolve("sessions.json");
        Path journalFile = dir.resolve("state-journal.json");
        JsonProfileStorage profiles = new JsonProfileStorage(profilesFile);
        JsonSessionStorage sessions = new JsonSessionStorage(sessionsFile);
        Files.writeString(journalFile, "{{ broken");

        TransactionStateJournal journal = new TransactionStateJournal(journalFile);

        assertFalse(journal.recover(profiles, sessions));
        assertTrue(Files.exists(journalFile));
        assertTrue(profiles.load().isEmpty());
        assertTrue(sessions.load().isEmpty());
    }
}
