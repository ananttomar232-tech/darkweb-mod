package com.darkwebduo.world;

import com.darkwebduo.block.DeliveryBoxBlock;
import com.darkwebduo.block.KeycardDoorBlock;
import com.darkwebduo.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Cipher vault: a domed server-citadel with a raised dais in the middle, eight glowing server towers,
 * a sealed tunnel on the south side and a lobby that contains the Delivery Box (Dark Keycard) and the keycard door.
 *
 * Layout (north = -z, the lobby is south = +z of the arena; the boss kneels in the middle looking south):
 *   arena (radius R) -- wall (2 thick) -- tunnel (3 wide, 6 long) -- [KEYCARD DOOR] -- lobby (15 x 15)
 */
public final class CipherArena {
    public static final class Result {
        public final Vec3 bossPos;
        public final Vec3 lobbyPos;

        Result(Vec3 bossPos, Vec3 lobbyPos) {
            this.bossPos = bossPos;
            this.lobbyPos = lobbyPos;
        }
    }

    private static final int WALL_H = 12;

    private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState PDEEP = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState PINK = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState CYAN_GLASS = Blocks.CYAN_STAINED_GLASS.defaultBlockState();
    private static final BlockState CYAN_CONC = Blocks.CYAN_CONCRETE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private CipherArena() {}

    private static void set(ServerLevel level, BlockPos.MutableBlockPos mp, int x, int y, int z, BlockState st) {
        mp.set(x, y, z);
        if (level.getBlockState(mp) != st) level.setBlock(mp, st, 2);
    }

