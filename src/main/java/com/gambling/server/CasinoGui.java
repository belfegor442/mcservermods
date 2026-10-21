package com.gambling.server;

import com.gambling.config.GamblingConfig;
import com.gambling.economy.PlayerProfile;
import com.gambling.game.GameSessionManager;
import com.gambling.util.ChipsFormat;
import com.gambling.util.Validation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side chest GUI for one player: selected game, option and bet plus
 * the last round outcome. Vanilla clients drive it through the normal
 * container click packets, so no client mod is required. Every round goes
 * through {@link CasinoRound}, the same engine as /casino, keeping the
 * economy guards identical. The GUI only exists while the menu is open.
 */
public final class CasinoGui {
    public static final int SLOT_COUNT = 27;

    static final int SLOT_PROFILE = 0;
    static final int SLOT_COINFLIP = 3;
    static final int SLOT_ROULETTE = 4;
    static final int SLOT_SLOTS = 5;
    static final int SLOT_RESULT = 8;
    static final int SLOT_OPTION_1 = 9;
    static final int SLOT_OPTION_2 = 10;
    static final int SLOT_OPTION_3 = 11;
    static final int SLOT_BET_RESET = 12;
    static final int SLOT_BET_DISPLAY = 13;
    static final int SLOT_BET_HALF = 14;
    static final int SLOT_BET_DOUBLE = 15;
    static final int SLOT_BET_MAX = 16;
    static final int SLOT_HELP = 18;
    static final int SLOT_PLAY = 22;
    static final int SLOT_CLOSE = 26;

    private static final long DEFAULT_BET = 10L;

    private final GamblingServerState state;
    private final UUID playerId;
    private final String playerName;

    private String game = "coinflip";
    private String option = "heads";
    private long bet = DEFAULT_BET;
    private CasinoRound.Outcome lastOutcome;

    private SimpleContainer container;
    private CasinoMenu menu;

    CasinoGui(GamblingServerState state, UUID playerId, String playerName) {
        this.state = state;
        this.playerId = playerId;
        this.playerName = playerName;
    }

    void attach(SimpleContainer container, CasinoMenu menu) {
        this.container = container;
        this.menu = menu;
    }

    void detach(CasinoMenu menu) {
        if (this.menu == menu) {
            this.menu = null;
            this.container = null;
        }
    }

    void refresh(ServerPlayer player) {
        if (menu == null || container == null) {
            return;
        }
        render(player);
        menu.sendAllDataToRemote();
    }

    void onSlotClicked(ServerPlayer player, int slot) {
        long maxBet = GamblingConfig.get().maxBet();
        switch (slot) {
            case SLOT_COINFLIP -> {
                game = "coinflip";
                option = "heads";
            }
            case SLOT_ROULETTE -> {
                game = "roulette";
                option = "red";
            }
            case SLOT_SLOTS -> {
                game = "slots";
                option = "";
            }
            case SLOT_OPTION_1 -> {
                if ("coinflip".equals(game)) {
                    option = "heads";
                } else if ("roulette".equals(game)) {
                    option = "red";
                }
            }
            case SLOT_OPTION_2 -> {
                if ("coinflip".equals(game)) {
                    option = "tails";
                } else if ("roulette".equals(game)) {
                    option = "black";
                }
            }
            case SLOT_OPTION_3 -> {
                if ("roulette".equals(game)) {
                    option = "green";
                }
            }
            case SLOT_BET_RESET -> bet = DEFAULT_BET;
            case SLOT_BET_HALF -> bet = Math.max(1L, bet / 2);
            case SLOT_BET_DOUBLE -> bet = Math.min(maxBet, bet * 2L);
            case SLOT_BET_MAX -> bet = maxBet;
            case SLOT_PLAY -> {
                playRound(player);
                refresh(player);
                return;
            }
            default -> {
                return;
            }
        }
        CasinoRound.playClickSound(player);
        refresh(player);
    }

    private void playRound(ServerPlayer player) {
        GamblingServerState serverState = GamblingServerState.get();
        if (serverState == null) {
            return;
        }
        String effectiveOption = "slots".equals(game) ? "" : option;
        lastOutcome = CasinoRound.play(serverState, player, game, bet, effectiveOption);
        if (lastOutcome.ok()) {
            serverState.replyResult(player,
                    GameSessionManager.Result.success(lastOutcome.key(), lastOutcome.args()));
            CasinoRound.showFeedback(player, lastOutcome.won(), lastOutcome.key(), lastOutcome.args());
        } else {
            CasinoRound.playClickSound(player);
        }
    }

    void render(ServerPlayer player) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        PlayerProfile profile = state.economy().getOrCreate(playerId, playerName);
        long maxBet = GamblingConfig.get().maxBet();

        ItemStack profileItem = named(player, Items.PAPER, "key.gambling.hud.profile_label");
        withLore(profileItem,
                ModText.of(player, "key.gambling.cmd.balance.self",
                        ChipsFormat.format(Validation.clampNonNegative(profile.chips())),
                        profile.level()),
                ModText.of(player, "key.gambling.gui.wagered",
                        ChipsFormat.format(Validation.clampNonNegative(profile.totalWagered()))));
        put(SLOT_PROFILE, profileItem);

        put(SLOT_COINFLIP, gameItem(player, "coinflip", Items.ENDER_PEARL));
        put(SLOT_ROULETTE, gameItem(player, "roulette", Items.WOOL.red()));
        put(SLOT_SLOTS, gameItem(player, "slots", Items.GOLD_NUGGET));

