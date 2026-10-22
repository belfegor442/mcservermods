package com.gambling.client.gametest;

import com.gambling.GamblingMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * End-to-end client game test for the chest casino menu. It boots a real
 * dedicated server with the mod, joins it from a real client and verifies the
 * exact flows players depend on: the auto-open menu on join, the decorative
 * casino table opening the menu on right-click, and a full round through the
 * GUI slot click path. Failures here are the errors players would see in
 * their logs.
 */
public final class CasinoClientGameTest implements FabricClientGameTest {
    private static final int SLOT_PROFILE = 0;
    private static final int SLOT_PLAY = 22;
    private static final int SLOT_RESULT = 8;
    private static final int EXPECTED_SLOTS = 63; // 27 chest slots + 36 player inventory

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
             TestDedicatedServerConnection connection = server.connect()) {

            connection.waitForChunksRender();

            // 1) The menu auto-opens on join and its title must be real text,
            // not a raw translation key (vanilla clients have no mod lang).
            context.waitForScreen(ContainerScreen.class);
            assertTranslatedMenu(context, "join auto-open");

            // Capture the balance line from the profile slot before the round.
            String balanceBefore = profileLore(context);

            // Close the menu the same way a player would.
            context.runOnClient(client -> {
                Screen screen = client.gui.screen();
                if (screen != null) {
                    screen.onClose();
                }
            });
            context.waitFor(client -> client.gui.screen() == null, 100);
            context.waitTicks(5);

            // 2) Place the decorative table in front of the player and
            // right-click it with an empty hand: the menu must open again.
            server.runCommand("execute as @p at @s run setblock ~3 ~ ~ "
                    + GamblingMod.MOD_ID + ":casino_table");
            server.runCommand("clear @p");
            context.waitTicks(5);

            BlockPos table = context.computeOnClient(client ->
                    client.player.blockPosition().offset(3, 0, 0));
            check(server.computeOnServer(s -> s.overworld().getBlockState(table).getBlock()
                            instanceof com.gambling.block.CasinoTableBlock),
                    "casino table block was not placed in the world");
            context.getInput().lookAt(table);
            context.waitTicks(3);
            context.getInput().pressMouse(1);
            context.waitForScreen(ContainerScreen.class);
            assertTranslatedMenu(context, "casino table right-click");

            // 3) Click the JUGAR (PLAY) slot exactly like a player: the server
            // runs a full round and resyncs the outcome slot plus the balance.
            context.runOnClient(client -> {
                if (client.gui.screen() instanceof ContainerScreen screen
                        && client.gameMode != null) {
                    client.gameMode.handleContainerInput(
                            screen.getMenu().containerId, SLOT_PLAY, 0,
                            ContainerInput.PICKUP, client.player);
                }
            });
            connection.waitForClientboundPackets();
            context.waitTicks(10);

            check(context.computeOnClient(client ->
                            client.gui.screen() instanceof ContainerScreen screen
                                    && !screen.getMenu().getSlot(SLOT_RESULT).getItem().isEmpty()),
                    "round result slot is empty after clicking PLAY");
            check(context.computeOnClient(client ->
                            client.gui.screen() instanceof ContainerScreen screen
                                    && screen.getMenu().slots.size() == EXPECTED_SLOTS),
                    "menu does not expose the full 27+36 chest menu");
            String balanceAfter = profileLore(context);
            check(!balanceBefore.equals(balanceAfter),
                    "balance did not change after a completed round: '" + balanceBefore + "'");

            context.takeScreenshot("casino_menu_round");
        }
    }

    private static void assertTranslatedMenu(ClientGameTestContext context, String phase) {
        String title = context.computeOnClient(client ->
                client.gui.screen() instanceof ContainerScreen screen
                        ? screen.getTitle().getString() : "");
        check(!title.isEmpty(), "no chest menu open (" + phase + ")");
        check(!title.startsWith("key."), "menu shows raw translation key (" + phase + "): '"
                + title + "'");
        int slots = context.computeOnClient(client ->
                client.gui.screen() instanceof ContainerScreen screen
                        ? screen.getMenu().slots.size() : -1);
        check(slots == EXPECTED_SLOTS, "menu has " + slots + " slots, expected "
                + EXPECTED_SLOTS + " (" + phase + ")");
    }

    private static String profileLore(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            if (!(client.gui.screen() instanceof ContainerScreen screen)) {
                return "<no-menu>";
            }
            ItemStack stack = screen.getMenu().getSlot(SLOT_PROFILE).getItem();
            var lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) {
                return stack.getHoverName().getString();
            }
            StringBuilder text = new StringBuilder();
            lore.lines().forEach(line -> text.append(line.getString()).append('|'));
            return text.toString();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
