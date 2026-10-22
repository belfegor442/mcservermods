package com.gambling.block;

import com.gambling.server.CasinoRound;
import com.gambling.server.GamblingServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Decorative casino table block. Placing it gives the world a permanent,
 * physical entrance to the casino: right-clicking it opens the same
 * server-driven chest GUI as /casino and the auto-open on join, so players
 * never need a command. The open happens entirely server-side and therefore
 * works for vanilla clients.
 */
public final class CasinoTableBlock extends Block {
    public CasinoTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            GamblingServerState gambling = GamblingServerState.get();
            if (gambling != null) {
                gambling.openCasinoMenu(serverPlayer);
                CasinoRound.playClickSound(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
