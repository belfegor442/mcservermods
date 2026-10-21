package com.gambling.server;

import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.scores.DisplaySlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the exact sidebar wire plan for vanilla clients: objective first,
 * then the display slot, then descending scores whose owner is the visible
 * line text, with no number column and no raw translation keys.
 */
class PlayerSidebarTest {
    private static final List<String> LINES = List.of(
            "\u25c6 1,500 CHIPS", "Nv 1", "Estado: Disponible");

    @BeforeAll
    static void bootstrapRegistries() {
        // Packet codecs reference built-in registries, which only exist after
        // vanilla's bootstrap; both calls are safe to repeat.
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void firstShowSendsObjectiveDisplayThenScoresInOrder() {
        List<Packet<?>> packets = PlayerSidebar.buildUpdate(Optional.empty(), LINES, "es_es");

        assertEquals(LINES.size() + 2, packets.size());
        ClientboundSetObjectivePacket add =
                assertInstanceOf(ClientboundSetObjectivePacket.class, packets.get(0));
        assertEquals(ClientboundSetObjectivePacket.METHOD_ADD, add.getMethod());
        assertEquals("gambling", add.getObjectiveName());

        ClientboundSetDisplayObjectivePacket display =
                assertInstanceOf(ClientboundSetDisplayObjectivePacket.class, packets.get(1));
        assertEquals(DisplaySlot.SIDEBAR, display.getSlot());
        assertEquals("gambling", display.getObjectiveName());
    }

    @Test
    void objectiveTitleIsTranslatedPerClientLanguage() {
        List<Packet<?>> es = PlayerSidebar.buildUpdate(Optional.empty(), LINES, "es_es");
        List<Packet<?>> en = PlayerSidebar.buildUpdate(Optional.empty(), LINES, "en_us");

        ClientboundSetObjectivePacket esAdd =
                assertInstanceOf(ClientboundSetObjectivePacket.class, es.get(0));
        ClientboundSetObjectivePacket enAdd =
                assertInstanceOf(ClientboundSetObjectivePacket.class, en.get(0));
        assertEquals("\u25c6 CASINO", esAdd.getDisplayName().getString());
        assertEquals("\u25c6 GAMBLING", enAdd.getDisplayName().getString());
    }

    @Test
    void numberFormatRendersNoNumberColumn() {
        List<Packet<?>> packets = PlayerSidebar.buildUpdate(Optional.empty(), LINES, "en_us");
        ClientboundSetObjectivePacket add =
                assertInstanceOf(ClientboundSetObjectivePacket.class, packets.get(0));

        Optional<NumberFormat> format = add.getNumberFormat();
        assertTrue(format.isPresent(), "objective must carry a fixed number format");
        assertEquals("", format.get().format(100).getString());
        assertEquals("", format.get().format(0).getString());
    }

    @Test
    void scoreOwnersAreTheVisibleLinesWithDescendingValues() {
        List<Packet<?>> packets = PlayerSidebar.buildUpdate(Optional.empty(), LINES, "en_us");
        List<ClientboundSetScorePacket> scores = scoresOf(packets);

        assertEquals(LINES.size(), scores.size());
        for (int i = 0; i < scores.size(); i++) {
            assertEquals(LINES.get(i), scores.get(i).owner());
            assertEquals("gambling", scores.get(i).objectiveName());
            assertEquals(100 - i, scores.get(i).score());
            assertTrue(scores.get(i).display().isPresent(), "line must carry its text");
            assertEquals(LINES.get(i), scores.get(i).display().get().getString());
        }
        assertEquals(100, scores.get(0).score());
        assertEquals(98, scores.get(2).score());
    }

    @Test
    void updateResetsOnlyStaleRowsAndSendsNoObjectiveAgain() {
        List<String> updated = List.of(LINES.get(0), "Resultado: HEADS \u2014 WIN");
        List<Packet<?>> packets = PlayerSidebar.buildUpdate(Optional.of(LINES), updated, "en_us");

        assertFalse(hasType(packets, ClientboundSetObjectivePacket.class),
                "already-shown sidebar must not re-add the objective");
        List<ClientboundResetScorePacket> resets = packets.stream()
                .filter(p -> p instanceof ClientboundResetScorePacket)
                .map(p -> (ClientboundResetScorePacket) p)
                .toList();
        assertEquals(2, resets.size(), "Nv 1 and Estado lines are stale");
        assertTrue(resets.stream().anyMatch(r -> r.owner().equals(LINES.get(1))));
        assertTrue(resets.stream().anyMatch(r -> r.owner().equals(LINES.get(2))));
        assertFalse(resets.stream().anyMatch(r -> r.owner().equals(LINES.get(0))));
        assertEquals(2, scoresOf(packets).size());
    }

    @Test
    void unchangedLinesProduceOnlyScoreUpdates() {
        List<Packet<?>> packets = PlayerSidebar.buildUpdate(Optional.of(LINES), LINES, "en_us");
        assertEquals(LINES.size(), packets.size());
        assertTrue(packets.stream().allMatch(p -> p instanceof ClientboundSetScorePacket));
    }

    @Test
    void noPacketCarriesARawTranslationKey() {
        List<Packet<?>> packets = PlayerSidebar.buildUpdate(Optional.empty(), LINES, "en_us");
        for (Packet<?> packet : packets) {
            assertFalse(String.valueOf(packet).contains("key.gambling"),
                    "raw key leaked: " + packet);
        }
    }

    private static boolean hasType(List<Packet<?>> packets, Class<?> type) {
        return packets.stream().anyMatch(type::isInstance);
    }

    private static List<ClientboundSetScorePacket> scoresOf(List<Packet<?>> packets) {
        return packets.stream()
                .filter(p -> p instanceof ClientboundSetScorePacket)
                .map(p -> (ClientboundSetScorePacket) p)
                .toList();
    }
}
