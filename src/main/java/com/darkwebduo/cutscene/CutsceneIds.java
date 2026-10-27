package com.darkwebduo.cutscene;

/**
 * Cutscene ids, lengths (ticks) and the BEATS the server (boss code) must trigger at the same tick offsets
 * the client camera script (client/cutscene/CutsceneScripts) is choreographed for.
 *
 * Server side: call {@link CutsceneServer#start} once; then drive your own timeline with a counter that starts at 0
 * on the same tick. Boss must be invulnerable + non-targeting during a cutscene, all nearby players are frozen by the system.
 *
 * ============ CIPHER_INTRO (160 ticks) ============
 *   0   camera far & high, boss is a dormant statue (eyes dark)
 *   30  EYES: eyes flicker on (sound BOSS1_AMBIENT stops / BOSS1_AWAKEN starts at 20)
 *   60  RISE: ground cracks, boss rises/straightens, big shockwave ring + shake, cyan flash (client flash already scripted)
 *   110 ROAR: BOSS1_ROAR, burst of particles from the face, heavy shake (client already shakes)
 *   160 END: boss becomes active (music starts here), cutscene ends
 * ============ CIPHER_PHASE (55 ticks, arg = new phase 2 or 3) ============
 *   0 boss staggers, 20 BEAT: shockwave + flash, 55 END
 * ============ CIPHER_DEATH (150 ticks) ============
 *   0   collapse begins (glitching, sparks)   100 more violent glitching
 *   110 EXPLODE: huge explosion burst, flash   125 LOOT: emerald shower + omega delivery box drop, boss entity is removed
 *   150 END
 * ============ OMEGA_SUMMON (280 ticks) ============  (origin = terminal block pos, bossId = -1)
 *   0    keycard inserted, terminal energises
 *   30   SKY: darken, thunder, beams start      60 BEAMS: pillars of light from the sky onto the terminal
 *   120  RIFT: a huge portal tears open ~28 blocks above the terminal
 *   160  DESCEND: boss entity is spawned inside the rift and descends
 *   210  SLAM: boss lands, colossal shockwave
 *   235  ROAR: BOSS2_ROAR, shake
 *   280  END: boss active (music starts), players unfrozen
 * ============ OMEGA_PHASE (70 ticks, arg = new phase 2..5) ============
 *   0 stagger, 25 BEAT: shockwave + flash + arena changes, 70 END
 * ============ OMEGA_DEATH (280 ticks) ============
 *   0    collapse, glitch                        120 reality cracks, beams burst out
 *   200  EXPLODE: supernova, boss entity removed, flash
 *   215  PHONE: iPhone Duo block materialises on top of the terminal with a light pillar
 *   280  END
 */
public final class CutsceneIds {
    public static final int CIPHER_INTRO = 1;
    public static final int CIPHER_PHASE = 2;
    public static final int CIPHER_DEATH = 3;
    public static final int OMEGA_SUMMON = 4;
    public static final int OMEGA_PHASE = 5;
    public static final int OMEGA_DEATH = 6;

    /** Duration in ticks, indexed by id (index 0 unused). */
    public static final int[] DURATION = {0, 160, 55, 150, 280, 70, 280};

    // beat tick offsets
    public static final int CIPHER_INTRO_EYES = 30, CIPHER_INTRO_RISE = 60, CIPHER_INTRO_ROAR = 110;
    public static final int CIPHER_PHASE_BEAT = 20;
    public static final int CIPHER_DEATH_EXPLODE = 110, CIPHER_DEATH_LOOT = 125;
    public static final int OMEGA_SUMMON_SKY = 30, OMEGA_SUMMON_BEAMS = 60, OMEGA_SUMMON_RIFT = 120,
            OMEGA_SUMMON_DESCEND = 160, OMEGA_SUMMON_SLAM = 210, OMEGA_SUMMON_ROAR = 235;
    public static final int OMEGA_PHASE_BEAT = 25;
    public static final int OMEGA_DEATH_CRACK = 120, OMEGA_DEATH_EXPLODE = 200, OMEGA_DEATH_PHONE = 215;

    public static int duration(int id) {
        return id > 0 && id < DURATION.length ? DURATION[id] : 0;
    }

    private CutsceneIds() {}
}
