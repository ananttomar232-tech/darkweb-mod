package com.darkwebduo.client.render;

import com.darkwebduo.DarkWebDuoMod;
import com.darkwebduo.cutscene.CutsceneIds;
import com.darkwebduo.entity.BossBase;
import com.darkwebduo.entity.CipherWardenEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Cipher Warden model. The mesh itself is generated (CipherWardenParts, density 2); this class animates it with
 * keyframe "clips" (one per attack / cutscene) sampled with the attack-time that is synchronised from the server.
 * No per-entity state is kept here: the pose is a pure function of (mode, attack, time, limb swing).
 */
public class CipherWardenModel extends HierarchicalModel<CipherWardenEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(DarkWebDuoMod.MODID, "cipher_warden"), "main");
    /** the model is authored at density D; the renderer scales it back down. */
    public static final float MODEL_SCALE = 1.0F / CipherWardenParts.D;

    public static LayerDefinition createBodyLayer() {
        return CipherWardenParts.create();
    }

    // ------------------------------------------------------------------ pose columns
    private static final int TX = 0, TY = 1, TZ = 2, HX = 3, HY = 4, JAW = 5;
    private static final int RX = 6, RY = 7, RZ = 8, LX = 9, LY = 10, LZ = 11, FR = 12, FL = 13;
    private static final int CROUCH = 14, SPREAD = 15, WING = 16, HALO = 17, BY = 18, SHAKE = 19, LEAN = 20;
    private static final int N = 21;

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

    private static final Clip[] ATTACKS = new Clip[16];
    private static final Clip DORMANT = new Clip();
    private static final Clip INTRO = new Clip();
    private static final Clip PHASE = new Clip();
    private static final Clip DEATH = new Clip();

    static {
        // 1 SLAM
        ATTACKS[1] = new Clip().key(0).rest()
                .key(10).set(CROUCH, 0.35F).set(TX, -0.25F).arms(-2.3F, 0F, 0.25F).set(FR, -0.5F).set(FL, -0.5F).set(WING, 0.5F).set(HALO, 0.6F)
                .key(22).set(CROUCH, 0.45F).set(TX, -0.5F).arms(-3.1F, 0F, 0.15F).set(FR, -0.2F).set(FL, -0.2F).set(HX, -0.2F).set(JAW, 0.5F).set(HALO, 1F).set(SHAKE, 0.3F)
                .key(25).set(CROUCH, 0.6F).set(TX, 0.7F).arms(-0.35F, 0.1F, 0.15F).set(FR, -0.4F).set(FL, -0.4F).set(HX, 0.3F).set(JAW, 0.2F).set(BY, 3F).set(SHAKE, 1.5F)
                .key(32).set(CROUCH, 0.5F).set(TX, 0.5F).set(BY, 1F).set(SHAKE, 0.5F)
                .key(44).rest();
        // 2 COMBO (claw swings at 14 / 30 / 48)
        ATTACKS[2] = new Clip().key(0).rest()
                .key(8).set(TY, -0.7F).armR(0.9F, 1.1F, 0.7F).armL(-0.4F, 0F, 0.2F).set(CROUCH, 0.25F).set(FR, -0.3F)
                .key(14).set(TY, 0.7F).armR(-1.55F, -0.9F, 0.2F).set(FR, -0.1F).set(CROUCH, 0.3F).set(SHAKE, 0.4F)
                .key(18).set(TY, 0.5F)
                .key(24).set(TY, 0.7F).armR(-0.2F, 0F, 0.3F).armL(0.9F, 1.1F, 0.7F).set(FL, -0.3F)
                .key(30).set(TY, -0.7F).armL(-1.55F, -0.9F, 0.2F).set(FL, -0.1F).set(SHAKE, 0.4F)
                .key(36).set(TY, -0.4F)
                .key(42).set(TY, 0F).arms(-2.9F, 0F, 0.2F).set(TX, -0.4F).set(CROUCH, 0.4F).set(FR, -0.2F).set(FL, -0.2F)
                .key(48).arms(-0.6F, 0F, 0.1F).set(TX, 0.6F).set(CROUCH, 0.5F).set(BY, 2F).set(SHAKE, 1.2F)
                .key(62).rest();
        // 3 BARRAGE (volleys at 14,24,34,44,54)
        Clip bar = new Clip().key(0).rest()
                .key(10).arms(-0.6F, 0F, 1.3F).set(FR, -0.2F).set(FL, -0.2F).set(WING, 1F).set(HALO, 1F).set(HX, -0.2F);
        for (int k = 0; k < 5; k++) {
            float t = 14 + k * 10;
            bar.key(t).set(TX, 0.14F).set(SHAKE, 0.6F).key(t + 4).set(TX, -0.06F).set(SHAKE, 0.1F);
        }
        ATTACKS[3] = bar.key(72).rest();
        // 4 LASER (charge 0-42, beam 42-102)
        ATTACKS[4] = new Clip().key(0).rest()
                .key(14).set(CROUCH, 0.3F).set(TX, -0.2F).arms(-0.4F, 0F, 1.0F).set(HX, -0.5F).set(JAW, 0.5F).set(WING, 1F).set(HALO, 0.6F)
                .key(40).set(CROUCH, 0.5F).set(TX, -0.35F).arms(-0.2F, 0F, 1.2F).set(HX, -0.8F).set(JAW, 1F).set(HALO, 1F).set(SHAKE, 0.6F).set(SPREAD, 0.8F)
                .key(46).set(HX, 0.1F).set(JAW, 1.1F).set(TX, 0.1F).set(CROUCH, 0.55F).set(SHAKE, 1.2F)
                .key(100).set(SHAKE, 1.0F)
                .key(112).rest();
        // 5 BLINK (teleport at 10, strike at 26)
        ATTACKS[5] = new Clip().key(0).rest()
                .key(8).set(CROUCH, 0.6F).set(TX, 0.3F).set(HX, 0.2F).arms(0.4F, 0.3F, 0.5F).set(SHAKE, 2F)
                .key(12).rest().set(SHAKE, 1.5F)
                .key(14).set(TY, -0.7F).armR(0.9F, 1.0F, 0.6F).set(SHAKE, 0F)
                .key(22).set(TY, -0.7F)
                .key(26).set(TY, 0.7F).armR(-1.6F, -0.9F, 0.2F).set(SHAKE, 0.5F)
                .key(32).set(TY, 0.5F)
                .key(44).rest();
        // 6 RAIN
        ATTACKS[6] = new Clip().key(0).rest()
                .key(12).arms(-3.0F, 0F, 0.5F).set(TX, -0.3F).set(HX, -0.7F).set(JAW, 0.7F).set(WING, 1F).set(HALO, 1F).set(CROUCH, 0.2F)
                .key(70).set(SHAKE, 0.5F)
                .key(84).rest();
        // 7 FIREWALL (ground punch at 22)
        ATTACKS[7] = new Clip().key(0).rest()
                .key(12).arms(-3.0F, 0F, 0.3F).set(TX, -0.4F).set(CROUCH, 0.3F).set(HALO, 0.8F)
                .key(22).arms(-0.4F, 0F, 0.2F).set(TX, 0.9F).set(CROUCH, 0.7F).set(BY, 3F).set(SHAKE, 1.5F)
                .key(34).set(SHAKE, 0.4F)
                .key(70).set(CROUCH, 0.6F).set(TX, 0.8F).set(HX, 0.3F).set(SHAKE, 0.3F)
                .key(96).rest();
        // 8 SAFE TILE (release at 80)
        ATTACKS[8] = new Clip().key(0).rest()
                .key(16).arms(-0.9F, -1.1F, 0F).set(FR, -2.0F).set(FL, -2.0F).set(TX, 0.25F).set(HALO, 1F).set(WING, 0.5F).set(SHAKE, 0.4F).set(CROUCH, 0.2F)
                .key(70).set(SHAKE, 1F).set(CROUCH, 0.35F)
                .key(80).arms(-0.3F, 0F, 1.6F).set(FR, -0.2F).set(FL, -0.2F).set(TX, -0.5F).set(HX, -0.7F).set(JAW, 1F).set(WING, 1F).set(SHAKE, 1.5F).set(CROUCH, 0F)
                .key(90).set(SHAKE, 0.5F)
                .key(100).rest();
        // 9 VORTEX
        ATTACKS[9] = new Clip().key(0).rest()
                .key(14).armR(-1.4F, -0.2F, 0.4F).set(FR, -0.2F).armL(0.5F, 0.2F, 0.7F).set(TY, -0.35F).set(TX, 0.1F).set(HALO, 0.8F)
                .key(100).set(SHAKE, 0.6F)
                .key(108).armR(-0.2F, 0F, 0.2F).set(SHAKE, 1.2F)
                .key(120).rest();
        // 10 DAEMONS
        ATTACKS[10] = new Clip().key(0).rest()
                .key(14).arms(-2.6F, 0F, 1.0F).set(HX, -0.8F).set(JAW, 0.8F).set(WING, 1F).set(HALO, 1F).set(CROUCH, 0.15F)
                .key(56).set(SHAKE, 0.6F)
                .key(70).rest();
        // 11 NOVA (rise 0-40, charge 40-100, slam 100)
        ATTACKS[11] = new Clip().key(0).rest()
                .key(20).arms(-0.2F, 0F, 1.6F).set(FR, -0.2F).set(FL, -0.2F).set(TX, -0.1F).set(CROUCH, 0.15F).set(HX, -0.4F).set(WING, 1F).set(HALO, 1F)
                .key(40).arms(-0.1F, 0F, 2.0F).set(HX, -0.7F).set(JAW, 1F).set(SHAKE, 0.4F)
                .key(94).arms(-0.1F, 0F, 2.3F).set(SHAKE, 1.2F).set(HX, -0.9F)
                .key(100).arms(-1.0F, 0F, 0.1F).set(TX, 0.9F).set(CROUCH, 0.8F).set(BY, 4F).set(HX, 0.2F).set(SHAKE, 2F)
                .key(112).set(CROUCH, 0.6F).set(TX, 0.6F).set(SHAKE, 0.4F)
                .key(140).rest();
        // 12 SPIRAL
        ATTACKS[12] = new Clip().key(0).rest()
                .key(12).arms(-3.0F, 0F, 0.4F).set(TX, -0.2F).set(HX, -0.5F).set(WING, 1F).set(HALO, 1F)
                .key(30).set(TY, 0.6F)
                .key(50).set(TY, -0.6F)
                .key(70).set(TY, 0.6F)
                .key(84).set(TY, 0F).set(SHAKE, 0.4F)
                .key(96).rest();
        // 13 LEAP (launch 12, slam 34)
        ATTACKS[13] = new Clip().key(0).rest()
                .key(12).set(CROUCH, 1F).set(TX, 0.5F).arms(0.8F, 0F, 0.2F).set(SHAKE, 0.8F)
                .key(16).set(CROUCH, 0.1F).set(TX, -0.2F).arms(-2.6F, 0F, 0.5F)
                .key(30).set(CROUCH, 0.4F).set(TX, 0.5F).arms(-2.0F, 0F, 0.4F)
                .key(34).set(CROUCH, 0.7F).set(TX, 0.8F).arms(-0.5F, 0F, 0.1F).set(BY, 2F).set(SHAKE, 1.5F)
                .key(56).rest();

        // statue: crouched, head bowed, arms hanging
        DORMANT.key(0).set(CROUCH, 1F).set(TX, 0.55F).set(HX, 0.75F).arms(0.45F, 0F, 0.2F).set(FR, -0.35F).set(FL, -0.35F);
        // intro cutscene (160)
        INTRO.key(0).set(CROUCH, 1F).set(TX, 0.55F).set(HX, 0.75F).arms(0.45F, 0F, 0.2F).set(FR, -0.35F).set(FL, -0.35F)
                .key(58)
                .key(62).set(SHAKE, 2.5F).set(HALO, 0.4F)
                .key(85).set(CROUCH, 0.5F).set(TX, 0.35F).set(HX, 0.3F).arms(0.2F, 0F, 0.2F).set(SHAKE, 1.5F).set(HALO, 0.6F)
                .key(100).rest().set(HALO, 0.8F)
                .key(108).set(TX, -0.35F).set(HX, -0.5F).set(HALO, 1F)
                .key(112).set(JAW, 1.1F).set(HX, -0.8F).set(TX, -0.4F).arms(-0.3F, 0F, 1.2F).set(FR, -0.3F).set(FL, -0.3F).set(WING, 1F).set(SHAKE, 1.2F)
                .key(148)
                .key(160).rest();
        // phase transition (55)
        PHASE.key(0).rest()
                .key(6).set(CROUCH, 0.7F).set(TX, 0.6F).set(HX, 0.5F).arms(0.4F, 0F, 0.4F).set(SHAKE, 1.5F).set(JAW, 0.3F)
                .key(18).set(CROUCH, 0.8F).set(TX, 0.7F).set(SHAKE, 2F).set(HALO, 1F)
                .key(22).arms(-0.4F, 0F, 1.7F).set(TX, -0.6F).set(HX, -0.9F).set(JAW, 1.1F).set(WING, 1F).set(CROUCH, 0.2F).set(SHAKE, 1.5F)
                .key(40)
                .key(55).rest();
        // death (150): collapse
        DEATH.key(0).rest().set(SHAKE, 0.5F)
                .key(20).set(CROUCH, 0.4F).set(TX, 0.3F).set(HX, 0.3F).arms(0.2F, 0F, 0.4F).set(SHAKE, 0.8F).set(WING, 0.4F)
                .key(60).set(CROUCH, 0.9F).set(TX, 0.6F).set(HX, 0.6F).arms(0.3F, 0F, 0.2F).set(FR, -0.2F).set(FL, -0.2F).set(JAW, 0.6F).set(SHAKE, 1.2F).set(HALO, 0F)
                .key(100).set(CROUCH, 1.15F).set(TX, 0.9F).set(HX, 0.8F).arms(0.5F, 0F, 0.1F).set(SHAKE, 2F)
                .key(125).set(SHAKE, 3.5F)
                .key(150);
    }

    // ------------------------------------------------------------------ parts
    private final ModelPart root;
    private final ModelPart body, hip, torso, neck, head, jaw, halo, halo2;
    private final ModelPart wingL, wingR, armL, armR, foreL, foreR;
    private final ModelPart thighL, thighR, shinL, shinR, footL, footR;
    private final ModelPart[] tail = new ModelPart[6];
    private final float[] pose = new float[N];

    /** set by the renderer right before rendering (needed for smooth animation). */
    public float partial = 0F;

    public CipherWardenModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.hip = body.getChild("hip");
        this.torso = hip.getChild("torso");
        this.neck = torso.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.halo = neck.getChild("halo");
        this.halo2 = neck.getChild("halo2");
        this.wingL = torso.getChild("wing_l");
        this.wingR = torso.getChild("wing_r");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("forearm_l");
        this.foreR = armR.getChild("forearm_r");
        this.thighL = hip.getChild("thigh_l");
        this.thighR = hip.getChild("thigh_r");
        this.shinL = thighL.getChild("shin_l");
        this.shinR = thighR.getChild("shin_r");
        this.footL = shinL.getChild("foot_l");
        this.footR = shinR.getChild("foot_r");
        ModelPart t = hip.getChild("tail_0");
        tail[0] = t;
        for (int i = 1; i < 6; i++) {
            t = t.getChild("tail_" + i);
            tail[i] = t;
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float px(float v) {
        return v * CipherWardenParts.D;
    }

    @Override
    public void setupAnim(CipherWardenEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // ---- choose the clip
        int mode = e.getMode();
        for (int i = 0; i < N; i++) pose[i] = 0F;
        float lsa = Mth.clamp(limbSwingAmount, 0F, 1F);
        boolean walking = false;
        if (mode == BossBase.MODE_DORMANT) {
            DORMANT.sample(0F, pose);
        } else if (mode == BossBase.MODE_CUTSCENE) {
            float t = e.modeTime(partial);
            if (e.getCutsceneId() == CutsceneIds.CIPHER_INTRO) INTRO.sample(t, pose);
            else PHASE.sample(t, pose);
        } else if (mode == BossBase.MODE_DYING) {
            DEATH.sample(e.modeTime(partial), pose);
        } else {
            int atk = e.getAttack();
            if (atk > 0 && atk < ATTACKS.length && ATTACKS[atk] != null) {
                ATTACKS[atk].sample(e.attackTime(partial), pose);
            } else {
                walking = true;
            }
        }

        float shake = pose[SHAKE];
        float crouch = pose[CROUCH];

        // ---- whole body: crouch geometry keeps the feet planted
        float a = 1.3F * crouch;
        float b = 1.8F * crouch;
        float drop = 40F - (22F * Mth.cos(a) + 18F * Mth.cos(a - b));
        float fwd = 22F * Mth.sin(a) + 18F * Mth.sin(a - b);
        float bob = walking ? Math.abs(Mth.sin(limbSwing * 0.5F)) * 1.4F * lsa : 0F;
        float breathe = Mth.sin(age * 0.09F);
        body.y += px(drop + pose[BY] + bob);
        body.z += px(fwd - pose[LEAN]);
        body.x += shake * px(0.35F) * Mth.sin(age * 2.9F);
        body.y += shake * px(0.25F) * Mth.sin(age * 3.7F + 1F);

        // ---- legs
        float swing = walking ? Mth.cos(limbSwing * 0.5F) * 0.85F * lsa : 0F;
        float spread = 0.12F + pose[SPREAD] * 0.3F;
        thighR.xRot = -a + swing;
        thighL.xRot = -a - swing;
        thighR.zRot = spread;
        thighL.zRot = -spread;
        shinR.xRot = b + Math.max(0F, -swing) * 1.3F;
        shinL.xRot = b + Math.max(0F, swing) * 1.3F;
        footR.xRot = (a - b) - Math.max(0F, -swing) * 1.3F - swing * 0.1F;
        footL.xRot = (a - b) - Math.max(0F, swing) * 1.3F + swing * 0.1F;
        footR.zRot = -spread;
        footL.zRot = spread;

        // ---- torso / head
        torso.xRot = pose[TX] + 0.04F + breathe * 0.015F + shake * 0.05F * Mth.sin(age * 2.3F);
        torso.yRot = pose[TY];
        torso.zRot = pose[TZ] + shake * 0.04F * Mth.sin(age * 3.1F);
        float hy = Mth.clamp(netHeadYaw, -50F, 50F) * Mth.DEG_TO_RAD * 0.6F;
        float hp = Mth.clamp(headPitch, -40F, 40F) * Mth.DEG_TO_RAD * 0.5F;
        boolean fixedHead = mode != BossBase.MODE_FIGHT;
        head.xRot = pose[HX] - pose[TX] * 0.55F + (fixedHead ? 0F : hp) + shake * 0.06F * Mth.sin(age * 4.1F);
        head.yRot = pose[HY] - pose[TY] * 0.5F + (fixedHead ? 0F : hy);
        jaw.xRot = pose[JAW] + 0.06F + breathe * 0.03F;

        // ---- arms
        float idleSway = Mth.sin(age * 0.08F) * 0.03F;
        float walkArm = walking ? Mth.cos(limbSwing * 0.5F) * 0.45F * lsa : 0F;
        boolean armsFree = true;
        armR.xRot = pose[RX] - walkArm * (armsFree ? 1F : 0F) - 0.08F + shake * 0.07F * Mth.sin(age * 2.7F);
        armL.xRot = pose[LX] + walkArm - 0.08F + shake * 0.07F * Mth.sin(age * 2.5F + 1F);
        armR.yRot = pose[RY];
        armL.yRot = -pose[LY];
        armR.zRot = pose[RZ] + 0.1F + idleSway;
        armL.zRot = -pose[LZ] - 0.1F - idleSway;
        foreR.xRot = pose[FR] - 0.28F + (walking ? Math.max(0F, walkArm) * -0.5F : 0F);
        foreL.xRot = pose[FL] - 0.28F + (walking ? Math.max(0F, -walkArm) * -0.5F : 0F);

        // ---- wings, halo, tail
        float flap = Mth.sin(age * 0.13F) * 0.05F;
        float wf = pose[WING];
        wingL.yRot += wf * 0.8F + flap;
        wingR.yRot -= wf * 0.8F + flap;
        wingL.xRot -= wf * 0.35F;
        wingR.xRot -= wf * 0.35F;
        wingL.zRot = wf * 0.18F + flap;
        wingR.zRot = -(wf * 0.18F + flap);

        float energy = pose[HALO];
        halo.zRot = age * 0.03F * (1F + energy * 3F);
        halo2.zRot = -age * 0.05F * (1F + energy * 3F);
        halo.y += px(Mth.sin(age * 0.06F) * 0.8F);
        halo2.y += px(Mth.sin(age * 0.06F + 1.2F) * 0.8F);
        halo.yRot = Mth.sin(age * 0.04F) * 0.12F;
        halo2.yRot = Mth.sin(age * 0.05F + 1F) * 0.15F;

        float tailAmp = 0.2F + lsa * 0.2F + pose[SHAKE] * 0.08F;
        for (int i = 0; i < 6; i++) {
            tail[i].yRot = Mth.sin(age * 0.1F - i * 0.7F) * tailAmp;
            tail[i].xRot += Mth.sin(age * 0.07F - i * 0.5F) * 0.05F + (crouch > 0.5F ? 0.15F : 0F);
        }
    }
}
