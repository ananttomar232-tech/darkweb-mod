package com.darkwebduo.world;

import com.darkwebduo.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Omega Sanctum: a floating, open-air white-and-gold citadel disc. Flat and completely free of obstacles inside the combat
 * radius (so every Omega Root attack pattern is fair); gold ring at r=31..32 marks the edge of the fight; a colonnade of 24 light-crowned
 * pillars stands outside it; monoliths float in the sky. An Omega glyph is inlaid around the Omega Terminal in the middle.
 */
public final class OmegaArena {
    public static final int FLOOR_R = 40;
    public static final int COMBAT_R = 32;
    private static final int CLEAR_H = 38;

    private static final BlockState QBRICK = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState QSMOOTH = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState QCHISEL = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState QPILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState OCHRE = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
    private static final BlockState PBS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState PDEEP = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState PURPUR = Blocks.PURPUR_PILLAR.defaultBlockState();
    private static final BlockState END_ROD = Blocks.END_ROD.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private OmegaArena() {}

    private static void set(ServerLevel level, BlockPos.MutableBlockPos mp, int x, int y, int z, BlockState st) {
        mp.set(x, y, z);
        if (level.getBlockState(mp) != st) level.setBlock(mp, st, 2);
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    private static double angDiff(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    /** floor block at offset (dx,dz) from the centre */
    private static BlockState floor(int dx, int dz) {
        double r = Math.sqrt((double) dx * dx + (double) dz * dz);
        double th = Math.toDegrees(Math.atan2(dz, dx)); // +90 = south (+z)
        // the terminal altar
        if (r <= 2.6) return GOLD;
        if (r <= 3.7) return CRYING;
        // the Omega glyph: an arc open to the south that curls into two feet
        if (r >= 8.9 && r <= 10.5 && Math.abs(angDiff(th, 90.0)) > 38.0) return r >= 9.4 && r <= 10.0 ? OCHRE : GOLD;
        if (dz >= 7 && dz <= 8 && Math.abs(dx) >= 6 && Math.abs(dx) <= 11) return Math.abs(dx) >= 10 ? OCHRE : GOLD;
        // boundary of the fight
        if (r >= 31.0 && r < 32.2) return (hash(dx, 7, dz) & 3) == 0 ? OCHRE : GOLD;
        // outer apron
        if (r >= 32.2) {
            if (r >= 39.0 && r < 40.4) return PBS;
            int h = hash(dx, 3, dz) % 100;
            return h < 8 ? GILDED : (h < 14 ? CRYING : PBS);
        }
        // twelve gilded spokes
        for (int k = 0; k < 12; k++) {
            double ang = Math.toRadians(k * 30.0);
            double along = dx * Math.cos(ang) + dz * Math.sin(ang);
            double perp = Math.abs(dx * Math.sin(ang) - dz * Math.cos(ang));
            if (along >= 5.0 && along <= 30.5 && perp < 0.62) {
                return ((int) Math.floor(along) & 3) == 0 ? OCHRE : GILDED;
            }
        }
        // rings
        if (r >= 16.0 && r < 17.2) return (hash(dx, 1, dz) & 7) == 0 ? CRYING : PBS;
        if (r >= 24.4 && r < 25.6) return (hash(dx, 2, dz) & 7) == 0 ? AMETHYST : QCHISEL;
        // marble
        int h = hash(dx, 0, dz) % 100;
        return h < 55 ? QBRICK : (h < 90 ? QSMOOTH : CALCITE);
    }

    private static void column(ServerLevel level, BlockPos.MutableBlockPos mp, int px, int y0, int pz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, mp, px + dx, y0 + 1, pz + dz, QBRICK);
                set(level, mp, px + dx, y0 + 2, pz + dz, dx == 0 && dz == 0 ? GOLD : QSMOOTH);
            }
        }
        for (int y = 3; y <= 15; y++) {
            boolean collar = y == 5 || y == 10;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean plus = dx == 0 || dz == 0;
                    if (collar) set(level, mp, px + dx, y0 + y, pz + dz, plus ? GOLD : QCHISEL);
                    else if (plus) set(level, mp, px + dx, y0 + y, pz + dz, QPILLAR);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) set(level, mp, px + dx, y0 + 16, pz + dz, Math.abs(dx) + Math.abs(dz) <= 3 ? QSMOOTH : AIR);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(level, mp, px + dx, y0 + 17, pz + dz, dx == 0 && dz == 0 ? OCHRE : QCHISEL);
        }
        for (int y = 18; y <= 21; y++) set(level, mp, px, y0 + y, pz, END_ROD);
    }

    private static void monolith(ServerLevel level, BlockPos.MutableBlockPos mp, int px, int py, int pz, int h) {
        for (int y = 0; y < h; y++) {
            BlockState st = y == 0 || y == h - 1 ? GOLD : (y == h / 2 ? CRYING : (y % 2 == 0 ? PURPUR : AMETHYST));
            set(level, mp, px, py + y, pz, st);
            if (y > 0 && y < h - 1 && y != h / 2) continue;
            if (y == h / 2) {
                set(level, mp, px + 1, py + y, pz, CRYING);
                set(level, mp, px - 1, py + y, pz, CRYING);
                set(level, mp, px, py + y, pz + 1, CRYING);
                set(level, mp, px, py + y, pz - 1, CRYING);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (Math.abs(dx) + Math.abs(dz) == 2) continue;
                set(level, mp, px + dx, py + 1, pz + dz, AMETHYST);
                set(level, mp, px + dx, py + h - 2, pz + dz, AMETHYST);
            }
        }
        set(level, mp, px, py + h, pz, END_ROD);
        set(level, mp, px, py - 1, pz, END_ROD.setValue(net.minecraft.world.level.block.EndRodBlock.FACING, net.minecraft.core.Direction.DOWN));
    }

    /** builds the sanctum around {@code origin} (floor = the block under it) and returns the Omega Terminal's position */
    public static BlockPos build(ServerLevel level, BlockPos origin) {
        final int cx = origin.getX();
        final int cz = origin.getZ();
        final int y0 = origin.getY() - 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        // 1. clear the airspace (a generous cylinder so the descent and the rift have room)
        final int clr = FLOOR_R + 4;
        for (int dx = -clr; dx <= clr; dx++) {
            for (int dz = -clr; dz <= clr; dz++) {
                if (dx * dx + dz * dz > clr * clr) continue;
                for (int y = y0 + 1; y <= y0 + CLEAR_H; y++) set(level, mp, cx + dx, y, cz + dz, AIR);
            }
        }

        // 2. floating disc: patterned floor + tapering rock underside with crystal veins
        for (int dx = -FLOOR_R - 1; dx <= FLOOR_R + 1; dx++) {
            for (int dz = -FLOOR_R - 1; dz <= FLOOR_R + 1; dz++) {
                double r = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (r > FLOOR_R + 0.5) continue;
                int x = cx + dx, z = cz + dz;
                set(level, mp, x, y0, z, floor(dx, dz));
                int depth = 3 + (int) ((FLOOR_R - r) * 0.42) + (hash(dx, 9, dz) % 3);
                for (int d = 1; d <= depth; d++) {
                    int h = hash(dx, d, dz) % 100;
                    BlockState st = d == 1 ? PBS : (h < 4 ? CRYING : (h < 8 ? AMETHYST : (h < 50 ? DEEPSLATE : PDEEP)));
                    set(level, mp, x, y0 - d, z, st);
                }
            }
        }

        // 3. parapet with gates every 90 degrees (south gate is where players arrive)
        for (int dx = -FLOOR_R - 1; dx <= FLOOR_R + 1; dx++) {
            for (int dz = -FLOOR_R - 1; dz <= FLOOR_R + 1; dz++) {
                double r = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (r < 39.2 || r > 40.4) continue;
                double th = Math.toDegrees(Math.atan2(dz, dx));
                boolean gate = false;
                for (int k = 0; k < 4; k++) if (Math.abs(angDiff(th, k * 90.0)) < 5.0) gate = true;
                if (gate) continue;
                set(level, mp, cx + dx, y0 + 1, cz + dz, PBS);
                set(level, mp, cx + dx, y0 + 2, cz + dz, hash(dx, 5, dz) % 6 == 0 ? OCHRE : PBS);
            }
        }

        // 4. the colonnade: 24 light-crowned pillars just outside the fight
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15.0 + 7.5);
            int px = cx + (int) Math.round(Math.cos(a) * 36.5);
            int pz = cz + (int) Math.round(Math.sin(a) * 36.5);
            column(level, mp, px, y0, pz);
        }

        // 5. floating monoliths in the sky
        for (int i = 0; i < 9; i++) {
            double a = Math.toRadians(i * 40.0 + 13.0 + (hash(i, 77, 1) % 20));
            double rr = 47.0 + (hash(i, 78, 2) % 9);
            int px = cx + (int) Math.round(Math.cos(a) * rr);
            int pz = cz + (int) Math.round(Math.sin(a) * rr);
            int py = y0 + 8 + (hash(i, 79, 3) % 18);
            monolith(level, mp, px, py, pz, 8 + (hash(i, 80, 4) % 6));
        }

        // 6. the terminal itself
        BlockPos terminal = new BlockPos(cx, y0 + 1, cz);
        level.setBlock(terminal, ModBlocks.OMEGA_TERMINAL.get().defaultBlockState(), 3);
        return terminal;
    }
}
