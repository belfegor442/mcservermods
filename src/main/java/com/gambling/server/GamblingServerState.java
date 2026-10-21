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
    private final JsonProfileStorage profileStorage;
    private final JsonSessionStorage sessionStorage;
    private final TransactionStateJournal transactionJournal;
    private final RoundLimiter roundLimiter;
    private final GamblingConfig config;
    private final MinecraftServer server;
    private volatile boolean transactionPersistenceHealthy = true;

    private GamblingServerState(MinecraftServer server, Path configDir) {
        this.server = server;
        Path gamblingDir = configDir.resolve("gambling");
        this.config = GamblingConfig.get();
        this.profileStorage = new JsonProfileStorage(gamblingDir.resolve("profiles.json"));
        this.sessionStorage = new JsonSessionStorage(gamblingDir.resolve("sessions.json"));
        this.transactionJournal = new TransactionStateJournal(gamblingDir.resolve("state-journal.json"));

        if (!transactionJournal.recover(profileStorage, sessionStorage)) {
            throw new IllegalStateException(
                    "Could not recover the gambling transaction journal; refusing to load inconsistent economy data.");
        }

        TransactionLog log = new TransactionLog(gamblingDir.resolve("transactions.csv"));
        this.economy = new EconomyManager(profileStorage, log, config.defaultChips());
        this.sessions = new GameSessionManager(economy);
        this.roundLimiter = new RoundLimiter(config.roundCooldownMillis());
        economy.loadFromDisk();
        sessions.restoreSessions(sessionStorage.load());

        // Startup may have refunded a persisted BETTING stake. Journal that
        // refund and the cleaned session map as one recoverable transaction.
        if (!persistCoherentState()) {
            throw new IllegalStateException(
                    "Could not persist recovered gambling state; refusing to start with uncertain balances.");
        }

        economy.addChangeListener(this::onProfileChanged);
        sessions.addChangeListener(this::onSessionChanged);
    }

    public static void start(MinecraftServer server, Path configDir) {
        instance = new GamblingServerState(server, configDir);
        LOGGER.info("Gambling server state initialized.");
    }

    public static void stop() {
        if (instance != null) {
            if (!instance.persistCoherentState()) {
                LOGGER.error("Gambling state could not be fully persisted during shutdown; the transaction journal was retained when possible.");
            }
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
        PlayerSidebar.update(player, profile, sessions.getSession(player.getUUID()));
    }

    public void onPlayerQuit(ServerPlayer player) {
        sessions.handleDisconnect(player.getUUID());
        roundLimiter.clear(player.getUUID());
        PlayerSidebar.forget(player.getUUID());
        if (economy.isDirty()) {
            saveProfilesIfSafe();
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
        player.sendSystemMessage(ModText.of(player, result.messageKey(), result.args()));
    }

    private void onProfileChanged(java.util.UUID uuid, PlayerProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            sendProfileSync(player, profile);
            PlayerSidebar.update(player, profile, sessions.getSession(uuid));
        }
    }

    private void onSessionChanged(java.util.UUID uuid, GameSession session) {
        persistCoherentState();
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            sendSessionSync(player, session);
            PlayerSidebar.update(player, economy.getProfile(uuid), session);
        }
    }

    /**
     * Persists both sides of a money/session transition through a write-ahead
     * snapshot. If either file write fails, the journal is kept for replay and
     * profile-only autosaves are blocked until a coherent commit succeeds.
     */
    private synchronized boolean persistCoherentState() {
        var profileSnapshot = economy.snapshot();
        var sessionSnapshot = sessions.snapshot();

        if (!transactionJournal.save(profileSnapshot, sessionSnapshot)) {
            transactionPersistenceHealthy = false;
            LOGGER.error("Could not journal gambling state; independent profile saves are disabled to avoid splitting a transaction.");
            return false;
        }

        boolean profilesSaved = economy.save();
        boolean sessionsSaved = sessionStorage.save(sessionSnapshot);
        if (!profilesSaved || !sessionsSaved) {
            transactionPersistenceHealthy = false;
            LOGGER.error("Could not persist the complete gambling state; the journal will be replayed on the next startup.");
            return false;
        }

        if (!transactionJournal.clear()) {
            transactionPersistenceHealthy = false;
            LOGGER.error("Gambling files were saved but the journal could not be cleared; independent profile saves remain disabled.");
            return false;
        }

        transactionPersistenceHealthy = true;
        return true;
    }

    public void saveProfilesIfSafe() {
        if (!transactionPersistenceHealthy) {
            return;
        }
        if (economy.isDirty() && !economy.save()) {
            LOGGER.error("Could not autosave gambling profiles; they will be retried.");
        }
    }
}
