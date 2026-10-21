package com.gambling.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side 3-row chest menu driving a {@link CasinoGui}. The client side
 * is the plain vanilla GENERIC_9x3 menu, so any client can use it; the
 * interesting half lives here. Every click — including attempts to take,
 * move or hotbar-swap items — is swallowed and the full menu state is pushed
 * back, so the menu can only ever trigger its own actions.
 */
public final class CasinoMenu extends ChestMenu {
    private final CasinoGui gui;

    public CasinoMenu(int containerId, Inventory inventory,
                      SimpleContainer container, CasinoGui gui) {
        super(MenuType.GENERIC_9x3, containerId, inventory, container, 3);
        this.gui = gui;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (slotId == CasinoGui.SLOT_CLOSE && input == ContainerInput.PICKUP) {
                serverPlayer.closeContainer();
                return;
            }
            if (slotId >= 0 && slotId < CasinoGui.SLOT_COUNT
                    && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
                gui.onSlotClicked(serverPlayer, slotId);
                sendAllDataToRemote();
                return;
            }
        }
        // Any other interaction (inventory moves, drags, hotbar swaps,
        // outside clicks) must not touch the GUI: resync and ignore.
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        gui.detach(this);
        super.removed(player);
    }
}