    private static void fill(ServerLevel level, BlockPos.MutableBlockPos mp, int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(level, mp, x, y, z, st);
                }
            }
        }
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    public static Result build(ServerLevel level, BlockPos origin, int radius) {
        final int R = Mth.clamp(radius, 16, 48);
        final int cx = origin.getX();
        final int cz = origin.getZ();
        final int y0 = origin.getY() - 1; // floor level
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        final double a = R + 2.0;
        final double b = 0.6 * (R + 2.0);
        final int domeBase = y0 + WALL_H;
        final int top = domeBase + (int) Math.ceil(b) + 2;
        final int tunnelZ0 = cz + R + 2;       // first block outside the arena wall
        final int zDoor = tunnelZ0 + 7;        // the keycard door lives in the lobby's north wall

        // ------------------------------------------------------------------ arena shell, floor and clearing
        for (int dx = -R - 3; dx <= R + 3; dx++) {
            for (int dz = -R - 3; dz <= R + 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 2.5) continue;
                int x = cx + dx;
                int z = cz + dz;
                double ang = Math.atan2(dz, dx);

                // foundation + floor
                for (int y = y0 - 4; y < y0; y++) set(level, mp, x, y, z, TILES);
                set(level, mp, x, y0, z, floorBlock(dx, dz, d, ang, R));

                if (d < R) {
                    // clear everything up to the dome and place the dome shell
                    for (int y = y0 + 1; y <= top; y++) {
                        BlockState st = AIR;
                        if (y > domeBase) {
                            double h = y - domeBase;
                            double e = Math.sqrt((d / a) * (d / a) + (h / b) * (h / b));
                            if (e >= 1.0) st = AIR;
                            else if (e >= 1.0 - 0.075) st = domeBlock(dx, dz, y - domeBase, ang, e);
                        }
                        set(level, mp, x, y, z, st);
                    }
                } else if (d < R + 2) {
                    // wall ring (server racks) up to the dome and the dome continuing over it
                    for (int y = y0 + 1; y <= top; y++) {
                        BlockState st = null;
                        if (y <= domeBase) {
                            st = wallBlock(y - y0, d, ang, R);
                        } else {
                            double h = y - domeBase;
                            double e = Math.sqrt((d / a) * (d / a) + (h / b) * (h / b));
                            if (e < 1.0 && e >= 1.0 - 0.075) st = domeBlock(dx, dz, y - domeBase, ang, e);
                            else if (e < 1.0 - 0.075) st = BRICKS;
                        }
                        if (st != null) set(level, mp, x, y, z, st);
                    }
                }
            }
        }

        // ------------------------------------------------------------------ dais in the middle
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= 8.0) continue;
                BlockState st;
                if (d < 2.6) st = CRYING;
                else if (d < 3.4) st = LANTERN;
                else if (d >= 6.6) st = (dx + dz) % 2 == 0 ? LANTERN : CHISELED;
                else if (((int) Math.floor(Math.atan2(dz, dx) / (Math.PI / 6.0) + 0.5)) % 2 == 0 && d > 4.0 && d < 6.0) st = CYAN_CONC;
                else st = BRICKS;
                set(level, mp, cx + dx, y0 + 1, cz + dz, st);
            }
        }

        // ------------------------------------------------------------------ eight server towers
        double towerD = Math.min(R * 0.68, R - 5.0);
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI / 8.0 + i * (Math.PI / 4.0);
            int tx = cx + (int) Math.round(Math.cos(ang) * towerD);
            int tz = cz + (int) Math.round(Math.sin(ang) * towerD);
            buildTower(level, mp, tx, y0 + 1, tz);
        }

        // ------------------------------------------------------------------ tunnel (arena wall -> door)
        // carve + frame: inner width 3 (x -1..1), inner height 4
        fill(level, mp, cx - 3, y0 - 1, tunnelZ0 - 3, cx + 3, y0 + 6, zDoor, BRICKS);
        fill(level, mp, cx - 1, y0 + 1, tunnelZ0 - 3, cx + 1, y0 + 4, zDoor - 1, AIR);
        fill(level, mp, cx - 1, y0, tunnelZ0 - 3, cx + 1, y0, zDoor, POLISHED);
        for (int z = tunnelZ0 - 2; z < zDoor; z += 3) {
            set(level, mp, cx, y0 + 5, z, LANTERN);
            set(level, mp, cx - 2, y0 + 2, z, LANTERN);
            set(level, mp, cx + 2, y0 + 2, z, LANTERN);
        }
        for (int z = tunnelZ0 - 3; z < zDoor; z += 2) {
            set(level, mp, cx - 1, y0, z, (z / 2) % 2 == 0 ? CYAN_CONC : POLISHED);
            set(level, mp, cx + 1, y0, z, (z / 2) % 2 == 0 ? CYAN_CONC : POLISHED);
        }

        // ------------------------------------------------------------------ lobby (south of the door)
        final int lx0 = cx - 8, lx1 = cx + 8;
        final int lz0 = zDoor, lz1 = zDoor + 16;
        fill(level, mp, lx0, y0 - 1, lz0, lx1, y0 + 9, lz1, BRICKS);
        fill(level, mp, lx0 + 1, y0 + 1, lz0 + 1, lx1 - 1, y0 + 8, lz1 - 1, AIR);
        for (int x = lx0 + 1; x <= lx1 - 1; x++) {
            for (int z = lz0 + 1; z <= lz1 - 1; z++) {
                set(level, mp, x, y0, z, ((x + z) & 1) == 0 ? POLISHED : PDEEP);
            }
        }
        // glowing floor cross + ring
        for (int i = -7; i <= 7; i++) {
            set(level, mp, cx + i, y0, zDoor + 8, i % 2 == 0 ? CYAN_CONC : LANTERN);
            set(level, mp, cx, y0, zDoor + 8 + i, i % 2 == 0 ? CYAN_CONC : LANTERN);
        }
        // ceiling lights and wall pillars
        for (int x = lx0 + 2; x <= lx1 - 2; x += 4) {
            for (int z = lz0 + 3; z <= lz1 - 3; z += 4) {
                set(level, mp, x, y0 + 9, z, LANTERN);
                set(level, mp, x, y0 + 8, z, LANTERN);
            }
        }
        for (int z = lz0 + 2; z <= lz1 - 2; z += 3) {
            for (int y = y0 + 1; y <= y0 + 8; y++) {
                set(level, mp, lx0 + 1, y, z, (y - y0) % 3 == 0 ? LANTERN : CHISELED);
                set(level, mp, lx1 - 1, y, z, (y - y0) % 3 == 0 ? LANTERN : CHISELED);
            }
        }
        // south exit (3 wide, 4 tall) with glowing frame
        fill(level, mp, cx - 1, y0 + 1, lz1, cx + 1, y0 + 4, lz1, AIR);
        fill(level, mp, cx - 2, y0 + 1, lz1, cx - 2, y0 + 5, lz1, LANTERN);
        fill(level, mp, cx + 2, y0 + 1, lz1, cx + 2, y0 + 5, lz1, LANTERN);
        fill(level, mp, cx - 1, y0 + 5, lz1, cx + 1, y0 + 5, lz1, CHISELED);

        // ------------------------------------------------------------------ the gate (door in the lobby's north wall)
        fill(level, mp, cx - 1, y0 + 1, zDoor, cx + 1, y0 + 4, zDoor, BRICKS);
        set(level, mp, cx - 1, y0 + 2, zDoor, LANTERN);
        set(level, mp, cx + 1, y0 + 2, zDoor, LANTERN);
        set(level, mp, cx, y0 + 3, zDoor, CHISELED);
        set(level, mp, cx, y0 + 4, zDoor, LANTERN);
        set(level, mp, cx, y0 + 1, zDoor, AIR);
        set(level, mp, cx, y0 + 2, zDoor, AIR);
        KeycardDoorBlock.place(level, new BlockPos(cx, y0 + 1, zDoor), Direction.NORTH);

        // ------------------------------------------------------------------ delivery box on a pedestal
        int bz = zDoor + 9;
        set(level, mp, cx, y0 + 1, bz, CHISELED);
        set(level, mp, cx - 1, y0 + 1, bz, POLISHED);
        set(level, mp, cx + 1, y0 + 1, bz, POLISHED);
        set(level, mp, cx, y0 + 1, bz - 1, POLISHED);
        set(level, mp, cx, y0 + 1, bz + 1, POLISHED);
        BlockState box = ModBlocks.DELIVERY_BOX.get().defaultBlockState()
                .setValue(DeliveryBoxBlock.FACING, Direction.SOUTH)
                .setValue(DeliveryBoxBlock.OPEN, Boolean.FALSE);
        set(level, mp, cx, y0 + 2, bz, box);
        // beacon-like light over the box
        for (int y = y0 + 3; y <= y0 + 7; y++) set(level, mp, cx, y, bz, Blocks.AIR.defaultBlockState());
        set(level, mp, cx - 1, y0 + 2, bz - 1, Blocks.END_ROD.defaultBlockState());
        set(level, mp, cx + 1, y0 + 2, bz - 1, Blocks.END_ROD.defaultBlockState());
        set(level, mp, cx - 1, y0 + 2, bz + 1, Blocks.END_ROD.defaultBlockState());
        set(level, mp, cx + 1, y0 + 2, bz + 1, Blocks.END_ROD.defaultBlockState());

        Vec3 bossPos = new Vec3(cx + 0.5, y0 + 2.0, cz + 0.5);
        Vec3 lobbyPos = new Vec3(cx + 0.5, y0 + 1.0, lz1 - 3.0 + 0.5);
        return new Result(bossPos, lobbyPos);
    }

    private static BlockState floorBlock(int dx, int dz, double d, double ang, int R) {
        if (d >= R - 1.5 && d < R) return PINK;
        if (d >= R - 4.0 && d < R - 3.0) return (dx + dz) % 2 == 0 ? LANTERN : CYAN_CONC;
        if (Math.abs(d - R * 0.5) < 0.6) return LANTERN;
        if (d > 9.0) {
            double sector = ang / (Math.PI / 6.0);
            double fr = Math.abs(sector - Math.round(sector));
            if (fr < 0.045) return ((int) d) % 4 == 0 ? LANTERN : CYAN_CONC;
        }
        if (((dx >> 2) + (dz >> 2)) % 2 == 0) return ((dx + dz) & 1) == 0 ? POLISHED : BRICKS;
        return ((dx + dz) & 1) == 0 ? BLACKSTONE : PDEEP;
    }

    private static BlockState wallBlock(int h, double d, double ang, int R) {
        double sector = ang / (Math.PI / 12.0);
        boolean pillar = Math.abs(sector - Math.round(sector)) < 0.2;
        if (h <= 1) return CHISELED;
        if (pillar) return (h % 4 == 0 && d < R + 1) ? LANTERN : BRICKS;
        if (d < R + 1) {
            if (h % 4 == 0) return LANTERN;
            if (h >= 6 && h <= 8 && hash((int) Math.round(ang * 40.0), h, 7) % 3 == 0) return CYAN_GLASS;
            return BLACKSTONE;
        }
        return POLISHED;
    }

    private static BlockState domeBlock(int dx, int dz, int h, double ang, double e) {
        double rib = ang / (Math.PI / 12.0);
        if (Math.abs(rib - Math.round(rib)) < 0.16) return BRICKS;
        if (h % 6 == 0) return LANTERN;
        if (hash(dx, dz, h) % 7 == 0) return CYAN_GLASS;
        return TILES;
    }

    private static void buildTower(ServerLevel level, BlockPos.MutableBlockPos mp, int tx, int y1, int tz) {
        for (int dy = 0; dy < 13; dy++) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    BlockState st;
                    boolean corner = Math.abs(ox) == 1 && Math.abs(oz) == 1;
                    if (corner) st = BRICKS;
                    else if (ox == 0 && oz == 0) st = BLACKSTONE;
                    else st = (dy % 3 == 1) ? LANTERN : BLACKSTONE;
                    set(level, mp, tx + ox, y1 + dy, tz + oz, st);
                }
            }
        }
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) set(level, mp, tx + ox, y1 + 13, tz + oz, CRYING);
        }
        set(level, mp, tx, y1 + 14, tz, Blocks.END_ROD.defaultBlockState());
        set(level, mp, tx, y1 + 15, tz, Blocks.END_ROD.defaultBlockState());
        for (int ox = -2; ox <= 2; ox++) {
            set(level, mp, tx + ox, y1, tz - 2, PINK);
            set(level, mp, tx + ox, y1, tz + 2, PINK);
        }
    }
}
