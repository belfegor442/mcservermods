package com.gambling.server;

import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSession;
import com.gambling.network.ProfileSyncS2C;
import com.gambling.util.StatusLines;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

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
 */
public final class PlayerSidebar {
    private static final String OBJECTIVE_NAME = "gambling";
    private static final int TOP_SCORE = 100;

    private static final Objective OBJECTIVE = new Objective(
            new Scoreboard(),
            OBJECTIVE_NAME,
            ObjectiveCriteria.DUMMY,
            Component.literal("◆ GAMBLING").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            null);

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
        List<String> lines = StatusLines.build(profile, session, player.clientInformation().language());
        List<String> previous = SHOWN.get(player.getUUID());
        if (previous == null) {
            player.connection.send(new ClientboundSetObjectivePacket(
                    OBJECTIVE, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, OBJECTIVE));
        } else {
            for (String old : previous) {
                if (!lines.contains(old)) {
                    player.connection.send(new ClientboundResetScorePacket(old, OBJECTIVE_NAME));
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            ChatFormatting color = LINE_COLORS[Math.min(i, LINE_COLORS.length - 1)];
            player.connection.send(new ClientboundSetScorePacket(
                    line,
                    OBJECTIVE_NAME,
                    TOP_SCORE - i,
                    Optional.of(Component.literal(line).withStyle(color)),
                    Optional.empty()));
        }
        SHOWN.put(player.getUUID(), List.copyOf(lines));
    }

    public static void remove(ServerPlayer player) {
        if (SHOWN.remove(player.getUUID()) != null) {
            player.connection.send(new ClientboundSetObjectivePacket(
                    OBJECTIVE, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }

    public static void forget(UUID playerId) {
        SHOWN.remove(playerId);
    }
}
