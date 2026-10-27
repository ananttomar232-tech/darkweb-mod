package com.darkwebduo.util;

import com.darkwebduo.block.GiantMonitorBlock;
import com.darkwebduo.block.entity.GiantMonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Helper that connects the keyboard and mouse blocks to the closest giant monitor. */
public final class PcSetup {
    private PcSetup() {}

    @Nullable
    public static GiantMonitorBlockEntity findMonitor(Level level, BlockPos origin) {
        int r = GiantMonitorBlockEntity.DEVICE_RANGE;
        GiantMonitorBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-r, -3, -r), origin.offset(r, 3, r))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof GiantMonitorBlock && GiantMonitorBlock.isController(s)) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof GiantMonitorBlockEntity) {
                    double d = p.distSqr(origin);
                    if (d < bestDist) {
                        bestDist = d;
                        best = (GiantMonitorBlockEntity) be;
                    }
                }
            }
        }
        return best;
    }
}
