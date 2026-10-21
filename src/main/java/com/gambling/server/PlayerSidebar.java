package com.gambling.server;

import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;
import com.gambling.network.ProfileSyncS2C;
import com.gambling.util.ServerTranslations;
import com.gambling.util.StatusLines;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-driven sidebar HUD for clients that do not install the mod: the
 * vanilla protocol cannot draw a custom panel, but it can feed the scoreboard
 * sidebar through per-player packets, without touching the shared world
 * scoreboard. Players that do have the mod never see this sidebar; they get
 * the panel HUD instead (detected through their registered payload channel).
 *
 * The sidebar line text is the score owner; the score value renders through a
 * fixed empty number format so no number column appears on the right.
 */
public final class PlayerSidebar {
    private static final String OBJECTIVE_NAME = "gambling";
    private static final int TOP_SCORE = 100;

    private static final ChatFormatting[] LINE_COLORS = {
            ChatFormatting.GOLD, ChatFormatting.AQUA, ChatFormatting.GRAY,
            ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.WHITE
    };

    /** Rows currently shown per player, used to remove stale rows on update. */
    private static final Map<UUID, List<String>> SHOWN = new ConcurrentHashMap<>();

    private PlayerSidebar() {
    }

    public static void update(ServerPlayer player, PlayerProfile profile, GameSession session) {
        if (profile == null) {
            return;
        }
        if (ServerPlayNetworking.canSend(player, ProfileSyncS2C.ID)) {
            remove(player);
            return;
        }
        String language = player.clientInformation().language();
        List<String> lines = StatusLines.build(profile, session, language);
        List<Packet<?>> packets = buildUpdate(
                Optional.ofNullable(SHOWN.get(player.getUUID())), lines, language);
        for (Packet<?> packet : packets) {
            player.connection.send(packet);
        }
        SHOWN.put(player.getUUID(), List.copyOf(lines));
    }

    /**
     * Pure packet plan: first show sends the objective plus its sidebar display
     * slot, later shows only replace rows so stale lines are reset first.
     * Package-private so tests can assert the exact wire behaviour.
     */
    static List<Packet<?>> buildUpdate(Optional<List<String>> previous, List<String> lines,
                                       String language) {
        List<Packet<?>> packets = new ArrayList<>(2 + lines.size());
        if (previous.isEmpty()) {
            Objective objective = buildObjective(language);
            packets.add(new ClientboundSetObjectivePacket(
                    objective, ClientboundSetObjectivePacket.METHOD_ADD));
            packets.add(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        } else {
            for (String old : previous.get()) {
                if (!lines.contains(old)) {
                    packets.add(new ClientboundResetScorePacket(old, OBJECTIVE_NAME));
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            ChatFormatting color = LINE_COLORS[Math.min(i, LINE_COLORS.length - 1)];
            packets.add(new ClientboundSetScorePacket(
                    line,
                    OBJECTIVE_NAME,
                    TOP_SCORE - i,
                    Optional.of(Component.literal(line).withStyle(color)),
                    Optional.empty()));
        }
        return packets;
    }

    private static Objective buildObjective(String language) {
        return new Objective(
                new Scoreboard(),
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal(ServerTranslations.translate("key.gambling.hud.title", language))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                new FixedFormat(Component.empty()));
    }

    public static void remove(ServerPlayer player) {
        if (SHOWN.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundSetObjectivePacket(
                    buildObjective(player.clientInformation().language()),
                    ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }

    public static void forget(UUID playerId) {
        SHOWN.remove(playerId);
    }
}
