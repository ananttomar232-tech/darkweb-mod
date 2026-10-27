package com.darkwebduo.command;

import com.darkwebduo.Config;
import com.darkwebduo.entity.CipherWardenEntity;
import com.darkwebduo.world.CipherArena;
import com.darkwebduo.world.OmegaArena;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * /summon boss fight [pos]         -> builds the Cipher arena (keycard-gated) and the dormant Cipher Warden
 * /summon boss fight omega [pos]   -> builds the Omega Sanctum with the Omega Terminal in the middle
 * (merged into vanilla's /summon tree by Brigadier, the literal "boss" does not clash with the entity argument)
 */
public final class BossFightCommand {
    private BossFightCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("summon")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("boss")
                        .then(Commands.literal("fight")
                                .executes(ctx -> cipher(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> cipher(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))
                                .then(Commands.literal("omega")
                                        .executes(ctx -> omega(ctx.getSource(), BlockPos.containing(ctx.getSource().getPosition())))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> omega(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))));
    }

    private static int cipher(CommandSourceStack src, BlockPos origin) {
        ServerLevel level = src.getLevel();
        src.sendSuccess(() -> Component.translatable("command.darkweb_duo.arena_building"), true);
        CipherArena.Result result = CipherArena.build(level, origin, Config.arenaRadius());
        CipherWardenEntity.spawnDormant(level, result.bossPos);
        Entity who = src.getEntity();
        if (who instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer) who;
            Vec3 lobby = result.lobbyPos;
            p.teleportTo(level, lobby.x, lobby.y, lobby.z, 180.0F, 0.0F);
        }
        src.sendSuccess(() -> Component.translatable("command.darkweb_duo.arena_ready"), true);
        return 1;
    }

    private static int omega(CommandSourceStack src, BlockPos origin) {
        ServerLevel level = src.getLevel();
        src.sendSuccess(() -> Component.translatable("command.darkweb_duo.omega_building"), true);
        BlockPos terminal = OmegaArena.build(level, origin);
        Entity who = src.getEntity();
        if (who instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer) who;
            p.teleportTo(level, terminal.getX() + 0.5, terminal.getY() + 1.0, terminal.getZ() + 22.5, 180.0F, 0.0F);
        }
        src.sendSuccess(() -> Component.translatable("command.darkweb_duo.omega_ready"), true);
        return 1;
    }
}