        if ("coinflip".equals(game)) {
            put(SLOT_OPTION_1, optionItem(player, "heads", "key.gambling.menu.heads"));
            put(SLOT_OPTION_2, optionItem(player, "tails", "key.gambling.menu.tails"));
        } else if ("roulette".equals(game)) {
            put(SLOT_OPTION_1, optionItem(player, "red", "key.gambling.menu.red"));
            put(SLOT_OPTION_2, optionItem(player, "black", "key.gambling.menu.black"));
            put(SLOT_OPTION_3, optionItem(player, "green", "key.gambling.menu.green"));
        } else {
            put(SLOT_OPTION_2,
                    named(player, Items.STAINED_GLASS_PANE.lime(), "key.gambling.gui.option.auto"));
        }

        put(SLOT_BET_RESET, named(player, Items.FEATHER, "key.gambling.gui.bet.reset"));
        ItemStack betDisplay = named(player, Items.PAPER,
                "key.gambling.hud.game.bet", ChipsFormat.format(bet));
        withLore(betDisplay, ModText.of(player, "key.gambling.gui.bet.range",
                ChipsFormat.format(maxBet)));
        put(SLOT_BET_DISPLAY, betDisplay);
        put(SLOT_BET_HALF, named(player, Items.DYE.gray(), "key.gambling.gui.bet.half"));
        put(SLOT_BET_DOUBLE, named(player, Items.DYE.lime(), "key.gambling.gui.bet.double"));
        put(SLOT_BET_MAX, named(player, Items.NETHER_STAR, "key.gambling.gui.bet.max_button"));

        ItemStack help = named(player, Items.BOOK, "key.gambling.gui.help.title");
        withLore(help,
                ModText.of(player, "key.gambling.gui.help.how"),
                ModText.of(player, "key.gambling.menu.hint"));
        put(SLOT_HELP, help);

        ItemStack play = new ItemStack(Items.EMERALD_BLOCK);
        play.set(DataComponents.CUSTOM_NAME, ModText.of(player, "key.gambling.menu.play")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        List<Component> playLore = new ArrayList<>(4);
        playLore.add(ModText.of(player, "key.gambling.hud.game.type", game));
        playLore.add(ModText.of(player, "key.gambling.hud.game.bet", ChipsFormat.format(bet)));
        if (!"slots".equals(game) && option.isEmpty()) {
            playLore.add(ModText.of(player, "key.gambling.gui.play.need_option")
                    .withStyle(ChatFormatting.RED));
        }
        if (profile.chips() < bet) {
            playLore.add(ModText.of(player, "key.gambling.menu.error.insufficient")
                    .withStyle(ChatFormatting.RED));
        }
        play.set(DataComponents.LORE, new ItemLore(playLore));
        put(SLOT_PLAY, play);

        ItemStack close = new ItemStack(Items.BARRIER);
        close.set(DataComponents.CUSTOM_NAME,
                ModText.of(player, "key.gambling.menu.close").withStyle(ChatFormatting.RED));
        put(SLOT_CLOSE, close);
        put(SLOT_RESULT, resultItem(player));
    }

    private ItemStack gameItem(ServerPlayer player, String gameId, ItemLike type) {
        boolean selected = gameId.equals(game);
        ItemStack stack = new ItemStack(type);
        stack.set(DataComponents.CUSTOM_NAME, ModText.of(player, "key.gambling.menu." + gameId)
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.WHITE,
                        selected ? ChatFormatting.BOLD : ChatFormatting.ITALIC));
        if (selected) {
            withLore(stack, ModText.of(player, "key.gambling.gui.game.selected")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            withLore(stack, ModText.of(player, "key.gambling.gui.select_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        return stack;
    }

    private ItemStack optionItem(ServerPlayer player, String value, String key) {
        boolean selected = value.equals(option);
        ItemStack stack = new ItemStack(selected ? Items.DYE.lime() : Items.DYE.gray());
        stack.set(DataComponents.CUSTOM_NAME, ModText.of(player, key)
                .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                        selected ? ChatFormatting.BOLD : ChatFormatting.ITALIC));
        if (selected) {
            withLore(stack, ModText.of(player, "key.gambling.gui.game.selected")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        }
        return stack;
    }

    private ItemStack resultItem(ServerPlayer player) {
        ItemStack stack = new ItemStack(Items.ANVIL);
        if (lastOutcome == null) {
            stack.set(DataComponents.CUSTOM_NAME,
                    ModText.of(player, "key.gambling.gui.result.none")
                            .withStyle(ChatFormatting.GRAY));
            withLore(stack, ModText.of(player, "key.gambling.gui.result.none.lore")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return stack;
        }
        if (!lastOutcome.ok()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    ModText.of(player, "key.gambling.gui.result.error")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            withLore(stack, ModText.of(player, lastOutcome.key(), lastOutcome.args())
                    .withStyle(ChatFormatting.RED));
            return stack;
        }
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(lastOutcome.resultText())
                        .withStyle(lastOutcome.won() ? ChatFormatting.GREEN : ChatFormatting.RED,
                                ChatFormatting.BOLD));
        withLore(stack, ModText.of(player, lastOutcome.key(), lastOutcome.args())
                .withStyle(ChatFormatting.GRAY));
        return stack;
    }

    private ItemStack named(ServerPlayer player, ItemLike type, String key, Object... args) {
        ItemStack stack = new ItemStack(type);
        stack.set(DataComponents.CUSTOM_NAME, ModText.of(player, key, args));
        return stack;
    }

    private static void withLore(ItemStack stack, Component... lines) {
        stack.set(DataComponents.LORE, new ItemLore(List.of(lines)));
    }

    private void put(int slot, ItemStack stack) {
        if (container != null) {
            container.setItem(slot, stack);
        }
    }
}
