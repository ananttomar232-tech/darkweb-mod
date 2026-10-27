package com.darkwebduo.client.render;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.entity.BossBase;
import com.darkwebduo.entity.OmegaRootEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Omega Root model. Mesh is generated (OmegaRootParts, density 2); this class animates it with keyframe clips
 * (one per attack / cutscene) sampled with the server-synchronised attack time. The pose is a pure function of
 * (mode, attack, time) except for the continuously integrated ring / orbit spin phases.
 */
public class OmegaRootModel extends HierarchicalModel<OmegaRootEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(DarkWebDuoMod.MODID, "omega_root"), "main");
    public static final float MODEL_SCALE = 1.0F / OmegaRootParts.D;

    public static LayerDefinition createBodyLayer() {
        return OmegaRootParts.create();
    }

    // ------------------------------------------------------------------ pose columns
    private static final int TX = 0, TY = 1, TZ = 2, HX = 3, HY = 4, JAW = 5;
    private static final int RX = 6, RY = 7, RZ = 8, LX = 9, LY = 10, LZ = 11, FR = 12, FL = 13;
    private static final int WING = 14, RING = 15, BY = 16, SHAKE = 17, LEAN = 18, FLARE = 19;
    private static final int LOWX = 20, LOWZ = 21, CROWN = 22;
    private static final int N = 23;

    /** Keyframe track: rows of [time, v0..vN-1]; a column that is not set keeps the previous key's value. */
    private static final class Clip {
        final List<float[]> keys = new ArrayList<>();
        float[] cur = new float[N + 1];

        Clip key(float t) {
            cur = cur.clone();
            cur[0] = t;
            keys.add(cur);
            return this;
        }

        Clip set(int col, float v) {
            cur[col + 1] = v;
            return this;
        }

        Clip arms(float x, float y, float z) {
            return set(RX, x).set(LX, x).set(RY, y).set(LY, y).set(RZ, z).set(LZ, z);
        }

        Clip armR(float x, float y, float z) {
            return set(RX, x).set(RY, y).set(RZ, z);
        }

        Clip armL(float x, float y, float z) {
            return set(LX, x).set(LY, y).set(LZ, z);
        }

        Clip rest() {
            for (int i = 1; i <= N; i++) cur[i] = 0F;
            return this;
        }

        void sample(float t, float[] out) {
            int n = keys.size();
            if (n == 0) {
                for (int i = 0; i < N; i++) out[i] = 0F;
                return;
            }
            float[] first = keys.get(0);
            if (t <= first[0]) {
                System.arraycopy(first, 1, out, 0, N);
                return;
            }
            for (int k = 0; k < n - 1; k++) {
                float[] a = keys.get(k);
                float[] b = keys.get(k + 1);
                if (t >= a[0] && t < b[0]) {
                    float u = (t - a[0]) / Math.max(0.0001F, b[0] - a[0]);
                    u = u * u * (3F - 2F * u);
                    for (int i = 0; i < N; i++) out[i] = a[i + 1] + (b[i + 1] - a[i + 1]) * u;
                    return;
                }
            }
            System.arraycopy(keys.get(n - 1), 1, out, 0, N);
        }
    }

    private static final Clip[] ATTACKS = new Clip[19];
    private static final Clip IDLE = new Clip();
    private static final Clip SUMMON = new Clip();
    private static final Clip PHASE = new Clip();
    private static final Clip DEATH = new Clip();

    static {
        // 1 REND: R claw 16, L claw 32, crossing cut 48, crushing finisher 60
        ATTACKS[1] = new Clip().key(0).rest()
                .key(6).set(TY, -0.6F).armR(0.7F, 1.0F, 0.8F).armL(-0.3F, 0F, 0.2F).set(LOWX, -0.8F).set(TX, 0.1F).set(FR, -0.3F)
                .key(16).set(TY, 0.7F).armR(-1.55F, -0.9F, 0.2F).set(FR, -0.1F).set(SHAKE, 0.5F).set(LOWX, -1.4F)
                .key(20).set(TY, 0.5F)
                .key(22).set(TY, 0.7F).armR(-0.2F, 0F, 0.3F).armL(0.7F, 1.0F, 0.8F).set(FL, -0.3F)
                .key(32).set(TY, -0.7F).armL(-1.55F, -0.9F, 0.2F).set(FL, -0.1F).set(SHAKE, 0.5F)
                .key(38).set(TY, -0.2F).arms(0.5F, 0.6F, 0.6F)
                .key(48).set(TY, 0F).arms(-1.5F, 0.9F, 0.1F).set(LOWX, -1.6F).set(SHAKE, 0.8F)
                .key(54).arms(-3.0F, 0F, 0.2F).set(TX, -0.4F).set(LOWX, -2.6F)
                .key(60).arms(-0.4F, 0F, 0.15F).set(TX, 0.8F).set(BY, 3F).set(SHAKE, 2F).set(LOWX, -0.5F)
                .key(70).rest();
        // 2 FISSURE: four-fist slam at 28
        ATTACKS[2] = new Clip().key(0).rest()
                .key(10).arms(-3.0F, 0F, 0.5F).set(LOWX, -2.8F).set(TX, -0.35F).set(HX, -0.4F).set(RING, 1F).set(WING, 0.6F)
                .key(26).arms(-3.1F, 0F, 0.3F).set(TX, -0.5F).set(SHAKE, 0.5F).set(RING, 2F).set(FLARE, 1F)
                .key(29).arms(-0.3F, 0F, 0.2F).set(TX, 0.9F).set(HX, 0.3F).set(BY, 5F).set(SHAKE, 2.5F).set(LOWX, -0.4F)
                .key(40).set(TX, 0.6F).set(BY, 2F).set(SHAKE, 0.8F)
                .key(60).rest();
        // 3 SEEKER STORM: volleys 14..62
        Clip seek = new Clip().key(0).rest()
                .key(12).arms(-0.3F, 0F, 1.5F).set(LOWX, -0.4F).set(LOWZ, 0.9F).set(WING, 1F).set(RING, 2F).set(HX, -0.3F).set(CROWN, 1F);
        for (int k = 0; k < 7; k++) {
            float t = 14 + k * 8;
            seek.key(t).set(TX, 0.12F).set(SHAKE, 0.5F).key(t + 4).set(TX, -0.04F).set(SHAKE, 0.1F);
        }
        ATTACKS[3] = seek.key(90).rest();
        // 4 OMEGA BEAM: charge 0..50, fire 50..130
        ATTACKS[4] = new Clip().key(0).rest()
                .key(14).arms(-0.8F, 0.6F, 0.5F).set(FR, -1.0F).set(FL, -1.0F).set(LOWX, -0.6F).set(LOWZ, 0.6F).set(TX, -0.15F).set(WING, 1F).set(RING, 2F).set(HX, -0.2F).set(CROWN, 1F)
                .key(46).arms(-1.0F, 0.9F, 0.3F).set(FR, -1.4F).set(FL, -1.4F).set(RING, 5F).set(SHAKE, 0.6F).set(JAW, 0.8F).set(TX, -0.3F)
                .key(52).arms(-0.2F, 0.3F, 0.6F).set(FR, -0.2F).set(FL, -0.2F).set(TX, 0.12F).set(SHAKE, 1.8F).set(JAW, 1.1F).set(RING, 6F).set(BY, 1F)
                .key(130).set(SHAKE, 1.5F)
                .key(150).rest();
        // 5 TWIN LASER: charge 0..40, fire 40..110
        ATTACKS[5] = new Clip().key(0).rest()
                .key(12).arms(-0.4F, 0F, 1.2F).set(LOWX, -0.6F).set(LOWZ, 0.8F).set(HX, -0.5F).set(WING, 1F).set(RING, 2F)
                .key(38).set(HX, -0.7F).set(JAW, 1F).set(SHAKE, 0.6F).set(RING, 4F)
                .key(44).set(HX, 0.15F).set(JAW, 1.1F).set(SHAKE, 1.4F).set(RING, 5F)
                .key(108).set(SHAKE, 1.2F)
                .key(120).rest();
        // 6 REALITY SKIP: blinks at 10/40/70, strikes at +14
        Clip skip = new Clip().key(0).rest();
        for (int b = 10; b <= 70; b += 30) {
            skip.key(b - 2).set(TX, 0.3F).arms(0.4F, 0.3F, 0.5F).set(SHAKE, 2F).set(RING, 2F)
                    .key(b + 1).rest().set(SHAKE, 1.5F)
                    .key(b + 4).set(TY, -0.7F).armR(0.7F, 1.0F, 0.7F).set(RING, 2F).set(SHAKE, 0F)
                    .key(b + 11).set(TY, -0.7F)
                    .key(b + 14).set(TY, 0.7F).armR(-1.6F, -0.9F, 0.2F).set(LOWX, -1.4F).set(SHAKE, 0.8F)
                    .key(b + 20).set(TY, 0.4F)
                    .key(b + 26).rest();
        }
        ATTACKS[6] = skip.key(90).rest();
        // 7 METEORS
        ATTACKS[7] = new Clip().key(0).rest()
                .key(12).arms(-3.1F, 0F, 0.4F).set(LOWX, -2.9F).set(HX, -0.8F).set(JAW, 0.8F).set(WING, 1F).set(RING, 3F).set(CROWN, 1F).set(TX, -0.25F)
                .key(80).set(SHAKE, 0.7F)
                .key(110).rest();
        // 8 GRID: slam at 24, second wave 56
        ATTACKS[8] = new Clip().key(0).rest()
                .key(12).arms(-3.0F, 0F, 0.3F).set(TX, -0.4F).set(RING, 2F).set(LOWX, -2.6F)
                .key(24).arms(-0.4F, 0F, 0.2F).set(TX, 0.9F).set(BY, 5F).set(SHAKE, 2.4F).set(LOWX, -0.4F)
                .key(40).set(TX, 0.2F).arms(-0.3F, 0F, 1.0F).set(SHAKE, 0.5F).set(BY, 0F).set(RING, 1F)
                .key(52).arms(-3.0F, 0F, 0.4F).set(TX, -0.4F).set(LOWX, -2.6F)
                .key(58).arms(-0.4F, 0F, 0.2F).set(TX, 0.8F).set(SHAKE, 1.6F).set(BY, 4F)
                .key(70).set(TX, 0.2F).set(BY, 0F).set(SHAKE, 0.4F)
                .key(120).rest();
        // 9 BULLET FLOOR
        ATTACKS[9] = new Clip().key(0).rest()
                .key(12).arms(-0.3F, 0F, 1.7F).set(LOWX, -0.2F).set(LOWZ, 1.2F).set(HX, -0.8F).set(JAW, 0.7F).set(WING, 1F).set(RING, 3F).set(TX, -0.1F).set(CROWN, 1F).set(FLARE, 1F)
                .key(64).set(SHAKE, 0.8F)
                .key(76).set(SHAKE, 1.8F).set(RING, 5F)
                .key(100).rest();
        // 10 SINGULARITY
        ATTACKS[10] = new Clip().key(0).rest()
                .key(14).armR(-1.4F, -0.2F, 0.4F).set(FR, -0.2F).armL(0.5F, 0.2F, 0.9F).set(TY, -0.3F).set(RING, 2F).set(WING, 0.8F)
                .key(20).armR(-1.5F, -0.1F, 0.3F).set(SHAKE, 1.2F)
                .key(100).set(SHAKE, 0.8F)
                .key(112).armR(-0.2F, 0F, 0.2F).set(SHAKE, 1.6F).set(TY, 0.1F)
                .key(140).rest();
        // 11 LEGION
        ATTACKS[11] = new Clip().key(0).rest()
                .key(14).arms(-2.6F, 0F, 1.0F).set(LOWX, -2.2F).set(HX, -0.8F).set(JAW, 0.8F).set(WING, 1F).set(RING, 3F).set(CROWN, 1F)
                .key(20).set(SHAKE, 1F)
                .key(60).set(SHAKE, 0.5F)
                .key(80).rest();
        // 12 CLOCKWORK
        ATTACKS[12] = new Clip().key(0).rest()
                .key(10).arms(-1.4F, 0F, 1.5F).set(LOWX, -1.2F).set(LOWZ, 1.2F).set(RING, 4F).set(WING, 0.7F).set(HX, -0.2F)
                .key(42).set(SHAKE, 0.9F).set(RING, 5F)
                .key(120).set(SHAKE, 0.4F)
                .key(130).rest();
        // 13 COMET RAIN
        ATTACKS[13] = new Clip().key(0).rest()
                .key(10).armR(-3.0F, 0F, 0.3F).armL(-1.0F, 0F, 0.5F).set(LOWX, -2.0F).set(HX, -0.7F).set(RING, 3F).set(WING, 1F)
                .key(30).armL(-3.0F, 0F, 0.3F).armR(-1.0F, 0F, 0.5F)
                .key(50).armR(-3.0F, 0F, 0.3F).armL(-1.0F, 0F, 0.5F)
                .key(70).armL(-3.0F, 0F, 0.3F).armR(-1.0F, 0F, 0.5F)
                .key(90).rest();
        // 14 ROCKET CHARGE: wind-up 0..20, dash 20..40, brake 40
        ATTACKS[14] = new Clip().key(0).rest()
                .key(18).set(TX, -0.5F).arms(0.5F, 0F, 0.5F).set(LOWX, 0.4F).set(RING, 3F).set(SHAKE, 0.7F).set(HX, -0.2F)
                .key(21).set(TX, 1.0F).arms(0.9F, 0F, 0.3F).set(LOWX, 0.9F).set(RING, 5F).set(SHAKE, 1F).set(FLARE, -1F).set(HX, 0.5F)
                .key(40).set(TX, 1.0F)
                .key(44).set(TX, 0.5F).arms(-1.5F, 0F, 0.3F).set(SHAKE, 2F).set(BY, 2F).set(FLARE, 0F)
                .key(70).rest();
        // 15 JUDGMENT
        ATTACKS[15] = new Clip().key(0).rest()
                .key(12).arms(-3.0F, 0F, 0.6F).set(LOWX, -2.8F).set(HX, -0.6F).set(RING, 3F).set(WING, 1F).set(CROWN, 1F)
                .key(18).set(SHAKE, 1.2F)
                .key(60).set(SHAKE, 0.8F).arms(-0.4F, 0F, 1.6F)
                .key(120).rest();
        // 16 VOID HANDS
        ATTACKS[16] = new Clip().key(0).rest()
                .key(8).arms(-3.0F, 0F, 0.4F).set(LOWX, -2.6F).set(RING, 2F)
                .key(14).arms(-0.9F, 0F, 0.3F).set(TX, 0.4F).set(SHAKE, 1.2F)
                .key(30).arms(-3.0F, 0F, 0.4F).set(TX, -0.2F)
                .key(40).arms(-0.9F, 0F, 0.3F).set(TX, 0.5F).set(SHAKE, 1.6F).set(BY, 2F)
                .key(60).set(TX, 0.1F).arms(-1.4F, 0F, 0.8F).set(BY, 0F)
                .key(100).rest();
        // 17 PRISM BURST
        ATTACKS[17] = new Clip().key(0).rest()
                .key(12).arms(-0.3F, 0F, 1.5F).set(LOWX, -0.4F).set(LOWZ, 1.2F).set(HX, -0.4F).set(WING, 1F).set(RING, 3F).set(CROWN, 1F)
                .key(30).set(TY, 0.6F)
                .key(50).set(TY, -0.6F)
                .key(70).set(TY, 0.6F)
                .key(86).set(TY, 0F).set(SHAKE, 0.5F)
                .key(100).rest();
        // 18 OMEGA NOVA: rises 0..40, charges to 134, slam 136
        ATTACKS[18] = new Clip().key(0).rest()
                .key(20).arms(-0.2F, 0F, 1.7F).set(LOWX, -0.2F).set(LOWZ, 1.3F).set(HX, -0.4F).set(WING, 1F).set(RING, 3F).set(CROWN, 1F).set(FLARE, 1F)
                .key(60).arms(-0.1F, 0F, 2.2F).set(HX, -0.8F).set(JAW, 1F).set(RING, 6F).set(SHAKE, 0.5F)
                .key(120).set(SHAKE, 1.4F).set(RING, 9F).arms(-0.1F, 0F, 2.5F).set(HX, -0.9F)
                .key(134).arms(-1.8F, 0F, 0.3F).set(TX, 0.4F).set(LOWX, -1.8F).set(SHAKE, 2F)
                .key(138).arms(-0.4F, 0F, 0.2F).set(TX, 1.0F).set(BY, 6F).set(SHAKE, 3F).set(RING, 2F)
                .key(150).set(TX, 0.5F).set(BY, 2F)
                .key(160).rest();

        IDLE.key(0).rest();
        // summon cutscene, sampled at t = 160 + modeTime: descends spread-eagled, lands at 210, roars at 235
        SUMMON.key(160).arms(-0.3F, 0F, 1.9F).set(LOWX, -0.2F).set(LOWZ, 1.0F).set(WING, 1F).set(RING, 6F).set(HX, -0.4F).set(JAW, 0.3F).set(CROWN, 1F).set(FLARE, 1F).set(SHAKE, 0.4F)
                .key(205).set(RING, 8F).set(SHAKE, 0.8F)
                .key(209).arms(-1.0F, 0F, 0.8F).set(TX, 0.1F).set(FLARE, 1F)
                .key(212).arms(-0.3F, 0F, 0.2F).set(TX, 0.9F).set(HX, 0.3F).set(BY, 6F).set(SHAKE, 3F).set(RING, 3F).set(LOWX, -0.4F).set(LOWZ, 0F).set(WING, 0.5F).set(FLARE, 0F)
                .key(225).set(TX, 0.5F).set(BY, 2F).set(SHAKE, 1F)
                .key(234).set(TX, -0.1F).set(BY, 0F).set(HX, -0.2F).arms(-0.4F, 0F, 0.9F).set(RING, 3F)
                .key(240).arms(-0.2F, 0F, 1.7F).set(TX, -0.45F).set(HX, -0.95F).set(JAW, 1.2F).set(WING, 1F).set(CROWN, 1F).set(LOWZ, 1.2F).set(RING, 7F).set(SHAKE, 1.6F)
                .key(262).set(SHAKE, 0.8F).set(RING, 4F)
                .key(280).rest();
        // phase transition (70): stagger, power-up at 26
        PHASE.key(0).rest()
                .key(8).set(TX, 0.5F).set(HX, 0.4F).arms(0.4F, 0F, 0.4F).set(SHAKE, 1.5F)
                .key(22).set(TX, 0.6F).set(SHAKE, 2.5F).set(RING, 6F)
                .key(26).arms(-0.3F, 0F, 2.0F).set(LOWZ, 1.4F).set(TX, -0.5F).set(HX, -0.9F).set(JAW, 1.1F).set(WING, 1F).set(CROWN, 1F).set(FLARE, 1F).set(SHAKE, 2F)
                .key(50).set(SHAKE, 1F)
                .key(70).rest();
        // death (280): collapse, reality cracks 120, supernova 200
        DEATH.key(0).rest().set(SHAKE, 0.5F)
                .key(30).set(TX, 0.2F).set(HX, 0.3F).arms(0.2F, 0F, 0.5F).set(SHAKE, 1F).set(RING, 2F)
                .key(80).set(TX, 0.4F).arms(0.5F, 0F, 0.2F).set(LOWX, 0.4F).set(HX, 0.6F).set(JAW, 0.6F).set(SHAKE, 1.6F).set(RING, 4F).set(WING, 0.3F)
                .key(120).set(TX, 0.6F).set(HX, 0.8F).arms(0.7F, 0F, 0.1F).set(SHAKE, 2.5F).set(RING, 7F).set(BY, 2F)
                .key(160).set(SHAKE, 3.5F).arms(-0.5F, 0F, 1.8F).set(LOWZ, 1.0F).set(WING, 1F)
                .key(198).set(SHAKE, 4F).set(RING, 12F).set(HX, -0.9F).set(JAW, 1.2F).arms(-0.3F, 0F, 2.4F).set(TX, -0.3F)
                .key(280);
    }

    // ------------------------------------------------------------------ parts
    private final ModelPart root;
    private final ModelPart body, pelvis, torso, neck, head, jaw, orbit;
    private final ModelPart armL, armR, foreL, foreR, larmL, larmR, lforeL, lforeR;
    private final ModelPart wingL, wingR;
    private final ModelPart[] wingFeathersL = new ModelPart[3];
    private final ModelPart[] wingFeathersR = new ModelPart[3];
    private final ModelPart[] crown = new ModelPart[7];
    private final ModelPart[] tnA = new ModelPart[12];
    private final ModelPart[] tnB = new ModelPart[12];
    private final ModelPart[] ringTilt = new ModelPart[3];
    private final ModelPart[] ringSpin = new ModelPart[3];
    private final float[] ringPhase = new float[3];
    private float orbitPhase;
    private float lastAge = -1F;
    private final float[] pose = new float[N];

    /** set by the renderer right before rendering (needed for smooth animation). */
    public float partial = 0F;

    public OmegaRootModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.pelvis = body.getChild("pelvis");
        this.torso = pelvis.getChild("torso");
        this.neck = torso.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.orbit = body.getChild("orbit");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("forearm_l");
        this.foreR = armR.getChild("forearm_r");
        this.larmL = torso.getChild("larm_l");
        this.larmR = torso.getChild("larm_r");
        this.lforeL = larmL.getChild("lfore_l");
        this.lforeR = larmR.getChild("lfore_r");
        this.wingL = torso.getChild("wing_l");
        this.wingR = torso.getChild("wing_r");
        for (int i = 0; i < 3; i++) {
            wingFeathersL[i] = wingL.getChild("wing_l" + i);
            wingFeathersR[i] = wingR.getChild("wing_r" + i);
        }
        ModelPart cr = head.getChild("crown");
        for (int i = 0; i < 7; i++) crown[i] = cr.getChild("cr_" + i);
        for (int i = 0; i < 12; i++) {
            ModelPart h = pelvis.getChild("tn_" + i);
            tnA[i] = h.getChild("tn_" + i + "_a");
            tnB[i] = tnA[i].getChild("tn_" + i + "_b");
        }
        ModelPart gyro = body.getChild("gyro");
        String[] keys = {"a", "b", "c"};
        for (int i = 0; i < 3; i++) {
            ringTilt[i] = gyro.getChild("g" + keys[i] + "_t");
            ringSpin[i] = ringTilt[i].getChild("g" + keys[i]);
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float px(float v) {
        return v * OmegaRootParts.D;
    }

    @Override
    public void setupAnim(OmegaRootEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        int mode = e.getMode();
        for (int i = 0; i < N; i++) pose[i] = 0F;
        float lsa = Mth.clamp(limbSwingAmount, 0F, 1F);
        boolean gliding = false;
        float aimTorso = 0F;
        if (mode == BossBase.MODE_CUTSCENE) {
            float t = e.modeTime(partial);
            if (e.getCutsceneId() == CutsceneIds.OMEGA_SUMMON) SUMMON.sample(160F + t, pose);
            else PHASE.sample(t, pose);
        } else if (mode == BossBase.MODE_DYING) {
            DEATH.sample(e.modeTime(partial), pose);
        } else if (mode == BossBase.MODE_FIGHT) {
            int atk = e.getAttack();
            if (atk > 0 && atk < ATTACKS.length && ATTACKS[atk] != null) {
                float at = e.attackTime(partial);
                ATTACKS[atk].sample(at, pose);
                if ((atk == OmegaRootEntity.A_BEAM && at > 8F && at < 138F) || (atk == OmegaRootEntity.A_TWIN && at > 6F && at < 118F)) {
                    aimTorso = Mth.clamp(Mth.wrapDegrees(e.getAimYaw() - e.yBodyRot) * Mth.DEG_TO_RAD * 0.85F, -1.6F, 1.6F);
                }
            } else {
                IDLE.sample(0F, pose);
                gliding = true;
            }
        } else {
            IDLE.sample(0F, pose);
        }

        float shake = pose[SHAKE];

        // ---- integrate ring / shard spin so changing the speed never makes them jump
        float dt = lastAge < 0F || age < lastAge || age - lastAge > 6F ? 0F : age - lastAge;
        lastAge = age;
        float boost = 1F + pose[RING] * 0.9F;
        ringPhase[0] += dt * 0.021F * boost;
        ringPhase[1] -= dt * 0.015F * boost;
        ringPhase[2] += dt * 0.027F * boost;
        orbitPhase += dt * 0.022F * (1F + pose[RING] * 0.5F);

        // ---- hover body
        float breathe = Mth.sin(age * 0.09F);
        float bob = Mth.sin(age * 0.07F) * 1.8F + (gliding ? lsa * -1.0F : 0F);
        body.y += px(pose[BY] + bob);
        body.z += px(-pose[LEAN]);
        body.x += shake * px(0.35F) * Mth.sin(age * 2.9F);
        body.y += shake * px(0.25F) * Mth.sin(age * 3.7F + 1F);

        // ---- torso / head
        float lean = pose[TX] + (gliding ? 0.16F * lsa : 0F);
        pelvis.xRot = lean * 0.3F;
        torso.xRot = lean * 0.7F + 0.02F + breathe * 0.012F + shake * 0.04F * Mth.sin(age * 2.3F);
        torso.yRot = pose[TY] + aimTorso;
        torso.zRot = pose[TZ] + shake * 0.035F * Mth.sin(age * 3.1F);
        float hy = Mth.clamp(netHeadYaw, -60F, 60F) * Mth.DEG_TO_RAD * 0.55F;
        float hp = Mth.clamp(headPitch, -40F, 40F) * Mth.DEG_TO_RAD * 0.5F;
        boolean fixedHead = mode != BossBase.MODE_FIGHT || aimTorso != 0F;
        head.xRot = pose[HX] - lean * 0.75F + (fixedHead ? 0F : hp) + shake * 0.05F * Mth.sin(age * 4.1F);
        head.yRot = pose[HY] - (pose[TY] + aimTorso) * 0.6F + (fixedHead ? 0F : hy);
        head.y += px(Mth.sin(age * 0.06F + 0.8F) * 1.2F);
        jaw.xRot = pose[JAW] + 0.04F + breathe * 0.02F;
        for (int k = 0; k < 7; k++) {
            crown[k].zRot = (k - 3) * 0.30F * (1F + pose[CROWN] * 0.9F) + Mth.sin(age * 0.1F + k) * 0.02F;
        }

        // ---- upper arms
        float idleSway = Mth.sin(age * 0.08F) * 0.03F;
        armR.xRot = pose[RX] - 0.06F + shake * 0.07F * Mth.sin(age * 2.7F);
        armL.xRot = pose[LX] - 0.06F + shake * 0.07F * Mth.sin(age * 2.5F + 1F);
        armR.yRot = pose[RY];
        armL.yRot = -pose[LY];
        armR.zRot = pose[RZ] + 0.12F + idleSway;
        armL.zRot = -pose[LZ] - 0.12F - idleSway;
        foreR.xRot = pose[FR] - 0.22F;
        foreL.xRot = pose[FL] - 0.22F;

        // ---- lower (thin, blade) arms
        float lowSway = Mth.sin(age * 0.05F) * 0.04F;
        larmR.xRot = pose[LOWX] + lowSway;
        larmL.xRot = pose[LOWX] - lowSway;
        larmR.zRot = 0.35F + pose[LOWZ];
        larmL.zRot = -(0.35F + pose[LOWZ]);
        lforeR.xRot = pose[LOWX] * 0.35F - 0.25F;
        lforeL.xRot = pose[LOWX] * 0.35F - 0.25F;

        // ---- crystal wings
        float wf = pose[WING];
        float flap = Mth.sin(age * 0.12F) * 0.05F;
        wingL.yRot += wf * 0.5F + flap;
        wingR.yRot -= wf * 0.5F + flap;
        wingL.xRot -= wf * 0.25F;
        wingR.xRot -= wf * 0.25F;
        for (int i = 0; i < 3; i++) {
            float f = wf * 0.32F * (1F + i * 0.35F) + flap * (1F + i * 0.4F);
            wingFeathersL[i].zRot += f;
            wingFeathersR[i].zRot -= f;
        }

        // ---- gyroscope rings + orbiting crystals
        for (int i = 0; i < 3; i++) {
            ringSpin[i].zRot = ringPhase[i];
        }
        ringTilt[0].xRot += Mth.sin(age * 0.031F) * 0.09F;
        ringTilt[0].zRot += Mth.cos(age * 0.027F) * 0.07F;
        ringTilt[1].yRot += Mth.sin(age * 0.023F) * 0.12F;
        ringTilt[1].xRot += Mth.cos(age * 0.019F) * 0.06F;
        ringTilt[2].xRot += Mth.sin(age * 0.017F + 1F) * 0.14F;
        ringTilt[2].yRot += age * 0.004F * boost;
        orbit.yRot = orbitPhase;
        orbit.xRot = Mth.sin(age * 0.02F) * 0.1F;

        // ---- tendril skirt: flares with energy, trails while gliding, ripples always
        float flare = pose[FLARE];
        float trail = gliding ? lsa * 0.35F : 0F;
        for (int i = 0; i < 12; i++) {
            float ph = i * 0.9F;
            tnA[i].xRot += flare * 0.55F + trail + Mth.sin(age * 0.09F - ph) * 0.1F + shake * 0.03F * Mth.sin(age * 1.7F + i);
            tnA[i].zRot += Mth.sin(age * 0.08F + i * 1.3F) * 0.08F;
            tnB[i].xRot += flare * 0.4F + trail * 0.7F + Mth.sin(age * 0.11F - ph + 1F) * 0.15F;
            tnB[i].zRot += Mth.sin(age * 0.1F + i * 0.7F) * 0.07F;
        }
    }
}
