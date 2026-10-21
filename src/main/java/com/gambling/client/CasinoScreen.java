package com.gambling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.gambling.util.ChipsFormat;

import java.util.Locale;

public final class CasinoScreen extends Screen {
    private static final int GOLD = 0xFFFFD36A;
    private static final int WHITE = 0xFFF0F2F5;
    private static final int MUTED = 0xFF9BA6B4;
    private static final int PANEL = 0xF0141922;
    private static final int CARD = 0xFF202834;
    private static final int SELECTED = 0xFF514126;
    private static final int LINE = 0xFF3A4554;

    private String selectedGame = "coinflip";
    private String selectedChoice = "heads";
    private String betText = "10";
    private EditBox betInput;

    public CasinoScreen() {
        super(Component.translatable("key.gambling.menu.title"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        int contentWidth = Math.min(360, width - 24);
        int left = center - contentWidth / 2;
        int cardGap = 6;
        int cardWidth = (contentWidth - cardGap * 2) / 3;
        int gameY = Math.max(64, height / 2 - 64);

        addRenderableWidget(Button.builder(Component.translatable("key.gambling.menu.coinflip"), button -> {
            selectedGame = "coinflip";
            selectedChoice = "heads";
            rebuildWidgets();
        }).bounds(left, gameY, cardWidth, 36).build());
        addRenderableWidget(Button.builder(Component.translatable("key.gambling.menu.roulette"), button -> {
            selectedGame = "roulette";
            selectedChoice = "red";
            rebuildWidgets();
        }).bounds(left + cardWidth + cardGap, gameY, cardWidth, 36).build());
        addRenderableWidget(Button.builder(Component.translatable("key.gambling.menu.slots"), button -> {
            selectedGame = "slots";
            selectedChoice = "";
            rebuildWidgets();
        }).bounds(left + (cardWidth + cardGap) * 2, gameY, cardWidth, 36).build());

        int sectionY = gameY + 53;
        if (selectedGame.equals("coinflip")) {
            addRenderableWidget(choiceButton(Component.translatable("key.gambling.menu.heads"), left,
                    sectionY, contentWidth / 2 - 3, "heads"));
            addRenderableWidget(choiceButton(Component.translatable("key.gambling.menu.tails"),
                    center + 3, sectionY, contentWidth / 2 - 3, "tails"));
        } else if (selectedGame.equals("roulette")) {
            int choiceWidth = (contentWidth - 8) / 3;
            addRenderableWidget(choiceButton(Component.translatable("key.gambling.menu.red"), left,
                    sectionY, choiceWidth, "red"));
            addRenderableWidget(choiceButton(Component.translatable("key.gambling.menu.black"),
                    left + choiceWidth + 4, sectionY, choiceWidth, "black"));
            addRenderableWidget(choiceButton(Component.translatable("key.gambling.menu.green"),
                    left + (choiceWidth + 4) * 2, sectionY, choiceWidth, "green"));
        }

        int betY = sectionY + 50;
        betInput = new EditBox(font, left, betY, contentWidth, 20,
                Component.translatable("key.gambling.menu.bet"));
        betInput.setMaxLength(18);
        betInput.setValue(betText);
        betInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        betInput.setResponder(value -> betText = value);
        addRenderableWidget(betInput);

        int presetY = betY + 28;
        int presetWidth = (contentWidth - 12) / 4;
        long[] presets = {10, 50, 100, 500};
        for (int i = 0; i < presets.length; i++) {
            long amount = presets[i];
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(amount)), button -> {
                betText = Long.toString(amount);
                if (betInput != null) {
                    betInput.setValue(betText);
                }
            }).bounds(left + i * (presetWidth + 4), presetY, presetWidth, 20).build());
        }

        int actionY = presetY + 34;
        addRenderableWidget(Button.builder(Component.translatable("key.gambling.menu.play"), button -> play())
                .bounds(left, actionY, contentWidth * 2 / 3 - 3, 25).build());
        addRenderableWidget(Button.builder(Component.translatable("key.gambling.menu.close"),
                        button -> onClose())
                .bounds(left + contentWidth * 2 / 3 + 3, actionY,
                        contentWidth / 3, 25).build());
    }

    private Button choiceButton(Component label, int x, int y, int buttonWidth, String choice) {
        Component display = selectedChoice.equals(choice)
                ? Component.literal("• ").append(label)
                : label;
        return Button.builder(display, button -> {
            selectedChoice = choice;
            rebuildWidgets();
        }).bounds(x, y, buttonWidth, 24).build();
    }

    private void play() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) {
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(betText);
        } catch (NumberFormatException exception) {
            return;
        }
        if (amount < 1L) {
            return;
        }

        String command = switch (selectedGame) {
            case "coinflip" -> "casino coinflip " + amount + " " + selectedChoice;
            case "roulette" -> "casino roulette " + amount + " " + selectedChoice;
            case "slots" -> "casino slots " + amount;
            default -> "";
        };
        if (!command.isEmpty()) {
            client.player.connection.sendCommand(command);
            onClose();
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        context.fill(0, 0, width, height, 0xB8000000);
        int contentWidth = Math.min(380, width - 20);
        int left = (width - contentWidth) / 2;
        int top = Math.max(8, height / 2 - 132);
        int bottom = Math.min(height - 8, top + 270);

        context.fill(left - 1, top - 1, left + contentWidth + 1, bottom + 1, 0xCC080B10);
        context.fill(left, top, left + contentWidth, bottom, PANEL);
        context.fill(left, top, left + contentWidth, top + 3, GOLD);

        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable("key.gambling.menu.title");
        context.text(font, title, width / 2 - font.width(title) / 2, top + 10, GOLD);
        ClientGamblingState state = ClientGamblingState.get();
        Component balance = Component.translatable("key.gambling.menu.balance",
                state.hasReceivedProfile() ? ChipsFormat.format(state.chips()) : "—");
        context.text(font, balance, width / 2 - font.width(balance) / 2, top + 24, WHITE);
        Component subtitle = Component.translatable("key.gambling.menu.subtitle");
        context.text(font, subtitle, width / 2 - font.width(subtitle) / 2, top + 37, MUTED);
        context.fill(left + 10, top + 49, left + contentWidth - 10, top + 50, LINE);

        Component gameLabel = Component.translatable("key.gambling.menu.choose_game");
        context.text(font, gameLabel, left + 2, top + 56, WHITE);
        context.fill(left, top + 53, left + 3, top + 66, GOLD);

        Component choiceLabel = Component.translatable("key.gambling.menu.choose_option");
        context.text(font, choiceLabel, left + 2, top + 109, WHITE);
        context.fill(left, top + 106, left + 3, top + 119, GOLD);

        Component betLabel = Component.translatable("key.gambling.menu.bet_label");
        context.text(font, betLabel, left + 2, top + 158, WHITE);
        context.fill(left, top + 155, left + 3, top + 168, GOLD);

        Component balance = Component.translatable("key.gambling.menu.hint");
        context.text(font, balance, width / 2 - font.width(balance) / 2, bottom - 14, MUTED);
        super.render(context, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
