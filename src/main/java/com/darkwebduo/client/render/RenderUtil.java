package com.darkwebduo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Low-level quad / cuboid / beam emitters used by every custom 3D renderer in the mod. */
public final class RenderUtil {
    private RenderUtil() {}

    /** Absolute UV rectangle of one cuboid face. */
    public static final class Uv {
        public final float u0, v0, u1, v1;

        public Uv(float u0, float v0, float u1, float v1) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
        }

        /** UV rectangle inside an atlas sprite, given as fractions (0..1) of the sprite. */
        public static Uv of(TextureAtlasSprite s, float fu0, float fv0, float fu1, float fv1) {
            float du = s.getU1() - s.getU0();
            float dv = s.getV1() - s.getV0();
            return new Uv(s.getU0() + du * fu0, s.getV0() + dv * fv0, s.getU0() + du * fu1, s.getV0() + dv * fv1);
        }

        /** Whole texture (0..1). */
        public static Uv full() {
            return new Uv(0f, 0f, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ textured (NEW_ENTITY vertex format)

    public static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                              int light, int overlay, float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    /**
     * Emits one textured quad. Corners are given counter-clockwise when looking at the face from outside:
     * (1)=bottom-left (2)=bottom-right (3)=top-right (4)=top-left, mapped to (u0,v1) (u1,v1) (u1,v0) (u0,v0).
     * Use with a no-cull render type.
     */
    public static void quad(VertexConsumer vc, PoseStack.Pose pose,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float x3, float y3, float z3, float x4, float y4, float z4,
                            Uv uv, float nx, float ny, float nz, int light, int overlay,
                            float r, float g, float b, float a) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, x1, y1, z1, uv.u0, uv.v1, light, overlay, r, g, b, a, nx, ny, nz);
        vertex(vc, m, n, x2, y2, z2, uv.u1, uv.v1, light, overlay, r, g, b, a, nx, ny, nz);
        vertex(vc, m, n, x3, y3, z3, uv.u1, uv.v0, light, overlay, r, g, b, a, nx, ny, nz);
        vertex(vc, m, n, x4, y4, z4, uv.u0, uv.v0, light, overlay, r, g, b, a, nx, ny, nz);
    }

