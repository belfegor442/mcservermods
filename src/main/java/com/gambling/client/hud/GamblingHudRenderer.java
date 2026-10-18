package com.gambling.client.hud;

import com.gambling.client.ClientGamblingState;
import com.gambling.game.PlayerState;
import com.gambling.util.ChipsFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        ClientGamblingState state = ClientGamblingState.get();
        if (!state.hasReceivedProfile()) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        int x = PADDING;
        int y = PADDING;

        Text nameLine = Text.literal(state.playerName())
                .append(" ")
                .append(Text.translatable("key.gambling.hud.level", String.valueOf(state.level())));
        Text chipsLine = Text.literal("◆ ")
                .append(Text.translatable("key.gambling.hud.chips"))
                .append(": ")
                .append(Text.literal(ChipsFormat.format(state.chips())));
        Text stateLine = Text.translatable("key.gambling.hud.state", stateLabel(state.playerState()));

        int width = textRenderer.getWidth(nameLine);
        width = Math.max(width, textRenderer.getWidth(chipsLine));
        width = Math.max(width, textRenderer.getWidth(stateLine));

        if (state.hasSession()) {
            Text gameLine = Text.translatable("key.gambling.hud.game.type", state.gameType());
            Text betLine = Text.translatable("key.gambling.hud.game.bet", ChipsFormat.format(state.bet()));
            Text roundLine = Text.translatable("key.gambling.hud.game.round", stateLabel(state.playerState()));
            width = Math.max(width, textRenderer.getWidth(gameLine));
            width = Math.max(width, textRenderer.getWidth(betLine));
            width = Math.max(width, textRenderer.getWidth(roundLine));
            if (!state.result().isEmpty()) {
                Text resultLine = Text.translatable("key.gambling.hud.game.result", state.result());
                width = Math.max(width, textRenderer.getWidth(resultLine));
            }
        }

        int headOffset = client.player != null ? HEAD_SIZE + 3 : 0;
        int panelWidth = width + PADDING * 2 + headOffset;
        int lines = 3 + (state.hasSession() ? (state.result().isEmpty() ? 3 : 4) : 0);
        int panelHeight = lines * LINE_HEIGHT + PADDING * 2;

        context.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, BORDER_COLOR);
        context.fill(x, y, x + panelWidth, y + panelHeight, BG_COLOR);

        int textX = x + PADDING + headOffset;
        int textY = y + PADDING;

        renderPlayerHead(context, client, x + PADDING, y + PADDING);

        context.drawTextWithShadow(textRenderer, nameLine, textX, textY, TEXT_COLOR);
        textY += LINE_HEIGHT;
        context.drawTextWithShadow(textRenderer, chipsLine, textX, textY, CHIP_COLOR);
        textY += LINE_HEIGHT;
        context.drawTextWithShadow(textRenderer, stateLine, textX, textY, TEXT_COLOR);
        textY += LINE_HEIGHT;

        if (state.hasSession()) {
            Text gameLine = Text.translatable("key.gambling.hud.game.type", state.gameType());
            Text betLine = Text.translatable("key.gambling.hud.game.bet", ChipsFormat.format(state.bet()));
            Text roundLine = Text.translatable("key.gambling.hud.game.round", stateLabel(state.playerState()));
            context.drawTextWithShadow(textRenderer, gameLine, textX, textY, LEVEL_COLOR);
            textY += LINE_HEIGHT;
            context.drawTextWithShadow(textRenderer, betLine, textX, textY, CHIP_COLOR);
            textY += LINE_HEIGHT;
            context.drawTextWithShadow(textRenderer, roundLine, textX, textY, TEXT_COLOR);
            textY += LINE_HEIGHT;
            if (!state.result().isEmpty()) {
                Text resultLine = Text.translatable("key.gambling.hud.game.result", state.result());
                context.drawTextWithShadow(textRenderer, resultLine, textX, textY, CHIP_COLOR);
            }
        }
    }

    private static void renderPlayerHead(DrawContext context, MinecraftClient client, int x, int y) {
        AbstractClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        Identifier skin = player.getSkinTextures().texture();
        context.drawTexture(skin, x, y, 8.0f, 8.0f, 8, 8, 64, 64);
    }

    private static Text stateLabel(PlayerState state) {
        return switch (state) {
            case AVAILABLE -> Text.translatable("key.gambling.hud.state.available");
            case IN_GAME -> Text.translatable("key.gambling.hud.state.in_game");
            case BETTING -> Text.translatable("key.gambling.hud.state.betting");
            case FINALIZING -> Text.translatable("key.gambling.hud.state.finalizing");
        };
    }
}
