package com.gambling.server;

import com.gambling.config.GamblingConfig;
import com.gambling.economy.EconomyManager;
import com.gambling.economy.JsonProfileStorage;
import com.gambling.economy.PlayerProfile;
import com.gambling.economy.TransactionLog;
import com.gambling.game.GameSession;
import com.gambling.game.GameSessionManager;
import com.gambling.game.JsonSessionStorage;
import com.gambling.game.RoundLimiter;
import com.gambling.network.GameStateSyncS2C;
import com.gambling.network.ProfileSyncS2C;
import com.gambling.util.Validation;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class GamblingServerState {
    private static final Logger LOGGER = LoggerFactory.getLogger("gambling-server");

    private static GamblingServerState instance;

    private final EconomyManager economy;
    private final GameSessionManager sessions;
    private final JsonSessionStorage sessionStorage;
    private final RoundLimiter roundLimiter;
    private final GamblingConfig config;
    private final MinecraftServer server;

    private GamblingServerState(MinecraftServer server, Path configDir) {
        this.server = server;
        Path gamblingDir = configDir.resolve("gambling");
        JsonProfileStorage storage = new JsonProfileStorage(gamblingDir.resolve("profiles.json"));
        TransactionLog log = new TransactionLog(gamblingDir.resolve("transactions.csv"));
        this.config = GamblingConfig.get();
        this.economy = new EconomyManager(storage, log, config.defaultChips());
        this.sessions = new GameSessionManager(economy);
        this.sessionStorage = new JsonSessionStorage(gamblingDir.resolve("sessions.json"));
        this.roundLimiter = new RoundLimiter(config.roundCooldownMillis());
        economy.loadFromDisk();
        sessions.restoreFrom(sessionStorage);
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

    public RoundLimiter roundLimiter() {
        return roundLimiter;
    }

    public void onPlayerJoin(ServerPlayer player) {
        PlayerProfile profile = economy.getOrCreate(player.getUUID(), player.getName().getString());
        sendProfileSync(player, profile);
        sendSessionSync(player, sessions.getSession(player.getUUID()));
    }

    public void onPlayerQuit(ServerPlayer player) {
        sessions.handleDisconnect(player.getUUID());
        roundLimiter.clear(player.getUUID());
        if (economy.isDirty()) {
            economy.save();
        }
    }

    public void sendFullSync(ServerPlayer player) {
        PlayerProfile profile = economy.getProfile(player.getUUID());
        if (profile == null) {
            profile = economy.getOrCreate(player.getUUID(), player.getName().getString());
        }
        sendProfileSync(player, profile);
        sendSessionSync(player, sessions.getSession(player.getUUID()));
    }

    public void sendProfileSync(ServerPlayer player, PlayerProfile profile) {
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

    public void sendSessionSync(ServerPlayer player, GameSession session) {
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

    public void replyResult(ServerPlayer player, GameSessionManager.Result result) {
        Component message = Component.translatable(result.messageKey(), result.args());
        player.sendSystemMessage(message);
    }

    private void onProfileChanged(java.util.UUID uuid, PlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            sendProfileSync(player, profile);
        }
    }

    private void onSessionChanged(java.util.UUID uuid, GameSession session) {
        // Money moves before the session state that justifies it: persist the
        // (already updated) balances first so profiles.json on disk always
        // reflects a stake/payout before sessions.json can show BETTING or
        // FINALIZING. A crash in between leaves a harmless pre-commit session;
        // the reverse order would let crash recovery refund a stake that was
        // never debited (chips created from nothing).
        if (economy.isDirty() && !economy.save()) {
            LOGGER.error("Could not persist balances for the session change; retrying on the next save.");
        }
        sessionStorage.save(sessions.snapshot());
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            sendSessionSync(player, session);
        }
    }
}
