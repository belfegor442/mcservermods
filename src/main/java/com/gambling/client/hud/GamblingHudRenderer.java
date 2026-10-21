package com.gambling.client.hud;

import com.gambling.client.ClientGamblingState;
import com.gambling.game.PlayerState;
import com.gambling.util.ChipsFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class GamblingHudRenderer {
    private static final int MARGIN = 6;
    private static final int PANEL_WIDTH = 216;
    private static final int HEAD_SIZE = 20;

    private static final int SHADOW = 0x55000000;
    private static final int BORDER = 0xAA0A0D12;
    private static final int BACKGROUND = 0xE610141B;
    private static final int SURFACE = 0xE91A202B;
    private static final int DIVIDER = 0x554C5665;
    private static final int GOLD = 0xFFFFD36A;
    private static final int WHITE = 0xFFF0F2F5;
    private static final int MUTED = 0xFF9BA6B4;
    private static final int CYAN = 0xFF82D9F5;
    private static final int GREEN = 0xFF8CE0A1;
    private static final int AMBER = 0xFFFFC36A;

    private GamblingHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) {
            return;
        }

        ClientGamblingState state = ClientGamblingState.get();
        if (!state.hasReceivedProfile()) {
            return;
        }

        Font font = client.font;
        int x = MARGIN;
        int y = MARGIN;
        int width = Math.min(PANEL_WIDTH, Math.max(150, client.getWindow().getGuiScaledWidth() - MARGIN * 2));
        boolean hasResult = state.hasSession() && !state.result().isEmpty();
        int height = state.hasSession() ? (hasResult ? 143 : 130) : 98;

        context.fill(x + 2, y + 2, x + width + 2, y + height + 2, SHADOW);
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);
        context.fill(x, y, x + width, y + height, BACKGROUND);
        context.fill(x, y, x + width, y + 2, GOLD);

        Component title = Component.literal("CASINO").withStyle(ChatFormatting.BOLD);
        Component level = Component.literal("LV " + state.level()).withStyle(ChatFormatting.BOLD);
        context.text(font, title, x + 9, y + 6, GOLD);
        context.text(font, level, x + width - 9 - font.width(level), y + 6, MUTED);
        context.fill(x + 8, y + 19, x + width - 8, y + 20, DIVIDER);

        renderPlayerHead(context, client, x + 9, y + 25);

        Component playerName = fit(Component.literal(state.playerName()).withStyle(ChatFormatting.BOLD),
                font, width - 48);
        context.text(font, playerName, x + 36, y + 28, WHITE);
        context.text(font, Component.literal("PLAYER PROFILE"), x + 36, y + 40, MUTED);

        int balanceTop = y + 52;
        context.fill(x + 8, balanceTop, x + width - 8, balanceTop + 24, SURFACE);
        context.fill(x + 8, balanceTop, x + 10, balanceTop + 24, GOLD);

        Component chipsLabel = Component.translatable("key.gambling.hud.chips");
        Component chipsValue = Component.literal(ChipsFormat.format(state.chips()))
                .withStyle(ChatFormatting.BOLD);
        context.text(font, chipsLabel, x + 15, balanceTop + 7, MUTED);
        context.text(font, chipsValue, x + width - 15 - font.width(chipsValue),
                balanceTop + 6, GOLD);

        int statusY = y + 83;
        int statusColor = statusColor(state.playerState());
        context.fill(x + 10, statusY + 3, x + 14, statusY + 7, statusColor);
        Component status = fit(stateLabel(state.playerState()), font, width - 32);
        context.text(font, status, x + 20, statusY, statusColor);

        if (state.hasSession()) {
            int sectionTop = y + 97;
            context.fill(x + 8, sectionTop, x + width - 8, sectionTop + 1, DIVIDER);

            String game = state.gameType().toUpperCase(Locale.ROOT);
            Component gameLine = Component.translatable("key.gambling.hud.game.type", game)
                    .withStyle(ChatFormatting.BOLD);
            Component betLine = Component.translatable("key.gambling.hud.game.bet",
                    ChipsFormat.format(state.bet()));
            context.text(font, fit(gameLine, font, width - 18), x + 9, y + 103, CYAN);
            context.text(font, fit(betLine, font, width - 18), x + 9, y + 115, WHITE);

            if (hasResult) {
                Component resultLine = Component.translatable("key.gambling.hud.game.result", state.result());
                context.text(font, fit(resultLine, font, width - 18), x + 9, y + 127, GOLD);
            }
        }
    }

    private static void renderPlayerHead(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
        AbstractClientPlayer player = client.player;
        if (player == null) {
            return;
        }
        Identifier skin = player.getSkin().body().texturePath();
        context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y,
                8.0f, 8.0f, HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
        context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y,
                40.0f, 8.0f, HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
    }

    private static Component fit(Component component, Font font, int maxWidth) {
        if (font.width(component) <= maxWidth) {
            return component;
        }

        String text = component.getString();
        String suffix = "…";
        int end = text.length();
        while (end > 0 && font.width(Component.literal(text.substring(0, end) + suffix)) > maxWidth) {
            end--;
        }
        return Component.literal(text.substring(0, end) + suffix);
    }

    private static int statusColor(PlayerState state) {
        return switch (state) {
            case AVAILABLE -> GREEN;
            case IN_GAME -> CYAN;
            case BETTING -> AMBER;
            case FINALIZING -> GOLD;
        };
    }

    private static Component stateLabel(PlayerState state) {
        return switch (state) {
            case AVAILABLE -> Component.translatable("key.gambling.hud.state.available");
            case IN_GAME -> Component.translatable("key.gambling.hud.state.in_game");
            case BETTING -> Component.translatable("key.gambling.hud.state.betting");
            case FINALIZING -> Component.translatable("key.gambling.hud.state.finalizing");
        };
    }
}
