package com.darkwebduo;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config. ALWAYS read values through the static helper methods below: they fall back to the
 * defaults if the spec is not loaded yet (attribute creation happens very early).
 */
public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue CIPHER_HEALTH = B
            .comment("Max health of the Cipher Warden (boss 1).")
            .defineInRange("cipherWardenHealth", 1500.0, 20.0, 1_000_000.0);
    public static final ForgeConfigSpec.DoubleValue OMEGA_HEALTH = B
            .comment("Max health of Omega Root (boss 2).")
            .defineInRange("omegaRootHealth", 15000.0, 20.0, 10_000_000.0);
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT = B
            .comment("Multiplier applied to ALL boss attack damage (1.0 = default).")
            .defineInRange("bossDamageMultiplier", 1.0, 0.0, 100.0);
    public static final ForgeConfigSpec.IntValue ORDER_COST = B
            .comment("Emeralds the Giant Monitor charges for ordering one Delivery Box.")
            .defineInRange("deliveryBoxOrderCostEmeralds", 16, 0, 2304);
    public static final ForgeConfigSpec.IntValue CIPHER_EMERALDS = B
            .comment("Emeralds dropped by the Cipher Warden.")
            .defineInRange("cipherWardenEmeraldDrop", 700, 0, 100_000);
    public static final ForgeConfigSpec.BooleanValue WAKE_ONLY_SURVIVAL = B
            .comment("Bosses only wake for survival/adventure players (creative/spectator are ignored).")
            .define("bossesWakeOnlyForSurvival", true);
    public static final ForgeConfigSpec.IntValue ARENA_RADIUS = B
            .comment("Radius of the Cipher arena created by /summon boss fight.")
            .defineInRange("arenaRadius", 30, 16, 48);

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {}

    public static double cipherHealth() { try { return CIPHER_HEALTH.get(); } catch (Exception e) { return 1500.0; } }
    public static double omegaHealth() { try { return OMEGA_HEALTH.get(); } catch (Exception e) { return 15000.0; } }
    public static double damageMult() { try { return DAMAGE_MULT.get(); } catch (Exception e) { return 1.0; } }
    public static int orderCost() { try { return ORDER_COST.get(); } catch (Exception e) { return 16; } }
    public static int cipherEmeralds() { try { return CIPHER_EMERALDS.get(); } catch (Exception e) { return 700; } }
    public static boolean wakeOnlySurvival() { try { return WAKE_ONLY_SURVIVAL.get(); } catch (Exception e) { return true; } }
    public static int arenaRadius() { try { return ARENA_RADIUS.get(); } catch (Exception e) { return 30; } }
}
