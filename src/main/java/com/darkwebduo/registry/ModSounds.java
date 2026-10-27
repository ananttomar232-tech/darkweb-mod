package com.darkwebduo.registry;

import com.darkwebduo.DarkWebDuoMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every sound the mod uses. The registry name equals the field name in lower case.
 * tools/gen_audio.py synthesises one .ogg per entry (assets/darkweb_duo/sounds/<name>.ogg) and writes sounds.json.
 * The comment after each entry is the creative brief + target length for the synthesiser.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DarkWebDuoMod.MODID);

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(DarkWebDuoMod.MODID, name)));
    }

    // ---- delivery box / keycards / door ----
    public static final RegistryObject<SoundEvent> BOX_CHARGE = reg("box_charge");         // rising electric hum + sparkle, 1.6s
    public static final RegistryObject<SoundEvent> BOX_OPEN = reg("box_open");             // latch clunk, steam hiss, magical shimmer chime, 1.8s
    public static final RegistryObject<SoundEvent> BOX_BURST = reg("box_burst");           // bright sparkling burst / glass-chime cascade, 1.2s
    public static final RegistryObject<SoundEvent> CARD_WHOOSH = reg("card_whoosh");       // fast airy swoosh with a rising pitch, 0.8s
    public static final RegistryObject<SoundEvent> KEYCARD_PICKUP = reg("keycard_pickup"); // two-tone bright ding + sparkle tail, 0.7s
    public static final RegistryObject<SoundEvent> DOOR_UNLOCK = reg("door_unlock");       // digital double beep + servo whirr + heavy lock clunk, 1.3s
    public static final RegistryObject<SoundEvent> DOOR_OPEN = reg("door_open");           // hydraulic pneumatic slide, 1.3s
    public static final RegistryObject<SoundEvent> DOOR_CLOSE = reg("door_close");         // hydraulic slide + thud, 1.1s
    public static final RegistryObject<SoundEvent> DOOR_DENIED = reg("door_denied");       // harsh low double buzzer, 0.8s

    // ---- PC setup ----
    public static final RegistryObject<SoundEvent> MONITOR_BOOT = reg("monitor_boot");     // CRT-ish power up thump, rising chirps, 2.2s
    public static final RegistryObject<SoundEvent> MONITOR_OFF = reg("monitor_off");       // power-down falling blip, 0.7s
    public static final RegistryObject<SoundEvent> KEY_CLICK = reg("key_click");           // mechanical keyboard clack, 0.12s
    public static final RegistryObject<SoundEvent> MOUSE_CLICK = reg("mouse_click");       // crisp mouse click, 0.1s
    public static final RegistryObject<SoundEvent> ORDER_CONFIRM = reg("order_confirm");   // digital cash-register / confirmation arpeggio, 1.2s
    public static final RegistryObject<SoundEvent> DELIVERY_DROP = reg("delivery_drop");   // drone whoosh then cardboard thud, 1.2s

    // ---- omega terminal ----
    public static final RegistryObject<SoundEvent> TERMINAL_INSERT = reg("terminal_insert");     // card slides in, beep, 1.0s
    public static final RegistryObject<SoundEvent> TERMINAL_ACTIVATE = reg("terminal_activate"); // massive power-surge swell, 3.5s
    public static final RegistryObject<SoundEvent> TERMINAL_HUM = reg("terminal_hum");           // low ominous electrical drone, 3.0s

    // ---- cutscene stingers ----
    public static final RegistryObject<SoundEvent> CUTSCENE_RISER = reg("cutscene_riser");   // 4.0s cinematic riser (noise sweep + rising tone)
    public static final RegistryObject<SoundEvent> CUTSCENE_BOOM = reg("cutscene_boom");     // 2.5s deep sub impact with reverb tail
    public static final RegistryObject<SoundEvent> CUTSCENE_GLITCH = reg("cutscene_glitch"); // 0.8s digital glitch/stutter burst

    // ---- boss 1: Cipher Warden (cyber / dark web / cyan + crimson) ----
    public static final RegistryObject<SoundEvent> BOSS1_THEME = reg("boss1_theme");       // MUSIC (stream). dark industrial/synthwave boss theme, A minor, ~128 BPM, >= 90s, seamless loop, STEREO ok
    public static final RegistryObject<SoundEvent> BOSS1_AMBIENT = reg("boss1_ambient");   // dormant hum, 4.0s
    public static final RegistryObject<SoundEvent> BOSS1_AWAKEN = reg("boss1_awaken");     // machine boot-up groan + glitch + rising roar, 3.5s
    public static final RegistryObject<SoundEvent> BOSS1_ROAR = reg("boss1_roar");         // distorted digital roar, 2.6s
    public static final RegistryObject<SoundEvent> BOSS1_HURT = reg("boss1_hurt");         // metallic glitchy hit, 0.6s
    public static final RegistryObject<SoundEvent> BOSS1_DEATH = reg("boss1_death");       // long descending glitch collapse + explosion, 4.5s
    public static final RegistryObject<SoundEvent> BOSS1_SLAM = reg("boss1_slam");         // heavy ground slam with shockwave, 1.3s
    public static final RegistryObject<SoundEvent> BOSS1_LASER = reg("boss1_laser");       // sustained charged laser beam, 2.2s
    public static final RegistryObject<SoundEvent> BOSS1_CHARGE = reg("boss1_charge");     // energy charge-up, 1.5s
    public static final RegistryObject<SoundEvent> BOSS1_TELEPORT = reg("boss1_teleport"); // glitch blink, 0.7s
    public static final RegistryObject<SoundEvent> BOSS1_SHOOT = reg("boss1_shoot");       // sharp data-packet zap, 0.4s
    public static final RegistryObject<SoundEvent> BOSS1_PHASE = reg("boss1_phase");       // phase transition shriek + shockwave, 2.6s
    public static final RegistryObject<SoundEvent> BOSS1_STEP = reg("boss1_step");         // heavy mechanical footstep, 0.6s

    // ---- boss 2: Omega Root (celestial admin god / gold + white + void violet) ----
    public static final RegistryObject<SoundEvent> BOSS2_THEME = reg("boss2_theme");       // MUSIC (stream). epic choir+orchestral-synth final boss theme, D minor, ~150 BPM, >= 100s, seamless loop, STEREO ok
    public static final RegistryObject<SoundEvent> BOSS2_AMBIENT = reg("boss2_ambient");   // ethereal drone, 4.0s
    public static final RegistryObject<SoundEvent> BOSS2_SUMMON = reg("boss2_summon");     // 6.0s reality-tearing rift opening, deep + choral
    public static final RegistryObject<SoundEvent> BOSS2_ROAR = reg("boss2_roar");         // layered godlike roar with choir, 3.2s
    public static final RegistryObject<SoundEvent> BOSS2_HURT = reg("boss2_hurt");         // resonant crystal-metal hit, 0.7s
    public static final RegistryObject<SoundEvent> BOSS2_DEATH = reg("boss2_death");       // 6.0s epic collapse: choir swell, glitch, supernova
    public static final RegistryObject<SoundEvent> BOSS2_SLAM = reg("boss2_slam");         // colossal slam, 1.6s
    public static final RegistryObject<SoundEvent> BOSS2_BEAM = reg("boss2_beam");         // huge sustained beam, 3.0s
    public static final RegistryObject<SoundEvent> BOSS2_CHARGE = reg("boss2_charge");     // charge-up swell, 2.0s
    public static final RegistryObject<SoundEvent> BOSS2_TELEPORT = reg("boss2_teleport"); // reality-skip blink, 0.8s
    public static final RegistryObject<SoundEvent> BOSS2_SHOOT = reg("boss2_shoot");       // bright energy bolt, 0.4s
    public static final RegistryObject<SoundEvent> BOSS2_NOVA = reg("boss2_nova");         // screen-filling nova detonation, 3.0s
    public static final RegistryObject<SoundEvent> BOSS2_PHASE = reg("boss2_phase");       // phase change: choir stab + shockwave, 3.0s
    public static final RegistryObject<SoundEvent> BOSS2_STEP = reg("boss2_step");         // colossal footstep, 0.7s

    // ---- iPhone Duo ----
    public static final RegistryObject<SoundEvent> PHONE_FOLD = reg("phone_fold");         // soft magnetic closing click, 0.35s
    public static final RegistryObject<SoundEvent> PHONE_UNFOLD = reg("phone_unfold");     // smooth hinge + magnetic release click, 0.5s
    public static final RegistryObject<SoundEvent> PHONE_UNLOCK = reg("phone_unlock");     // light two-note unlock chime, 0.4s
    public static final RegistryObject<SoundEvent> PHONE_TAP = reg("phone_tap");           // very soft UI tick, 0.06s
    public static final RegistryObject<SoundEvent> PHONE_BOOT = reg("phone_boot");         // warm boot-up chord (not a copy of any real chime), 2.0s
    public static final RegistryObject<SoundEvent> PHONE_SHUTTER = reg("phone_shutter");   // camera shutter, 0.2s
    public static final RegistryObject<SoundEvent> PHONE_NOTIFY = reg("phone_notify");     // gentle three-note notification, 0.8s
    public static final RegistryObject<SoundEvent> PHONE_TYPE = reg("phone_type");         // soft keyboard tick, 0.05s

    private ModSounds() {}
}