    /**
     * Textured axis-aligned cuboid. Faces: down, up, north(-Z), south(+Z), west(-X), east(+X).
     * Pass the same Uv for several faces if you like. Any Uv may be null to skip that face.
     */
    public static void cuboid(VertexConsumer vc, PoseStack.Pose pose,
                              float x0, float y0, float z0, float x1, float y1, float z1,
                              Uv down, Uv up, Uv north, Uv south, Uv west, Uv east,
                              int light, int overlay, float r, float g, float b, float a) {
        if (down != null) quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, down, 0, -1, 0, light, overlay, r, g, b, a);
        if (up != null) quad(vc, pose, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, up, 0, 1, 0, light, overlay, r, g, b, a);
        if (north != null) quad(vc, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, north, 0, 0, -1, light, overlay, r, g, b, a);
        if (south != null) quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, south, 0, 0, 1, light, overlay, r, g, b, a);
        if (west != null) quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, west, -1, 0, 0, light, overlay, r, g, b, a);
        if (east != null) quad(vc, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, east, 1, 0, 0, light, overlay, r, g, b, a);
    }

    /** Cuboid with the same Uv on every face. */
    public static void cuboid(VertexConsumer vc, PoseStack.Pose pose,
                              float x0, float y0, float z0, float x1, float y1, float z1,
                              Uv all, int light, int overlay, float r, float g, float b, float a) {
        cuboid(vc, pose, x0, y0, z0, x1, y1, z1, all, all, all, all, all, all, light, overlay, r, g, b, a);
    }

    // ------------------------------------------------------------------ additive glow (POSITION_COLOR, RenderType.lightning())

    public static void colorVertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }

    /** A POSITION_COLOR quad drawn with both windings so it survives culling. Alpha per corner for gradients. */
    public static void glowQuad(VertexConsumer vc, Matrix4f m,
                                float x1, float y1, float z1, float a1,
                                float x2, float y2, float z2, float a2,
                                float x3, float y3, float z3, float a3,
                                float x4, float y4, float z4, float a4,
                                float r, float g, float b) {
        colorVertex(vc, m, x1, y1, z1, r, g, b, a1);
        colorVertex(vc, m, x2, y2, z2, r, g, b, a2);
        colorVertex(vc, m, x3, y3, z3, r, g, b, a3);
        colorVertex(vc, m, x4, y4, z4, r, g, b, a4);
        colorVertex(vc, m, x4, y4, z4, r, g, b, a4);
        colorVertex(vc, m, x3, y3, z3, r, g, b, a3);
        colorVertex(vc, m, x2, y2, z2, r, g, b, a2);
        colorVertex(vc, m, x1, y1, z1, r, g, b, a1);
    }

    /**
     * Vertical glowing beam (prism with `sides` faces) from (cx,y0,cz) up to y1, radius r0 at the bottom and r1 at the top,
     * alpha aBottom at the base fading to aTop at the top. spin rotates it (radians).
     */
    public static void beam(VertexConsumer vc, Matrix4f m, float cx, float y0, float cz, float y1,
                            float r0, float r1, float aBottom, float aTop, int sides, float spin,
                            float r, float g, float b) {
        for (int i = 0; i < sides; i++) {
            float a0 = spin + (float) (Math.PI * 2 * i / sides);
            float a1 = spin + (float) (Math.PI * 2 * (i + 1) / sides);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            glowQuad(vc, m,
                    cx + c0 * r0, y0, cz + s0 * r0, aBottom,
                    cx + c1 * r0, y0, cz + s1 * r0, aBottom,
                    cx + c1 * r1, y1, cz + s1 * r1, aTop,
                    cx + c0 * r1, y1, cz + s0 * r1, aTop,
                    r, g, b);
        }
    }

    /** Flat glowing disc / ring on the XZ plane at height y. inner may be 0 for a full disc. */
    public static void disc(VertexConsumer vc, Matrix4f m, float cx, float y, float cz, float inner, float outer,
                            float aInner, float aOuter, int segments, float spin, float r, float g, float b) {
        for (int i = 0; i < segments; i++) {
            float a0 = spin + (float) (Math.PI * 2 * i / segments);
            float a1 = spin + (float) (Math.PI * 2 * (i + 1) / segments);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            glowQuad(vc, m,
                    cx + c0 * inner, y, cz + s0 * inner, aInner,
                    cx + c1 * inner, y, cz + s1 * inner, aInner,
                    cx + c1 * outer, y, cz + s1 * outer, aOuter,
                    cx + c0 * outer, y, cz + s0 * outer, aOuter,
                    r, g, b);
        }
    }

    /** Camera-independent glowing cross (two vertical planes) – a cheap "sparkle" / flare. */
    public static void flare(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float size, float a,
                             float spin, float r, float g, float b) {
        for (int k = 0; k < 2; k++) {
            float ang = spin + k * (float) (Math.PI / 2);
            float dx = (float) Math.cos(ang) * size, dz = (float) Math.sin(ang) * size;
            glowQuad(vc, m,
                    cx - dx, cy, cz - dz, 0f,
                    cx, cy - size, cz, a,
                    cx + dx, cy, cz + dz, 0f,
                    cx, cy + size, cz, a,
                    r, g, b);
        }
    }

    /**
     * Glowing prism (tube) of any orientation from (sx,sy,sz) along the direction (dx,dy,dz) for `len` blocks.
     * Radius r0 at the start, r1 at the end; alpha a0 -> a1. Both windings are emitted.
     */
    public static void prism(VertexConsumer vc, Matrix4f m, float sx, float sy, float sz, float dx, float dy, float dz, float len,
                             float r0, float r1, float a0, float a1, int sides, float spin, float cr, float cg, float cb) {
        Vector3f d = new Vector3f(dx, dy, dz);
        if (d.lengthSquared() < 1e-8F) return;
        d.normalize();
        Vector3f up = Math.abs(d.y) > 0.95F ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);
        Vector3f u = d.cross(up, new Vector3f()).normalize();
        Vector3f v = d.cross(u, new Vector3f()).normalize();
        float ex = sx + d.x * len, ey = sy + d.y * len, ez = sz + d.z * len;
        for (int i = 0; i < sides; i++) {
            float t0 = spin + (float) (Math.PI * 2 * i / sides);
            float t1 = spin + (float) (Math.PI * 2 * (i + 1) / sides);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float ax = u.x * c0 + v.x * s0, ay = u.y * c0 + v.y * s0, az = u.z * c0 + v.z * s0;
            float bx = u.x * c1 + v.x * s1, by = u.y * c1 + v.y * s1, bz = u.z * c1 + v.z * s1;
            glowQuad(vc, m,
                    sx + ax * r0, sy + ay * r0, sz + az * r0, a0,
                    sx + bx * r0, sy + by * r0, sz + bz * r0, a0,
                    ex + bx * r1, ey + by * r1, ez + bz * r1, a1,
                    ex + ax * r1, ey + ay * r1, ez + az * r1, a1,
                    cr, cg, cb);
        }
    }

    /** Hollow glowing sphere made of stacked rings (cheap energy shell). */
    public static void shell(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float radius, float alpha,
                             int rings, int segs, float spin, float cr, float cg, float cb) {
        for (int k = 0; k < rings; k++) {
            float lat0 = (float) (-Math.PI / 2 + Math.PI * k / rings);
            float lat1 = (float) (-Math.PI / 2 + Math.PI * (k + 1) / rings);
            float y0 = (float) Math.sin(lat0) * radius, y1 = (float) Math.sin(lat1) * radius;
            float r0 = (float) Math.cos(lat0) * radius, r1 = (float) Math.cos(lat1) * radius;
            float edge = 1F - Math.abs((lat0 + lat1) * 0.5F) / 1.5708F;
            float a = alpha * (0.35F + 0.65F * (1F - edge * 0.0F));
            for (int i = 0; i < segs; i++) {
                float t0 = spin + (float) (Math.PI * 2 * i / segs);
                float t1 = spin + (float) (Math.PI * 2 * (i + 1) / segs);
                float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
                float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
                glowQuad(vc, m,
                        cx + c0 * r0, cy + y0, cz + s0 * r0, a,
                        cx + c1 * r0, cy + y0, cz + s1 * r0, a,
                        cx + c1 * r1, cy + y1, cz + s1 * r1, a,
                        cx + c0 * r1, cy + y1, cz + s0 * r1, a,
                        cr, cg, cb);
            }
        }
    }
}
