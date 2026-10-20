package com.gambling.client.hud;

import com.gambling.client.ClientGamblingState;
import com.gambling.game.PlayerState;
import com.gambling.util.ChipsFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class GamblingHudRenderer {
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int HEAD_SIZE = 16;
    private static final int BG_COLOR = 0x88000000;
    private static final int BORDER_COLOR = 0x66FFFFFF;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int CHIP_COLOR = 0xFFFFD75A;
    private static final int LEVEL_COLOR = 0xFF7FD8FF;

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
        int x = PADDING;
        int y = PADDING;

        Component nameLine = Component.literal(state.playerName())
                .append(" ")
                .append(Component.translatable("key.gambling.hud.level", String.valueOf(state.level())));
        Component chipsLine = Component.literal("◆ ")
                .append(Component.translatable("key.gambling.hud.chips"))
                .append(": ")
                .append(Component.literal(ChipsFormat.format(state.chips())));
        Component stateLine = Component.translatable("key.gambling.hud.state", stateLabel(state.playerState()));

        int width = font.width(nameLine);
        width = Math.max(width, font.width(chipsLine));
        width = Math.max(width, font.width(stateLine));

        if (state.hasSession()) {
            Component gameLine = Component.translatable("key.gambling.hud.game.type", state.gameType());
            Component betLine = Component.translatable("key.gambling.hud.game.bet", ChipsFormat.format(state.bet()));
            Component roundLine = Component.translatable("key.gambling.hud.game.round", stateLabel(state.playerState()));
            width = Math.max(width, font.width(gameLine));
            width = Math.max(width, font.width(betLine));
            width = Math.max(width, font.width(roundLine));
            if (!state.result().isEmpty()) {
                Component resultLine = Component.translatable("key.gambling.hud.game.result", state.result());
                width = Math.max(width, font.width(resultLine));
            }
        }

        int headOffset = HEAD_SIZE + 3;
        int panelWidth = width + PADDING * 2 + headOffset;
        int lines = 3 + (state.hasSession() ? (state.result().isEmpty() ? 3 : 4) : 0);
        int panelHeight = lines * LINE_HEIGHT + PADDING * 2;

        context.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, BORDER_COLOR);
        context.fill(x, y, x + panelWidth, y + panelHeight, BG_COLOR);

        int textX = x + PADDING + headOffset;
        int textY = y + PADDING;

        renderPlayerHead(context, client, x + PADDING, y + PADDING);

        context.text(font, nameLine, textX, textY, TEXT_COLOR);
        textY += LINE_HEIGHT;
        context.text(font, chipsLine, textX, textY, CHIP_COLOR);
        textY += LINE_HEIGHT;
        context.text(font, stateLine, textX, textY, TEXT_COLOR);
        textY += LINE_HEIGHT;

        if (state.hasSession()) {
            Component gameLine = Component.translatable("key.gambling.hud.game.type", state.gameType());
            Component betLine = Component.translatable("key.gambling.hud.game.bet", ChipsFormat.format(state.bet()));
            Component roundLine = Component.translatable("key.gambling.hud.game.round", stateLabel(state.playerState()));
            context.text(font, gameLine, textX, textY, LEVEL_COLOR);
            textY += LINE_HEIGHT;
            context.text(font, betLine, textX, textY, CHIP_COLOR);
            textY += LINE_HEIGHT;
            context.text(font, roundLine, textX, textY, TEXT_COLOR);
            textY += LINE_HEIGHT;
            if (!state.result().isEmpty()) {
                Component resultLine = Component.translatable("key.gambling.hud.game.result", state.result());
                context.text(font, resultLine, textX, textY, CHIP_COLOR);
            }
        }
    }

    private static void renderPlayerHead(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
        AbstractClientPlayer player = client.player;
        if (player == null) {
            return;
        }
        Identifier skin = player.getSkin().body().texturePath();
        context.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f, 8, 8, 64, 64);
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
