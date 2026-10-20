package com.gambling.server;

import com.gambling.economy.EconomyManager;
import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.PlayerProfile;
import com.gambling.economy.TransactionLog;
import com.gambling.game.GameSession;
import com.gambling.game.GameSessionManager;
import com.gambling.game.JsonSessionStorage;
import com.gambling.network.GameStateSyncS2C;
import com.gambling.network.ProfileSyncS2C;
import com.gambling.util.Validation;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class GamblingServerState {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-server");

    private static GamblingServerState instance;

    private final EconomyManager economy;
    private final GameSessionManager sessions;
    private final JsonSessionStorage sessionStorage;
    private final MinecraftServer server;

    private GamblingServerState(MinecraftServer server, Path configDir) {
        this.server = server;
        Path gamblingDir = configDir.resolve("gambling");
        JsonProfileStorage storage = new JsonProfileStorage(gamblingDir.resolve("profiles.json"));
        TransactionLog log = new TransactionLog(gamblingDir.resolve("transactions.csv"));
        this.economy = new EconomyManager(storage, log);
        this.sessions = new GameSessionManager(economy);
        this.sessionStorage = new JsonSessionStorage(gamblingDir.resolve("sessions.json"));
        economy.loadFromDisk();
        sessions.restoreSessions(sessionStorage.load());
        economy.addChangeListener(this::onProfileChanged);
        sessions.addChangeListener(this::onSessionChanged);
    }

    public static void start(MinecraftServer server, Path configDir) {
        instance = new GamblingServerState(server, configDir);
        LOGGER.info("Gambling server state initialized.");
    }

    public static void stop() {
        if (instance != null) {
            instance.economy.save();
            instance.sessionStorage.save(instance.sessions.snapshot());
            instance = null;
            LOGGER.info("Gambling server state saved and cleared.");
        }
    }

    public static GamblingServerState get() {
        return instance;
    }

    public EconomyManager economy() {
        return economy;
    }

    public GameSessionManager sessions() {
        return sessions;
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        PlayerProfile profile = economy.getOrCreate(player.getUuid(), player.getName().getString());
        sendProfileSync(player, profile);
        sendSessionSync(player, sessions.getSession(player.getUuid()));
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        sessions.handleDisconnect(player.getUuid());
        economy.save();
    }

    public void sendFullSync(ServerPlayerEntity player) {
        PlayerProfile profile = economy.getProfile(player.getUuid());
        if (profile == null) {
            profile = economy.getOrCreate(player.getUuid(), player.getName().getString());
        }
        sendProfileSync(player, profile);
        sendSessionSync(player, sessions.getSession(player.getUuid()));
    }

    public void sendProfileSync(ServerPlayerEntity player, PlayerProfile profile) {
        if (!ServerPlayNetworking.canSend(player, ProfileSyncS2C.ID)) {
            return;
        }
        ServerPlayNetworking.send(player, new ProfileSyncS2C(
                profile.name(),
                Validation.clampNonNegative(profile.chips()),
                profile.level(),
                Validation.clampNonNegative(profile.xp()),
                Validation.clampNonNegative(profile.totalWagered())));
    }

    public void sendSessionSync(ServerPlayerEntity player, GameSession session) {
        if (!ServerPlayNetworking.canSend(player, GameStateSyncS2C.ID)) {
            return;
        }
        if (session == null) {
            ServerPlayNetworking.send(player, new GameStateSyncS2C(false, "", 0L, "", ""));
        } else {
            ServerPlayNetworking.send(player, new GameStateSyncS2C(
                    true,
                    session.gameType(),
                    session.bet(),
                    session.state().id(),
                    session.result() == null ? "" : session.result()));
        }
    }

    public void replyResult(ServerPlayerEntity player, GameSessionManager.Result result) {
        Text message = Text.translatable(result.messageKey(), result.args());
        player.sendMessage(message, false);
    }

    private void onProfileChanged(java.util.UUID uuid, PlayerProfile profile) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            sendProfileSync(player, profile);
        }
    }

    private void onSessionChanged(java.util.UUID uuid, GameSession session) {
        sessionStorage.save(sessions.snapshot());
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            sendSessionSync(player, session);
        }
    }
}
