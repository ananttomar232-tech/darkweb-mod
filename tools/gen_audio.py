#!/usr/bin/env python3
"""
gen_audio.py -- procedural sound-design + music generator for "Dark Web & iPhone Duo" (mod id darkweb_duo).

Synthesises EVERY sound listed in src/main/java/com/darkwebduo/registry/ModSounds.java from scratch
(numpy + scipy.signal for filtering/convolution, ffmpeg/libvorbis for encoding) and writes

  src/main/resources/assets/darkweb_duo/sounds/<name>.ogg
  src/main/resources/assets/darkweb_duo/sounds.json
  tools/lang/audio.json                     (subtitle strings, flat JSON)

Deterministic (every sound seeds its own RNG from its name) and idempotent.  Usage:

  python3 tools/gen_audio.py                 # everything
  python3 tools/gen_audio.py box_open key_click      # only these sounds (sounds.json/lang are always rewritten)
  python3 tools/gen_audio.py --sfx           # all sound effects, no music
  python3 tools/gen_audio.py --music         # only the two themes

Layout of this file:  1) DSP helpers   2) drum / synth voices   3) SFX   4) music   5) pipeline.
SFX are MONO (Minecraft attenuates mono sounds with distance), music themes are STEREO.
"""
import argparse
import json
import math
import re
import subprocess
import sys
import time
import zlib
from functools import lru_cache
from pathlib import Path

import numpy as np

try:
    from scipy import signal as sg
except ImportError:  # pragma: no cover
    subprocess.check_call([sys.executable, "-m", "pip", "install", "scipy", "--break-system-packages", "-q"])
    from scipy import signal as sg

SR = 44100
MODID = "darkweb_duo"
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources/assets" / MODID
OUT_SOUNDS = RES / "sounds"
OUT_JSON = RES / "sounds.json"
OUT_LANG = ROOT / "tools/lang/audio.json"
MODSOUNDS_JAVA = ROOT / "src/main/java/com/darkwebduo/registry/ModSounds.java"

TAU = 2.0 * np.pi
SFX_PEAK_DB = -1.5


# =====================================================================================================================
# 1) DSP HELPERS
# =====================================================================================================================
class _G:
    rng = np.random.default_rng(0)


def R():
    return _G.rng


def seed(name):
    _G.rng = np.random.default_rng(zlib.crc32(name.encode()) & 0xFFFFFFFF)


def N(sec):
    return int(round(sec * SR))


def T(n):
    return np.arange(n) / SR


def db(x):
    return 10.0 ** (x / 20.0)


def mtof(m):
    return 440.0 * 2.0 ** ((np.asarray(m, dtype=float) - 69.0) / 12.0)


_NOTES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def nn(s):
    """'F#3' -> midi number (C4 = 60)."""
    if isinstance(s, (int, float)):
        return s
    m = _NOTES[s[0]]
    i = 1
    while i < len(s) and s[i] in "#b":
        m += 1 if s[i] == "#" else -1
        i += 1
    return m + 12 * (int(s[i:]) + 1)


def fr(s):
    return float(mtof(nn(s)))


def zeros(sec):
    return np.zeros(N(sec))


def nrm(x, peak=1.0):
    p = np.max(np.abs(x))
    return x * (peak / p) if p > 1e-12 else x


def _arr(v, n):
    """scalar / short array -> length-n float array (linear resample for arrays of other lengths)."""
    a = np.asarray(v, dtype=float)
    if a.ndim == 0:
        return np.full(n, float(a))
    if len(a) == n:
        return a
    return np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(a)), a)


# ---- envelopes ------------------------------------------------------------------------------------------------------
def ep(n, pts, log=False):
    """Piece-wise envelope from [(time_s, value), ...]; log=True interpolates exponentially (for pitch/freq)."""
    ts = [p[0] for p in pts]
    vs = [p[1] for p in pts]
    t = T(n)
    if log:
        return np.exp(np.interp(t, ts, np.log(vs)))
    return np.interp(t, ts, vs)


def adsr(n, a=0.01, d=0.1, s=0.7, r=0.1, k=4.0):
    """n = total samples including release; note is gated off n - r seconds in."""
    na, nd, nr = max(1, N(a)), max(1, N(d)), max(1, N(r))
    ng = max(n - nr, na)
    idx = np.arange(n)
    att = np.minimum(idx / na, 1.0)
    att = att * att * (3 - 2 * att)  # smoothstep attack: no clicks
    td = np.clip((idx - na) / nd, 0, 1)
    dec = s + (1 - s) * (np.exp(-k * td) - np.exp(-k)) / (1 - np.exp(-k))
    lvl = np.where(idx < na, att, dec)
    tr = np.clip((idx - ng) / nr, 0, 1)
    rel = (1 - tr) ** 2
    return lvl * rel


def exp_env(n, tau, att=0.0005):
    t = T(n)
    e = np.exp(-t / tau)
    if att > 0:
        e = e * (1 - np.exp(-t / att))
    return e


def bump(n, peak=0.5, p=2.0):
    """smooth 0..1..0 envelope with its maximum at fraction `peak` of the length."""
    x = np.linspace(0, 1, n)
    up = np.clip(x / max(peak, 1e-6), 0, 1)
    dn = np.clip((1 - x) / max(1 - peak, 1e-6), 0, 1)
    return (np.sin(up * np.pi / 2) ** p) * (np.sin(dn * np.pi / 2) ** p)


def fade(x, fi=0.002, fo=0.01):
    x = x.copy()
    ni, no = min(N(fi), len(x)), min(N(fo), len(x))
    if ni > 1:
        x[:ni] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, ni))
    if no > 1:
        x[-no:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, no))
    return x


# ---- oscillators ----------------------------------------------------------------------------------------------------
def _phase(freq, n, ph0=0.0):
    f = _arr(freq, n)
    dt = np.minimum(f / SR, 0.45)
    ph = np.cumsum(dt) - dt + ph0
    return ph, dt


def sine(freq, n, ph0=0.0):
    ph, _ = _phase(freq, n, ph0)
    return np.sin(TAU * ph)


def _blep(t, dt):
    y = np.zeros_like(t)
    m = t < dt
    x = t[m] / dt[m]
    y[m] = x + x - x * x - 1.0
    m = t > 1.0 - dt
    x = (t[m] - 1.0) / dt[m]
    y[m] = x * x + x + x + 1.0
    return y


def saw(freq, n, ph0=0.0):
    ph, dt = _phase(freq, n, ph0)
    t = ph % 1.0
    return 2.0 * t - 1.0 - _blep(t, dt)


def square(freq, n, pw=0.5, ph0=0.0):
    ph, dt = _phase(freq, n, ph0)
    t = ph % 1.0
    y = np.where(t < pw, 1.0, -1.0)
    y = y + _blep(t, dt) - _blep((t + 1.0 - pw) % 1.0, dt)
    return y


def tri(freq, n, ph0=0.0):
    ph, _ = _phase(freq, n, ph0)
    return 4.0 * np.abs(ph % 1.0 - 0.5) - 1.0


def fm(fc, ratio, index, n, fb=0.0):
    """2-operator FM. fc: Hz (scalar/array); index: scalar or array of modulation index."""
    ph, _ = _phase(fc, n)
    phm, _ = _phase(_arr(fc, n) * ratio, n)
    return np.sin(TAU * ph + _arr(index, n) * np.sin(TAU * phm))


def supersaw(freq, n, voices=5, detune=12.0, stereo=False, vib=None, spread=0.8):
    """Detuned saw stack. detune in cents (total +-). Returns mono, or (L, R) when stereo=True."""
    f0 = _arr(freq, n)
    if vib is not None:  # (rate_hz, depth_fraction)
        f0 = f0 * (1 + vib[1] * np.sin(TAU * vib[0] * T(n) + R().uniform(0, TAU)))
    offs = np.linspace(-1, 1, voices) * detune
    L = np.zeros(n)
    Rr = np.zeros(n)
    for i, o in enumerate(offs):
        s = saw(f0 * 2 ** (o / 1200.0), n, ph0=R().random())
        p = (i / (voices - 1) * 2 - 1) * spread if voices > 1 else 0.0
        L += s * math.cos((p + 1) * math.pi / 4)
        Rr += s * math.sin((p + 1) * math.pi / 4)
    k = 1.0 / math.sqrt(voices)
    if stereo:
        return L * k, Rr * k
    return (L + Rr) * k / math.sqrt(2) * 1.0


# ---- noise ----------------------------------------------------------------------------------------------------------
def wn(n):
    return R().uniform(-1, 1, n)


def bandnoise(n, lo=20.0, hi=18000.0, tilt=0.0, edge=4):
    """FFT-synthesised noise with a smooth band-pass magnitude and optional spectral tilt (amplitude ~ f^-tilt).
    Periodic over n samples (loop friendly). Normalised to peak 1."""
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = 1e-3
    mag = 1.0 / (1.0 + (lo / f) ** edge) / (1.0 + (f / hi) ** edge)
    if tilt:
        mag = mag * (f / 1000.0) ** (-tilt)
    ph = R().uniform(0, TAU, len(f))
    x = np.fft.irfft(mag * np.exp(1j * ph), n)
    return x / max(np.max(np.abs(x)), 1e-9)


def pink(n):
    return bandnoise(n, 20, 20000, tilt=0.5)


# ---- filters --------------------------------------------------------------------------------------------------------
def _rbj(kind, fc, q, gdb=0.0):
    fc = np.clip(np.asarray(fc, float), 8.0, SR * 0.47)
    q = np.maximum(np.asarray(q, float), 0.05)
    w0 = TAU * fc / SR
    c, s = np.cos(w0), np.sin(w0)
    al = s / (2 * q)
    one = np.ones_like(c)
    a0, a1, a2 = 1 + al, -2 * c, 1 - al
    if kind == "lp":
        b0 = (1 - c) / 2
        b1 = 1 - c
        b2 = b0
    elif kind == "hp":
        b0 = (1 + c) / 2
        b1 = -(1 + c)
        b2 = b0
    elif kind == "bp":
        b0, b1, b2 = al, 0 * one, -al
    elif kind == "notch":
        b0, b1, b2 = one, -2 * c, one
    elif kind == "peak":
        A = 10 ** (gdb / 40.0)
        b0, b1, b2 = 1 + al * A, -2 * c, 1 - al * A
        a0, a1, a2 = 1 + al / A, -2 * c, 1 - al / A
    elif kind == "ap":
        b0, b1, b2 = 1 - al, -2 * c, 1 + al
    elif kind == "ls":  # low shelf
        A = 10 ** (gdb / 40.0)
        sq = 2 * np.sqrt(A) * al
        b0 = A * ((A + 1) - (A - 1) * c + sq)
        b1 = 2 * A * ((A - 1) - (A + 1) * c)
        b2 = A * ((A + 1) - (A - 1) * c - sq)
        a0 = (A + 1) + (A - 1) * c + sq
        a1 = -2 * ((A - 1) + (A + 1) * c)
        a2 = (A + 1) + (A - 1) * c - sq
    elif kind == "hs":  # high shelf
        A = 10 ** (gdb / 40.0)
        sq = 2 * np.sqrt(A) * al
        b0 = A * ((A + 1) + (A - 1) * c + sq)
        b1 = -2 * A * ((A - 1) + (A + 1) * c)
        b2 = A * ((A + 1) + (A - 1) * c - sq)
        a0 = (A + 1) - (A - 1) * c + sq
        a1 = 2 * ((A - 1) - (A + 1) * c)
        a2 = (A + 1) - (A - 1) * c - sq
    else:
        raise ValueError(kind)
    return b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0


def bq(x, kind, fc, q=0.7071, gdb=0.0):
    b0, b1, b2, a1, a2 = _rbj(kind, fc, q, gdb)
    return sg.lfilter([float(b0), float(b1), float(b2)], [1.0, float(a1), float(a2)], x)


def lp(x, fc, q=0.7071):
    return bq(x, "lp", fc, q)


def hp(x, fc, q=0.7071):
    return bq(x, "hp", fc, q)


def bp(x, fc, q=2.0):
    return bq(x, "bp", fc, q)


def lpf(x, fc, order=4):
    fc = min(fc, SR * 0.47)
    return sg.sosfilt(sg.butter(order, fc / (SR / 2), "low", output="sos"), x)


def hpf(x, fc, order=4):
    return sg.sosfilt(sg.butter(order, max(fc, 5.0) / (SR / 2), "high", output="sos"), x)


def bpf(x, lo, hi, order=2):
    hi = min(hi, SR * 0.47)
    return sg.sosfilt(sg.butter(order, [lo / (SR / 2), hi / (SR / 2)], "band", output="sos"), x)


def sweep(x, kind, fc, q=1.0, chunk=64):
    """Time-varying RBJ biquad. fc/q: scalar or array. State is carried across chunks."""
    n = len(x)
    fc = _arr(fc, n)
    q = _arr(q, n)
    nch = (n + chunk - 1) // chunk
    mid = np.minimum(np.arange(nch) * chunk + chunk // 2, n - 1)
    b0, b1, b2, a1, a2 = _rbj(kind, fc[mid], q[mid])
    y = np.empty(n)
    zi = np.zeros(2)
    for i in range(nch):
        s = i * chunk
        e = min(n, s + chunk)
        y[s:e], zi = sg.lfilter([b0[i], b1[i], b2[i]], [1.0, a1[i], a2[i]], x[s:e], zi=zi)
    return y


def dcblock(x, fc=18.0):
    return sg.sosfilt(sg.butter(1, fc / (SR / 2), "high", output="sos"), x)


# ---- distortion / digital damage ------------------------------------------------------------------------------------
def sat(x, drive=2.0, asym=0.0):
    if asym:
        y = np.tanh(drive * x + asym) - np.tanh(asym)
        return y / (np.tanh(drive + asym) - np.tanh(asym))
    return np.tanh(drive * x) / np.tanh(drive)


def fold(x, k=2.0):
    return np.sin(k * x * np.pi / 2) / 1.0


def crush(x, bits=8, down=4):
    """Bitcrusher. down: sample-rate divisor (scalar or per-sample array)."""
    n = len(x)
    d = _arr(down, n)
    if np.all(d <= 1.0):
        y = x
    else:
        k = np.floor(np.cumsum(1.0 / np.maximum(d, 1.0)))
        _, first, inv = np.unique(k, return_index=True, return_inverse=True)
        y = x[first][inv]
    if bits < 16:
        q = 2.0 ** (bits - 1)
        y = np.round(y * q) / q
    return y


def ringmod(x, f):
    return x * sine(f, len(x))


def stutter(x, t0, dur, slice_ms, rev=False, g=1.0):
    """Replace a region by a repeated slice (buffer-stutter glitch)."""
    x = x.copy()
    s = N(t0)
    e = min(len(x), s + N(dur))
    L = max(8, N(slice_ms / 1000.0))
    if s >= len(x) or e - s < 8:
        return x
    seg = x[s:s + L].copy()
    if rev:
        seg = seg[::-1].copy()
    w = np.ones(len(seg))
    f = min(N(0.0012), len(seg) // 3)
    if f > 1:
        w[:f] = np.linspace(0, 1, f)
        w[-f:] = np.linspace(1, 0, f)
    seg = seg * w
    reps = np.tile(seg, (e - s) // len(seg) + 2)[: e - s]
    x[s:e] = reps * g
    return x


def dropout(x, t0, dur):
    x = x.copy()
    s, e = N(t0), min(len(x), N(t0 + dur))
    if e - s < 4:
        return x
    w = np.ones(e - s)
    f = min(N(0.001), (e - s) // 3)
    if f > 1:
        w[:f] = np.linspace(1, 0, f)
        w[-f:] = np.linspace(0, 1, f)
    w[f:-f if f > 1 else None] = 0.0
    x[s:e] *= w
    return x


def glitchify(x, t_start, t_end, events=8, max_slice=60, amount=1.0, seed_off=0):
    """Random mix of stutters, dropouts, reversed slices and crush bursts between t_start and t_end (seconds)."""
    r = np.random.default_rng(zlib.crc32(b"glitch") + seed_off + len(x))
    y = x.copy()
    for _ in range(events):
        t0 = r.uniform(t_start, max(t_start + 0.01, t_end - 0.05))
        kind = r.integers(0, 4)
        if kind == 0:
            y = stutter(y, t0, r.uniform(0.03, 0.12), r.uniform(6, max_slice))
        elif kind == 1:
            y = dropout(y, t0, r.uniform(0.008, 0.035))
        elif kind == 2:
            y = stutter(y, t0, r.uniform(0.03, 0.1), r.uniform(8, max_slice), rev=True)
        else:
            s, e = N(t0), min(len(y), N(t0 + r.uniform(0.02, 0.08)))
            if e - s > 16:
                y[s:e] = crush(y[s:e], r.integers(3, 7), r.uniform(4, 24))
    return x * (1 - amount) + y * amount if amount < 1 else y


# ---- space ----------------------------------------------------------------------------------------------------------
@lru_cache(maxsize=64)
def make_ir(rt60, seed_=1, damp=0.5, pre=0.0, early=True):
    r = np.random.default_rng(seed_ * 7919 + int(rt60 * 100))
    n = N(rt60 * 1.15) + N(pre) + 16
    t = np.arange(n) / SR
    nz = r.standard_normal(n)
    lo = lp(nz, 450)
    hi = hp(nz, 3200 - 1800 * damp)
    mid = nz - lo - hi
    dec = 6.91 / rt60
    ir = lo * np.exp(-t * dec / 1.25) + mid * np.exp(-t * dec) + hi * np.exp(-t * dec * (1.0 + 2.2 * damp)) * 0.8
    ir *= 1 - np.exp(-t / 0.004)
    if early:
        for _ in range(9):
            k = int(r.uniform(0.004, 0.09) * SR)
            ir[k] += r.uniform(-1, 1) * 2.5 * np.exp(-k / SR / (rt60 * 0.3))
    if pre > 0:
        ir = np.concatenate([np.zeros(N(pre)), ir])
    ir /= math.sqrt(np.sum(ir ** 2))
    return ir


def reverb(x, rt60=1.5, wet=0.3, dry=1.0, damp=0.5, pre=0.0, seed_=1, tail=0.0):
    """Convolution reverb with a synthesised decaying-noise IR. Output = input length + tail seconds."""
    ir = make_ir(float(rt60), seed_, float(damp), float(pre))
    w = sg.oaconvolve(x, ir)
    n_out = len(x) + N(tail)
    y = np.zeros(n_out)
    m = min(len(x), n_out)
    y[:m] += x[:m] * dry
    m = min(len(w), n_out)
    y[:m] += w[:m] * wet
    return y


def reverb2(send, rt60=2.0, damp=0.5, pre=0.012, seed_=3):
    """Stereo reverb return from a mono (or 2xN) send: two decorrelated IRs. Returns (2, n) of the wet signal only."""
    s = send if send.ndim == 1 else send.sum(axis=0) * 0.5
    a = sg.oaconvolve(s, make_ir(float(rt60), seed_, float(damp), float(pre)))[: len(s)]
    b = sg.oaconvolve(s, make_ir(float(rt60), seed_ + 11, float(damp), float(pre) * 1.3))[: len(s)]
    return np.stack([a, b])


def echo(x, delay, fb=0.45, mix=0.35, damp=4500, taps=8):
    d = N(delay)
    y = np.zeros(len(x))
    cur = x
    for k in range(1, taps + 1):
        cur = np.concatenate([np.zeros(d), cur[:-d]])
        cur = lp(cur, damp) * fb
        y += cur
    return x + mix * y


def moddelay(x, delay_s):
    """Fractional delay line with a per-sample delay (seconds): chorus / flanger / vibrato core."""
    n = len(x)
    idx = np.arange(n) - _arr(delay_s, n) * SR
    return np.interp(idx, np.arange(n), x)


def chorus(x, base=0.018, depth=0.004, rate=0.6, mix=0.5, ph=0.0):
    t = T(len(x))
    return x * (1 - mix * 0.5) + mix * 0.5 * moddelay(x, base + depth * np.sin(TAU * rate * t + ph))


def flanger(x, base=0.003, depth=0.0025, rate=0.3, mix=0.6):
    t = T(len(x))
    return x + mix * moddelay(x, base + depth * (0.5 + 0.5 * np.sin(TAU * rate * t)))


def phaser_ap(x, fc_env, stages=4, q=0.6, mix=0.5):
    y = x
    for _ in range(stages):
        y = sweep(y, "ap", fc_env, q=q)
    return x * (1 - mix * 0.5) + y * mix * 0.5


def duck_curve(n, times, depth=0.7, rel=0.18, att=0.004, curve=1.6):
    g = np.ones(n)
    a, r = max(1, N(att)), N(rel)
    shape = np.concatenate([np.linspace(1, 1 - depth, a, endpoint=False),
                            (1 - depth) + depth * (np.linspace(0, 1, r) ** curve)])
    for t0 in times:
        s = N(t0)
        if s >= n:
            continue
        e = min(n, s + len(shape))
        g[s:e] = np.minimum(g[s:e], shape[: e - s])
    return g


# ---- mixing ---------------------------------------------------------------------------------------------------------
def put(buf, sig, t0=0.0, g=1.0, pan=0.0):
    """Add `sig` into `buf` at t0 seconds. buf (n,) mono or (2, n) stereo; sig (m,) or (2, m). pan -1..1 (mono sig->stereo)."""
    s = N(t0)
    m = sig.shape[-1]
    if s >= buf.shape[-1] or s + m <= 0:
        return buf
    a = max(0, -s)
    s0 = max(0, s)
    e = min(buf.shape[-1], s + m)
    part = sig[..., a: a + (e - s0)]
    if buf.ndim == 1:
        buf[s0:e] += (part if part.ndim == 1 else part.sum(axis=0) * 0.5) * g
    else:
        if part.ndim == 1:
            p = (pan + 1) * math.pi / 4  # equal-power pan, centre = unity gain in each channel
            buf[0, s0:e] += part * g * math.cos(p) * math.sqrt(2)
            buf[1, s0:e] += part * g * math.sin(p) * math.sqrt(2)
        else:
            buf[:, s0:e] += part * g
    return buf


def midside(st, width=1.3):
    m = (st[0] + st[1]) * 0.5
    s = (st[0] - st[1]) * 0.5 * width
    return np.stack([m + s, m - s])


# ---- reusable "instruments" -----------------------------------------------------------------------------------------
BELL_P = [(1.0, 1.0, 1.0), (2.0, 0.5, 0.7), (2.76, 0.45, 0.5), (5.4, 0.25, 0.3), (8.93, 0.12, 0.2)]
GLASS_P = [(1.0, 1.0, 1.0), (2.32, 0.6, 0.6), (4.25, 0.4, 0.4), (6.63, 0.3, 0.3), (9.38, 0.15, 0.2)]
CRYSTAL_P = [(1.0, 1.0, 1.0), (2.6, 0.55, 0.8), (4.1, 0.4, 0.55), (5.9, 0.3, 0.4), (8.3, 0.22, 0.3), (11.2, 0.1, 0.2)]
GONG_P = [(1.0, 1.0, 1.0), (1.47, 0.7, 0.9), (2.09, 0.6, 0.8), (2.56, 0.5, 0.7), (3.4, 0.35, 0.6), (4.7, 0.3, 0.4), (6.3, 0.2, 0.3)]


def bell(freq, dur, decay=0.5, partials=BELL_P, att=0.0006, bright=1.0):
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    for r_, a_, d_ in partials:
        f = freq * r_
        if f > SR * 0.45:
            continue
        y += a_ * (bright ** (r_ - 1.0)) * np.sin(TAU * f * t + R().uniform(0, TAU)) * np.exp(-t / (decay * d_))
    return y * (1 - np.exp(-t / att))


def fmtone(freq, dur, ratio=1.0, index=2.0, decay=0.3, idx_decay=0.12, att=0.002):
    n = N(dur)
    t = T(n)
    idx = index * np.exp(-t / idx_decay)
    return np.sin(TAU * freq * t + idx * np.sin(TAU * freq * ratio * t)) * np.exp(-t / decay) * (1 - np.exp(-t / att))


def thump(dur, f0=140.0, f1=45.0, tau=0.05, decay=0.2, drive=1.5, click=0.3):
    n = N(dur)
    t = T(n)
    f = f1 + (f0 - f1) * np.exp(-t / tau)
    y = sine(f, n) * np.exp(-t / decay) * (1 - np.exp(-t / 0.0008))
    y = sat(y, drive)
    if click > 0:
        y = y + click * lp(wn(n), 4000) * np.exp(-t / 0.003)
    return y


def hit_noise(dur, fc=3000.0, q=1.2, tau=0.004, kind="bp"):
    n = N(dur)
    x = wn(n) * np.exp(-T(n) / tau)
    return bq(x, kind, fc, q)


def whoosh(dur, f0, f1, q=2.0, peak=0.5, p=1.5, kind="bp"):
    n = N(dur)
    x = wn(n)
    f = ep(n, [(0, f0), (dur, f1)], log=True)
    y = sweep(x, kind, f, q)
    return y * bump(n, peak, p)


def hiss(dur, lo=2500.0, hi=11000.0, att=0.02, tau=0.4, order=2):
    n = N(dur)
    y = bpf(wn(n), lo, hi, order)
    return y * (1 - np.exp(-T(n) / att)) * np.exp(-T(n) / tau)


def crackle(dur, rate, fc=4500.0, q=1.2, amp=1.0):
    """Electric crackle: random impulses (rate = Hz scalar/array) through a resonant band-pass."""
    n = N(dur)
    p = _arr(rate, n) / SR
    mask = R().random(n) < p
    imp = mask * R().uniform(-1, 1, n) * amp
    return bq(imp, "bp", fc, q) * 4.0


def sparkle(dur, rate, fmin=2500.0, fmax=9000.0, tau=(0.015, 0.06), g=1.0, t_env=None):
    """Random high sine pings (rate = events/sec, scalar or array)."""
    n = N(dur)
    y = np.zeros(n)
    rt = _arr(rate, n)
    cnt = int(np.sum(rt) / SR)
    times = np.sort(R().random(int(R().poisson(max(cnt, 1)))) * dur)
    for t0 in times:
        if t_env is not None and R().random() > float(np.interp(t0, np.linspace(0, dur, len(t_env)), t_env)):
            continue
        f = math.exp(R().uniform(math.log(fmin), math.log(fmax)))
        tt = R().uniform(*tau)
        m = int(min(n - N(t0), tt * 6 * SR))
        if m < 8:
            continue
        s = N(t0)
        tm = np.arange(m) / SR
        y[s: s + m] += np.sin(TAU * f * tm) * np.exp(-tm / tt) * R().uniform(0.3, 1.0) * g
    return y


def servo(dur, f0, f1, level=1.0, tick=28.0, q=3.0):
    n = N(dur)
    f = ep(n, [(0, f0), (dur, f1)], log=False) if not isinstance(f0, np.ndarray) else f0
    s = saw(f, n) * 0.6 + square(f * 2.0, n) * 0.3
    s = sweep(s, "bp", f * 3.0, q)
    s = s * (0.75 + 0.25 * np.sin(TAU * tick * T(n)))
    nz = sweep(wn(n), "bp", f * 5.0, 1.5) * 0.25
    return (s + nz) * level


def metal(freq, dur, decay=0.12, n_part=6, spread=1.0):
    """Inharmonic clang (plate/pipe)."""
    n = N(dur)
    t = T(n)
    y = np.zeros(n)
    ratios = [1.0, 1.59 * spread, 2.14 * spread, 2.30, 2.65 * spread, 3.16, 3.98, 4.8][:n_part]
    for i, r_ in enumerate(ratios):
        f = freq * r_ * R().uniform(0.99, 1.01)
        y += np.sin(TAU * f * t + R().uniform(0, TAU)) * np.exp(-t / (decay / (1 + 0.45 * i))) / (1 + 0.3 * i)
    return y * (1 - np.exp(-t / 0.0004))


def debris(dur, density=60, fmin=300.0, fmax=3500.0, tau=(0.006, 0.03), decay=None, g=1.0):
    n = N(dur)
    y = np.zeros(n)
    for _ in range(int(density)):
        u = R().random() ** 1.8  # denser at the start
        t0 = u * dur
        m = N(R().uniform(*tau) * 5)
        if N(t0) + m >= n:
            continue
        tm = np.arange(m) / SR
        tau_ = R().uniform(*tau)
        g_ = np.exp(-t0 / decay) if decay else 1.0
        y[N(t0): N(t0) + m] += bq(wn(m) * np.exp(-tm / tau_), "bp", math.exp(R().uniform(math.log(fmin), math.log(fmax))), 1.2) * g_ * R().uniform(0.3, 1) * g
    return y


def rumble(dur, fc=120.0, tau=None, g=1.0):
    n = N(dur)
    y = bandnoise(n, 15, fc, tilt=0.0, edge=2) * g
    if tau:
        y = y * np.exp(-T(n) / tau)
    return y


# ---- vocal / choir formants -----------------------------------------------------------------------------------------
# (freq Hz, bandwidth Hz, level dB)
VOWELS = {
    ("bass", "a"): [(600, 60, 0), (1040, 70, -7), (2250, 110, -9), (2450, 120, -9), (2750, 130, -20)],
    ("bass", "o"): [(400, 40, 0), (750, 80, -11), (2400, 100, -21), (2600, 120, -20), (2900, 120, -40)],
    ("bass", "u"): [(350, 40, 0), (600, 80, -20), (2400, 100, -32), (2675, 120, -28), (2950, 120, -36)],
    ("tenor", "a"): [(650, 80, 0), (1080, 90, -6), (2650, 120, -7), (2900, 130, -8), (3250, 140, -22)],
    ("tenor", "o"): [(400, 70, 0), (800, 80, -10), (2600, 100, -12), (2800, 120, -12), (3000, 120, -26)],
    ("tenor", "e"): [(400, 70, 0), (1700, 80, -14), (2600, 100, -12), (3200, 120, -14), (3580, 120, -20)],
    ("tenor", "i"): [(290, 40, 0), (1870, 90, -15), (2800, 100, -18), (3250, 120, -20), (3540, 120, -30)],
    ("tenor", "u"): [(350, 40, 0), (600, 60, -20), (2700, 100, -17), (2900, 120, -14), (3300, 120, -26)],
    ("alto", "a"): [(800, 80, 0), (1150, 90, -4), (2800, 120, -20), (3500, 130, -36), (4950, 140, -60)],
    ("alto", "o"): [(450, 70, 0), (800, 80, -9), (2830, 100, -16), (3500, 130, -28), (4950, 135, -55)],
    ("alto", "e"): [(400, 60, 0), (1600, 80, -24), (2700, 120, -30), (3300, 150, -35), (4950, 200, -60)],
    ("alto", "u"): [(325, 50, 0), (700, 60, -12), (2530, 170, -30), (3500, 180, -40), (4950, 200, -64)],
    ("soprano", "a"): [(800, 80, 0), (1150, 90, -6), (2900, 120, -32), (3900, 130, -20), (4950, 140, -50)],
    ("soprano", "o"): [(450, 70, 0), (800, 80, -11), (2830, 100, -22), (3800, 130, -22), (4950, 135, -50)],
    ("soprano", "u"): [(325, 50, 0), (700, 60, -16), (2700, 170, -35), (3800, 180, -40), (4950, 200, -60)],
}


def formant(x, vowel="a", voice="tenor", shift=1.0, bwmul=1.0):
    """Parallel formant filter bank (vowel synthesis). shift scales all formant freqs (size of the 'throat')."""
    y = np.zeros(len(x))
    for f, bw, lv in VOWELS[(voice, vowel)]:
        fc = f * shift
        if fc > SR * 0.42:
            continue
        y += bp(x, fc, max(0.8, fc / (bw * bwmul))) * db(lv)
    return y


def formant_morph(x, v0, v1, w, voice="tenor", shift=1.0):
    """Crossfade between two vowels over time (w array 0..1)."""
    def vv(v):
        # accepts "a", ("a", "alto") or ("alto", "a")  ->  (vowel, voice)
        if not isinstance(v, tuple):
            return v, voice
        p, q = v
        return (q, p) if p in ("bass", "tenor", "alto", "soprano") else (p, q)
    va, vb = vv(v0), vv(v1)
    a = formant(x, va[0], va[1], shift)
    b = formant(x, vb[0], vb[1], shift)
    w = _arr(w, len(x))
    return a * (1 - w) + b * w


def choir_voice(freq, dur, vowel="a", voice="alto", unison=4, detune=9.0, vib_rate=5.3, vib_depth=0.0045,
                a=0.35, r=0.5, breath=0.05, shift=1.0, vowel2=None, morph=None):
    """One choir 'singer': detuned saw stack + vibrato -> formants (+ breath). dur includes the release tail."""
    n = N(dur)
    t = T(n)
    f = _arr(freq, n)
    vib = 1 + vib_depth * np.sin(TAU * vib_rate * t + R().uniform(0, TAU)) * np.minimum(t / 0.5, 1.0)
    vib = vib * (1 + 0.0015 * lp(R().standard_normal(n), 6))  # slow random pitch drift
    src = np.zeros(n)
    for i in range(unison):
        c = (i - (unison - 1) / 2.0) * detune / max(unison - 1, 1) * 2 / 2.0
        src += saw(f * vib * 2 ** (c / 1200.0), n, ph0=R().random())
    src = lp(src / unison, 5200) + breath * lp(wn(n), 7000)
    if vowel2 is None:
        y = formant(src, vowel, voice, shift)
    else:
        w = morph if morph is not None else np.linspace(0, 1, n)
        y = formant_morph(src, (vowel, voice), (vowel2, voice), w, voice, shift)
    return y * adsr(n, a, 0.2, 0.9, r, k=2.0) * 0.35


def glottal_roar(dur, f0, vow0=("bass", "a"), vow1=("bass", "o"), w=None, growl=45.0, growl_depth=0.6, jitter=0.03,
                 drive=3.0, breath=0.2, sub=0.5, shift=1.0):
    """Monster roar: jittery saw (+ sub-octave) -> AM growl -> morphing formants -> saturation."""
    n = N(dur)
    t = T(n)
    f = _arr(f0, n) * (1 + jitter * lp(R().standard_normal(n) * 6, 90))
    src = saw(f, n) + sub * square(f * 0.5, n)
    gr = 1 - growl_depth * (0.5 + 0.5 * np.sin(TAU * _arr(growl, n).cumsum() / SR))
    src = src * gr
    w = _arr(0.0 if w is None else w, n)
    voiced = formant_morph(src, vow0, vow1, w, shift=shift)
    air = formant_morph(wn(n) * 0.5, vow0, vow1, w, shift=shift) * breath
    y = sat(voiced + air, drive)
    return y


# ---- misc small synth ------------------------------------------------------------------------------------------------
def beep(freq, dur, kind="sq", att=0.003, rel=0.012, cut=None):
    n = N(dur)
    if kind == "sq":
        y = square(freq, n)
        y = lp(y, cut or freq * 5)
    elif kind == "sine":
        y = sine(freq, n)
    elif kind == "tri":
        y = tri(freq, n)
    else:
        y = saw(freq, n)
        y = lp(y, cut or freq * 5)
    e = np.minimum(np.minimum(np.arange(n) / max(N(att), 1), 1.0), np.clip((n - np.arange(n)) / max(N(rel), 1), 0, 1))
    return y * e


def pad_tone(freqs, dur, a=0.3, r=0.5, detune=7.0, voices=3, cutoff=2500.0, kind="saw"):
    n = N(dur)
    y = np.zeros(n)
    for f in np.atleast_1d(freqs):
        for i in range(voices):
            c = (i - (voices - 1) / 2.0) * detune
            ff = f * 2 ** (c / 1200.0)
            y += saw(ff, n, R().random()) if kind == "saw" else sine(ff, n, R().random())
    y = lp(y / max(len(np.atleast_1d(freqs)) * voices, 1) * 2.0, cutoff)
    return y * adsr(n, a, 0.2, 0.85, r, k=2.0)


# =====================================================================================================================
# registry of generators
# =====================================================================================================================
SFX = {}      # name -> (fn, target_duration_s)
MUSIC = {}    # name -> fn   (returns (2, n) float array, loop-seamless)


def sfx(name, dur):
    def deco(fn):
        SFX[name] = (fn, dur)
        return fn
    return deco


def music(name):
    def deco(fn):
        MUSIC[name] = fn
        return fn
    return deco


def finish_sfx(x, dur, peak_db=SFX_PEAK_DB):
    """DC-remove, fit to the target length, soft fades, peak-normalise."""
    x = np.asarray(x, dtype=float)
    x = np.nan_to_num(x)
    x = dcblock(x)
    n = N(dur)
    if len(x) < n:
        x = np.concatenate([x, np.zeros(n - len(x))])
    x = x[:n]
    fo = min(0.08, max(0.012, dur * 0.05))
    x = fade(x, 0.0015, fo)
    p = np.max(np.abs(x))
    if p < 1e-6:
        raise RuntimeError("silent sound")
    return (x * (db(peak_db) / p)).astype(np.float32)


# =====================================================================================================================
# 3) SFX
# =====================================================================================================================
# ---- delivery box / keycards / door ---------------------------------------------------------------------------------
@sfx("box_charge", 1.6)
def _box_charge():
    d = 1.6
    n = N(d)
    f = ep(n, [(0, 62), (0.9, 150), (d, 520)], log=True)
    core = saw(f, n) * 0.55 + square(f * 1.003, n) * 0.3 + sine(f * 0.5, n) * 0.5
    core = sweep(core, "lp", ep(n, [(0, 260), (d, 6500)], log=True), 1.4)
    buzz = sum(sine(100.0 * k, n) / k for k in (1, 2, 3, 4, 6)) * ep(n, [(0, 0.1), (d, 0.45)])
    arc = crackle(d, ep(n, [(0, 10), (d, 420)]), fc=4800, q=1.5) * ep(n, [(0, 0.2), (d, 0.9)])
    spk = sparkle(d, ep(n, [(0, 6), (d, 70)]), 2500, 10000, tau=(0.01, 0.05), g=0.5)
    shim = fm(ep(n, [(0, 900), (d, 3200)], log=True), 2.01, ep(n, [(0, 0.5), (d, 3)]), n) * 0.15 * ep(n, [(0, 0), (0.4, 0.1), (d, 1)])
    y = core * ep(n, [(0, 0.0), (0.25, 0.35), (d, 1.0)]) + buzz * 0.3 + arc * 0.35 + spk * 0.45 + shim
    y = sat(y * 0.8, 1.6)
    y *= ep(n, [(0, 0), (0.05, 1), (d - 0.05, 1), (d, 0.6)])
    return reverb(y, 0.5, 0.18)


@sfx("box_open", 1.8)
def _box_open():
    d = 1.8
    n = N(d)
    y = np.zeros(n)
    # latch clunk (two-stage: catch + release)
    put(y, thump(0.25, 190, 70, 0.025, 0.07, 1.4, 0.0), 0.0, 1.0)
    put(y, metal(1700, 0.18, 0.05, 5), 0.0, 0.35)
    put(y, hit_noise(0.03, 3500, 1.0, 0.004), 0.0, 0.8)
    put(y, hit_noise(0.04, 1800, 2.0, 0.006), 0.09, 0.5)
    put(y, metal(950, 0.2, 0.04, 4), 0.09, 0.2)
    # steam hiss
    h = hiss(1.0, 1800, 12000, att=0.04, tau=0.35)
    h = sweep(h, "lp", ep(N(1.0), [(0, 9000), (1.0, 3000)], log=True), 0.8)
    put(y, h, 0.1, 0.55)
    put(y, whoosh(0.5, 300, 1500, 1.0, 0.3), 0.08, 0.25)
    # shimmering glass-chime arpeggio + airy pad
    sh = np.zeros(n)
    for i, nt in enumerate(["E5", "G5", "B5", "E6", "G6", "B6"]):
        put(sh, bell(fr(nt), 1.2, 0.45, GLASS_P), 0.28 + i * 0.09, 0.5 - 0.04 * i)
    put(sh, pad_tone([fr("E4"), fr("B4"), fr("G5")], 1.4, a=0.3, r=0.8, cutoff=3000), 0.3, 0.12)
    put(sh, sparkle(1.3, 25, 3500, 11000, g=0.35), 0.4, 1.0)
    sh = chorus(sh)
    put(y, reverb(sh, 1.1, 0.5, dry=0.8), 0.0, 0.75)
    return y


@sfx("box_burst", 1.2)
def _box_burst():
    d = 1.2
    n = N(d)
    y = np.zeros(n)
    put(y, hiss(0.25, 3000, 14000, att=0.003, tau=0.07), 0, 0.5)
    put(y, whoosh(0.18, 600, 7000, 1.0, 0.7), 0, 0.3)
    put(y, thump(0.2, 220, 90, 0.02, 0.06, 1.3, 0.0), 0, 0.35)
    sc = ["C6", "D6", "E6", "G6", "A6", "C7", "D7", "E7", "G7"]
    for i in range(18):
        t0 = (i / 18.0) ** 1.5 * 0.55
        f = fr(sc[R().integers(0, len(sc))])
        put(y, bell(f, 0.8, 0.35, GLASS_P), t0, R().uniform(0.2, 0.55))
    for i, nt in enumerate(["C6", "E6", "G6", "C7", "E7", "G7", "C8"]):
        put(y, bell(fr(nt), 0.9, 0.4, CRYSTAL_P), 0.02 + i * 0.035, 0.35)
    put(y, sparkle(1.0, 60, 3000, 12000, g=0.4, t_env=[1, 0.5, 0.1]), 0.1, 1.0)
    return reverb(y, 1.0, 0.45, dry=0.85)


@sfx("card_whoosh", 0.8)
def _card_whoosh():
    d = 0.8
    n = N(d)
    w1 = whoosh(d, 500, 7000, 2.5, 0.62, 1.2)
    w2 = hp(whoosh(d, 900, 9000, 1.2, 0.68), 2000) * 0.5
    tone = sine(ep(n, [(0, 500), (d, 2800)], log=True), n) * 0.12 * bump(n, 0.7)
    sp = sparkle(0.5, 40, 4000, 12000, g=0.25)
    y = w1 + w2 + tone
    put(y, sp, 0.3, 1.0)
    return reverb(y, 0.4, 0.15)


@sfx("keycard_pickup", 0.7)
def _keycard_pickup():
    d = 0.7
    n = N(d)
    y = np.zeros(n)
    put(y, bell(fr("E6"), 0.6, 0.3, GLASS_P), 0.0, 0.8)
    put(y, bell(fr("B6"), 0.55, 0.28, GLASS_P), 0.11, 0.85)
    put(y, fmtone(fr("E5"), 0.2, 2.0, 1.5, 0.08, 0.05), 0.0, 0.3)
    put(y, fmtone(fr("B5"), 0.2, 2.0, 1.5, 0.08, 0.05), 0.11, 0.3)
    put(y, sparkle(0.5, 70, 4000, 12000, g=0.4), 0.12, 1.0)
    return reverb(y, 0.5, 0.25)


@sfx("door_unlock", 1.3)
def _door_unlock():
    d = 1.3
    n = N(d)
    y = np.zeros(n)
    put(y, beep(1200, 0.07, "sq", cut=6000), 0.0, 0.5)
    put(y, beep(1800, 0.09, "sq", cut=8000), 0.11, 0.5)
    put(y, beep(2400, 0.06, "sine"), 0.11, 0.15)
    f = ep(N(0.65), [(0, 200), (0.3, 640), (0.65, 330)])
    put(y, servo(0.65, f, 0.0, 0.5, tick=34.0), 0.28, 0.45)
    put(y, thump(0.4, 120, 42, 0.03, 0.14, 2.0, 0.2), 0.95, 1.0)
    put(y, metal(420, 0.5, 0.14, 7), 0.95, 0.5)
    put(y, metal(1100, 0.25, 0.05, 5), 0.95, 0.3)
    put(y, hit_noise(0.05, 2500, 1.0, 0.006), 0.95, 0.6)
    return reverb(y, 0.35, 0.18)


def _pneumatic(opening):
    d = 1.3 if opening else 1.1
    n = N(d)
    y = np.zeros(n)
    put(y, hiss(0.4, 1500, 12000, att=0.005, tau=0.12), 0.0, 0.7)
    sl = 0.9 if opening else 0.75
    m = N(sl)
    if opening:
        f = ep(m, [(0, 140), (0.25, 300), (0.7, 280), (sl, 170)])
    else:
        f = ep(m, [(0, 170), (0.15, 300), (0.55, 290), (sl, 130)])
    t0 = 0.12 if opening else 0.05
    put(y, servo(sl, f, 0.0, 0.5, tick=22.0, q=2.0) * bump(m, 0.3, 0.8), t0, 0.5)
    slide = sweep(bandnoise(m, 40, 1800, tilt=0.4), "lp", ep(m, [(0, 500), (0.4, 1200), (sl, 300)]), 0.8) * bump(m, 0.4, 1.0)
    put(y, slide, t0, 0.55)
    tstop = t0 + sl - 0.02
    if opening:
        put(y, thump(0.25, 130, 60, 0.02, 0.06, 1.3, 0.2), tstop, 0.55)
        put(y, hiss(0.25, 2000, 9000, att=0.01, tau=0.07), tstop, 0.3)
    else:
        put(y, thump(0.35, 100, 38, 0.03, 0.1, 1.8, 0.35), tstop, 1.0)
        put(y, metal(300, 0.25, 0.06, 5), tstop, 0.25)
        put(y, hiss(0.25, 2000, 9000, att=0.01, tau=0.07), tstop + 0.02, 0.3)
    return reverb(y, 0.35, 0.15)


@sfx("door_open", 1.3)
def _door_open():
    return _pneumatic(True)


@sfx("door_close", 1.1)
def _door_close():
    return _pneumatic(False)


@sfx("door_denied", 0.8)
def _door_denied():
    d = 0.8
    n = N(d)
    y = np.zeros(n)
    for t0 in (0.0, 0.34):
        m = N(0.28)
        f = ep(m, [(0, 118), (0.28, 104)])
        s = saw(f, m) * 0.6 + square(f * 1.0594, m) * 0.5 + square(f * 0.5, m) * 0.4
        s = sat(lp(s, 1800), 3.0) * adsr(m, 0.004, 0.05, 0.9, 0.05)
        s += hit_noise(0.04, 900, 1.0, 0.01)[: m] * 0.0 if False else 0
        put(y, s, t0, 1.0)
        put(y, bp(wn(m), 400, 1.0) * adsr(m, 0.003, 0.05, 0.8, 0.06), t0, 0.25)
    return reverb(y, 0.3, 0.12)


# ---- PC setup -------------------------------------------------------------------------------------------------------
@sfx("monitor_boot", 2.2)
def _monitor_boot():
    d = 2.2
    n = N(d)
    y = np.zeros(n)
    m = N(0.8)
    tm = T(m)
    dg = sat(sine(55, m) * np.exp(-tm / 0.22) * (0.6 + 0.4 * np.sin(TAU * (22 * np.exp(-tm / 0.3)) * tm)), 2.0)
    put(y, dg, 0.0, 0.7)
    put(y, thump(0.4, 110, 38, 0.04, 0.2, 2.0, 0.5), 0.0, 0.9)
    put(y, crackle(0.6, ep(N(0.6), [(0, 400), (0.6, 30)]), fc=3500, q=1.0), 0.02, 0.5)
    whine = sine(ep(n, [(0, 2500), (1.1, 9500), (d, 9000)]), n) * ep(n, [(0, 0), (0.3, 0.02), (1.1, 0.07), (d, 0.0)])
    whine += sine(ep(n, [(0, 1250), (1.1, 4750), (d, 4500)]), n) * 0.03
    y += whine
    for i, f in enumerate([520, 700, 930, 1240, 1650, 2200]):
        put(y, beep(f, 0.085, "sq", cut=5200, att=0.004, rel=0.02), 0.55 + i * 0.1, 0.22)
    chord = np.zeros(n)
    for nt in ["E5", "B5", "E6"]:
        put(chord, bell(fr(nt), 0.7, 0.4, GLASS_P), 1.62, 0.45)
        put(chord, fmtone(fr(nt), 0.5, 2.0, 1.2, 0.2, 0.06), 1.62, 0.12)
    put(y, chord, 0.0, 1.0)
    return reverb(y, 0.5, 0.18)


@sfx("monitor_off", 0.7)
def _monitor_off():
    d = 0.7
    n = N(d)
    t = T(n)
    y = sine(ep(n, [(0, 1400), (0.22, 70), (d, 70)], log=True), n) * np.exp(-t / 0.1) * 0.8
    y += whoosh(0.3, 6000, 200, 1.5, 0.3, 1.0) * 0.5 * (np.arange(n) < N(0.3)).astype(float) if False else 0
    w = whoosh(0.3, 6000, 200, 1.5, 0.3, 1.0)
    put(y, w, 0.0, 0.5)
    wh = sine(ep(n, [(0, 6000), (0.35, 2500), (d, 2500)]), n) * ep(n, [(0, 0.1), (0.3, 0.06), (0.36, 0.0), (d, 0)])
    y += wh
    put(y, thump(0.2, 160, 50, 0.02, 0.05, 1.5, 0.4), 0.24, 0.55)
    put(y, beep(2400, 0.006, "sine"), 0.33, 0.25)
    return y


@sfx("key_click", 0.12)
def _key_click():
    n = N(0.12)
    t = T(n)
    y = hit_noise(0.03, 2600, 1.4, 0.003)[:n] if False else np.zeros(n)
    put(y, hit_noise(0.03, 2600, 1.4, 0.003), 0.0, 1.0)
    y += (np.sin(TAU * 780 * t) * np.exp(-t / 0.012) * 0.6 + np.sin(TAU * 1450 * t) * np.exp(-t / 0.008) * 0.3)
    put(y, thump(0.05, 220, 110, 0.01, 0.015, 1.0, 0.0), 0.0, 0.5)
    put(y, np.sin(TAU * 4300 * t) * np.exp(-t / 0.02) * 0.12, 0.012, 1.0)
    put(y, hit_noise(0.02, 3500, 1.4, 0.002), 0.07, 0.3)
    return y


@sfx("mouse_click", 0.1)
def _mouse_click():
    n = N(0.1)
    t = T(n)
    y = np.zeros(n)
    put(y, hit_noise(0.02, 4000, 1.2, 0.0025), 0.0, 1.0)
    put(y, np.sin(TAU * 2100 * t) * np.exp(-t / 0.006) * 0.7, 0.0, 1.0)
    put(y, thump(0.03, 300, 160, 0.006, 0.008, 1.0, 0.0), 0.0, 0.3)
    put(y, hit_noise(0.015, 5500, 1.2, 0.0018), 0.055, 0.5)
    put(y, np.sin(TAU * 3200 * t) * np.exp(-t / 0.004) * 0.4, 0.055, 1.0)
    return y


@sfx("order_confirm", 1.2)
def _order_confirm():
    d = 1.2
    n = N(d)
    y = np.zeros(n)
    put(y, hit_noise(0.05, 1200, 0.9, 0.01), 0.0, 0.5)
    put(y, thump(0.15, 200, 90, 0.02, 0.05, 1.5, 0.2), 0.0, 0.6)
    for i, nt in enumerate(["C6", "E6", "G6", "C7", "E7"]):
        put(y, fmtone(fr(nt), 0.4, 3.5, 1.6, 0.16, 0.05), 0.07 + i * 0.075, 0.45)
    put(y, bell(fr("C7"), 0.9, 0.45, BELL_P), 0.45, 0.6)
    put(y, bell(fr("G6"), 0.8, 0.4, GLASS_P), 0.45, 0.3)
    put(y, metal(3300, 0.35, 0.09, 5), 0.45, 0.14)
    put(y, sparkle(0.7, 40, 4000, 12000, g=0.3), 0.5, 1.0)
    return reverb(y, 0.6, 0.2)


@sfx("delivery_drop", 1.2)
def _delivery_drop():
    d = 1.2
    n = N(d)
    m = N(0.9)
    f = ep(m, [(0, 245), (0.6, 232), (0.9, 190)])
    dr = sum(saw(f * k, m, R().random()) * a for k, a in ((1.0, 1.0), (1.51, 0.5), (2.0, 0.6), (3.02, 0.3)))
    dr = lp(dr, 1700) * (0.6 + 0.4 * np.sin(TAU * 14.0 * T(m)))
    nz = sweep(wn(m), "bp", ep(m, [(0, 1800), (0.9, 900)]), 0.9) * 0.8
    env = ep(m, [(0, 0.0), (0.7, 1.0), (0.9, 0.0)]) ** 1.3
    y = np.zeros(n)
    put(y, (dr * 0.7 + nz * 0.5) * env, 0.0, 1.0)
    put(y, thump(0.3, 105, 55, 0.03, 0.09, 1.3, 0.3), 0.82, 1.0)
    put(y, lp(hit_noise(0.14, 900, 0.8, 0.03), 1400), 0.82, 0.9)
    put(y, hit_noise(0.05, 1800, 1.0, 0.012), 0.87, 0.4)
    put(y, hit_noise(0.04, 2400, 1.2, 0.01), 0.93, 0.25)
    return reverb(y, 0.4, 0.15)


# ---- omega terminal -------------------------------------------------------------------------------------------------
@sfx("terminal_insert", 1.0)
def _terminal_insert():
    d = 1.0
    n = N(d)
    y = np.zeros(n)
    m = N(0.45)
    fric = sweep(wn(m), "bp", ep(m, [(0, 1200), (0.45, 3000)], log=True), 2.0) * (0.55 + 0.45 * sine(87.0, m))
    fric += sweep(wn(m), "lp", 600, 0.8) * 0.4
    put(y, fric * bump(m, 0.5, 0.8), 0.0, 0.55)
    put(y, hit_noise(0.04, 2600, 1.2, 0.006), 0.46, 0.7)
    put(y, thump(0.15, 150, 70, 0.02, 0.04, 1.2, 0.0), 0.46, 0.6)
    put(y, metal(2100, 0.2, 0.04, 4), 0.46, 0.25)
    put(y, beep(1320, 0.09, "sq", cut=5000), 0.62, 0.4)
    put(y, beep(1760, 0.12, "sq", cut=6000), 0.73, 0.4)
    return reverb(y, 0.4, 0.15)


@sfx("terminal_activate", 3.5)
def _terminal_activate():
    d = 3.5
    n = N(d)
    tp = 2.6
    f = ep(n, [(0, 28), (tp, 110), (d, 110)], log=True)
    sub = sine(f, n) * ep(n, [(0, 0.1), (tp, 1.0), (d, 0.4)])
    saws = saw(f * 2, n) + saw(f * 2.007, n) * 0.8 + saw(f * 3.01, n) * 0.5
    saws = sweep(saws, "lp", ep(n, [(0, 150), (tp, 7000), (d, 2500)], log=True), 1.3) * ep(n, [(0, 0.0), (1.0, 0.2), (tp, 0.9), (d, 0.3)])
    rt = ep(n, [(0, 1.5), (tp, 18.0), (d, 18.0)])
    trem = 0.6 + 0.4 * np.sin(TAU * np.cumsum(rt) / SR)
    rs = sweep(wn(n), "bp", ep(n, [(0, 200), (tp, 8000), (d, 3000)], log=True), ep(n, [(0, 1.0), (tp, 4.0), (d, 1.0)])) * ep(n, [(0, 0.0), (tp, 0.8), (d, 0.0)]) ** 1.5
    arcs = crackle(d, ep(n, [(0, 5), (tp, 500), (d, 60)]), fc=4500, q=1.2) * ep(n, [(0, 0.1), (tp, 0.8), (d, 0.3)])
    y = (sub * 0.9 + saws * 0.5) * (0.65 + 0.35 * trem) + rs * 0.4 + arcs * 0.35
    # impact
    tt = tp + 0.02
    put(y, thump(0.9, 90, 28, 0.08, 0.5, 2.0, 0.5), tt, 1.4)
    put(y, hit_noise(0.5, 400, 0.6, 0.12) * 1.0, tt, 0.9)
    put(y, metal(180, 0.9, 0.25, 7), tt, 0.4)
    # ringing hum + shimmer tail
    m = N(d - tt)
    hum = sum(sine(55.0 * k, m, R().random()) / k for k in (1, 2, 3, 4, 5)) * np.exp(-T(m) / 0.5)
    put(y, hum, tt, 0.45)
    put(y, sparkle(d - tt, 45, 2500, 11000, g=0.35, t_env=[1, 0.5, 0.1]), tt, 1.0)
    y = sat(y * 0.8, 1.7)
    return reverb(y, 1.3, 0.3, dry=0.9)


@sfx("terminal_hum", 3.0)
def _terminal_hum():
    d = 3.0
    n = N(d)
    t = T(n)

    def pf(fq):
        return round(fq * d) / d

    amps = {1: 1.0, 2: 0.7, 3: 0.5, 4: 0.25, 5: 0.3, 6: 0.15, 7: 0.14, 8: 0.08}
    mains = sum(sine(pf(50.0 * k), n, ph0=0.13 * k) * a for k, a in amps.items())
    sub = sine(pf(27.5), n) * 0.8 + sine(pf(41.3), n) * 0.5
    beat = sine(pf(55.0), n) * 0.5 + sine(pf(55.0) + 1.0 / d, n) * 0.5
    am = 1 + 0.22 * np.sin(TAU * (2.0 / d) * t)
    bands = bandnoise(n, 60, 900, tilt=0.8) * 0.18 * (0.65 + 0.35 * np.sin(TAU * (1.0 / d) * t + 1.0))
    y = (mains * 0.4 + sub * 0.5 + beat * 0.35) * am + bands
    y = lp(sat(y * 0.5, 1.5), 3500)
    sp = crackle(d, 5.0, fc=5200, q=1.2, amp=0.25)
    sp *= ((t > 0.25) & (t < d - 0.25)).astype(float)
    return y + sp * 0.15


# ---- cutscene stingers ----------------------------------------------------------------------------------------------
@sfx("cutscene_riser", 4.0)
def _cutscene_riser():
    d = 4.0
    n = N(d)
    ramp = ep(n, [(0, 0.0), (d - 0.05, 1.0), (d, 0.9)]) ** 2.0
    ns = sweep(wn(n), "bp", ep(n, [(0, 150), (d, 11000)], log=True), ep(n, [(0, 1.0), (d, 5.0)])) * ramp
    f = ep(n, [(0, 110), (d, 880)], log=True)
    st = sum(saw(f * 2 ** (c / 1200.0), n, R().random()) for c in (-14, 0, 14)) + saw(f * 2, n) * 0.5
    st = sweep(st, "lp", ep(n, [(0, 300), (d, 9000)], log=True), 1.2) * ramp
    sn = sine(ep(n, [(0, 300), (d, 4500)], log=True), n) * ep(n, [(0, 0.1), (d, 0.35)]) * ramp ** 0.5
    rate = ep(n, [(0, 2.0), (d, 22.0)])
    am = 0.55 + 0.45 * np.sin(TAU * np.cumsum(rate) / SR)
    y = (ns * 0.55 + st * 0.22) * am + sn * 0.3
    y += sparkle(1.5, ep(N(1.5), [(0, 5), (1.5, 80)]), 3000, 11000, g=0.25) * 0 if False else 0
    put(y, sparkle(1.5, 50, 3000, 11000, g=0.25), d - 1.55, 1.0)
    y += sine(ep(n, [(0, 40), (d, 90)], log=True), n) * ramp * 0.4
    return reverb(y, 0.8, 0.15)


@sfx("cutscene_boom", 2.5)
def _cutscene_boom():
    d = 2.5
    n = N(d)
    y = np.zeros(n)
    put(y, thump(2.0, 95, 32, 0.12, 0.9, 2.2, 0.4), 0.0, 1.0)
    t = T(n)
    put(y, sine(ep(n, [(0, 46), (1.0, 30), (d, 28)], log=True), n) * np.exp(-t / 1.3), 0.0, 0.7)
    put(y, lpf(wn(n), 500, 4) * np.exp(-t / 0.5), 0.0, 0.7)
    put(y, hit_noise(0.2, 2500, 0.8, 0.03), 0.0, 0.5)
    put(y, bell(110.0, 2.2, 0.8, GONG_P), 0.0, 0.14)
    return reverb(y, 2.2, 0.6, dry=0.8, damp=0.6)


@sfx("cutscene_glitch", 0.8)
def _cutscene_glitch():
    d = 0.8
    n = N(d)
    y = np.zeros(n)
    t0 = 0.0
    while t0 < d - 0.05:
        L = R().uniform(0.02, 0.06)
        f = float(R().choice([220, 330, 440, 660, 880, 1320, 1760, 2640, 3520]))
        m = N(L)
        kind = R().integers(0, 3)
        s = (square(f, m) if kind == 0 else saw(f, m) if kind == 1 else crush(wn(m), 3, 6))
        put(y, s * adsr(m, 0.001, 0.01, 0.8, 0.004), t0, R().uniform(0.25, 0.6))
        t0 += L
    nz = crush(wn(n), 4, 9) * (R().random(n) < 0.0) if False else crush(wn(n), 4, 9)
    gate = np.zeros(n)
    for _ in range(9):
        s = N(R().uniform(0, d - 0.06))
        gate[s: s + N(R().uniform(0.008, 0.04))] = 1.0
    y += nz * gate * 0.45
    y = ringmod(y, 180.0) * 0.5 + y * 0.8
    y = glitchify(y, 0.0, d, events=14, amount=1.0, seed_off=3)
    y *= ep(n, [(0, 1.0), (d - 0.15, 1.0), (d, 0.0)])
    return y


# ---- composite voices used by the boss sounds -----------------------------------------------------------------------
def varispeed(x, rate):
    """Tape-style speed change (rate scalar/array, 1 = normal). Pitch follows speed."""
    n = len(x)
    pos = np.cumsum(_arr(rate, n)) - _arr(rate, n)
    pos = np.clip(pos, 0, n - 1)
    return np.interp(pos, np.arange(n), x)


def reverb_loop(x, **kw):
    """Reverb whose tail wraps around: makes a periodic input stay periodic (seamless-loop friendly)."""
    n = len(x)
    return reverb(np.tile(x, 3), **kw)[n: 2 * n]


def reverse(x):
    return x[::-1].copy()


def cyber_roar(dur, f0, w=None, drive=4.0, bits=9, down=3.0, ring=130.0, growl=52.0, shift=1.0, voice="bass"):
    """Distorted digital roar: glottal source + vowel formants + saturation + bit-crush + robotic ring-mod."""
    y = glottal_roar(dur, f0, (voice, "a"), (voice, "o"), w=w, growl=growl, growl_depth=0.55, drive=drive, breath=0.25, shift=shift)
    y = crush(y, bits, down)
    return ringmod(y, ring) * 0.45 + y * 0.75


# ---- boss 1: Cipher Warden -----------------------------------------------------------------------------------------
@sfx("boss1_ambient", 4.0)
def _boss1_ambient():
    d = 4.0
    n = N(d)
    t = T(n)

    def pf(fq):
        return round(fq * d) / d

    base = saw(pf(55.0), n) * 0.5 + saw(pf(55.25), n) * 0.5
    base = sweep(base, "lp", 330 + 130 * np.sin(TAU * t / d), 1.4)
    sub = sine(pf(27.5), n) * 0.9
    fifth = lp(square(pf(82.5), n) * 0.2, 600)
    flut = bandnoise(n, 800, 6000, tilt=0.3) * 0.06 * (0.5 + 0.5 * np.sin(TAU * 3.0 * t / d + 2.0))
    y = (base * 0.6 + sub + fifth) * (0.85 + 0.15 * np.sin(TAU * t / d * 2.0))
    ticks = np.zeros(n)
    for _ in range(14):
        put(ticks, bp(wn(N(0.01)) * np.exp(-T(N(0.01)) / 0.003), math.exp(R().uniform(math.log(2500), math.log(7000))), 3.0), R().uniform(0.1, d - 0.2), R().uniform(0.2, 0.5))
    y = y * 0.5 + flut + ticks * 0.15
    return reverb_loop(y, rt60=0.9, wet=0.18, dry=1.0)


@sfx("boss1_awaken", 3.5)
def _boss1_awaken():
    d = 3.5
    n = N(d)
    y = np.zeros(n)
    # relay clunks + groaning machine spool-up
    for t0, g in ((0.0, 1.0), (0.62, 0.7), (1.2, 0.6)):
        put(y, thump(0.35, 120, 40, 0.03, 0.12, 2.0, 0.3), t0, g)
        put(y, metal(300, 0.4, 0.1, 6), t0, 0.3 * g)
    m = N(1.9)
    gf = ep(m, [(0, 26), (1.9, 62)], log=True)
    gr = saw(gf, m) + square(gf * 2.01, m) * 0.5
    gr = formant(gr * 0.5, "o", "bass", 0.9) * ep(m, [(0, 0.0), (0.4, 0.7), (1.9, 1.0)])
    gr = sat(gr, 3.0) * 0.9
    put(y, gr, 0.0, 0.8)
    put(y, servo(1.4, ep(N(1.4), [(0, 120), (1.4, 520)]), 0.0, 0.4, tick=18.0) * bump(N(1.4), 0.7), 0.3, 0.5)
    put(y, hiss(1.0, 800, 8000, att=0.3, tau=0.8), 0.2, 0.15)
    # data glitches
    gl = np.zeros(n)
    for _ in range(26):
        t0 = R().uniform(1.0, 2.15)
        m2 = N(R().uniform(0.015, 0.05))
        f = float(R().choice([330, 440, 660, 880, 1320, 1760, 2640]))
        s = square(f, m2) if R().random() < 0.5 else crush(wn(m2), 3, 6)
        put(gl, s * adsr(m2, 0.001, 0.01, 0.8, 0.004), t0, R().uniform(0.2, 0.5))
    put(y, gl, 0.0, 0.5)
    # rising roar
    m3 = N(1.65)
    f0 = ep(m3, [(0, 52), (0.5, 80), (1.0, 150), (1.65, 105)])
    w3 = ep(m3, [(0, 0.0), (1.65, 1.0)])
    r = cyber_roar(1.65, f0, w3, drive=4.5, bits=8, down=ep(m3, [(0, 3), (1.65, 2)]), ring=95.0, growl=48.0)
    r *= ep(m3, [(0, 0.0), (0.25, 0.8), (0.9, 1.0), (1.35, 0.8), (1.65, 0.0)])
    put(y, r, 1.85, 1.0)
    put(y, thump(1.2, 85, 28, 0.08, 0.5, 2.0, 0.4), 1.85, 0.8)
    y = glitchify(y, 1.0, 3.2, events=7, amount=1.0, seed_off=1)
    return reverb(y, 1.2, 0.22)


@sfx("boss1_roar", 2.6)
def _boss1_roar():
    d = 2.6
    n = N(d)
    f0 = ep(n, [(0, 62), (0.15, 150), (0.5, 140), (1.4, 118), (d, 55)])
    f0 = f0 * (1 + 0.012 * np.sin(TAU * 6.5 * T(n)))
    w = ep(n, [(0, 0.0), (0.4, 0.2), (1.5, 0.9), (d, 1.0)])
    r = cyber_roar(d, f0, w, drive=4.5, bits=9, down=ep(n, [(0, 2), (1.0, 3), (d, 9)]), ring=130.0, growl=ep(n, [(0, 40), (d, 70)]))
    r *= ep(n, [(0, 0.0), (0.03, 1.0), (1.4, 0.9), (2.0, 0.55), (d, 0.0)])
    y = r.copy()
    put(y, thump(1.0, 90, 30, 0.07, 0.5, 2.0, 0.3), 0.0, 0.7)
    put(y, hiss(0.4, 1500, 9000, att=0.01, tau=0.12), 0.0, 0.35)
    y = glitchify(y, 0.2, 2.2, events=6, amount=1.0, seed_off=2)
    return reverb(y, 0.9, 0.2)


@sfx("boss1_hurt", 0.6)
def _boss1_hurt():
    d = 0.6
    n = N(d)
    y = np.zeros(n)
    put(y, metal(520, 0.45, 0.12, 7), 0.0, 0.8)
    put(y, metal(1480, 0.3, 0.06, 5), 0.0, 0.4)
    put(y, hit_noise(0.04, 3000, 1.0, 0.006), 0.0, 0.8)
    m = N(0.35)
    f = ep(m, [(0, 240), (0.35, 105)])
    yl = cyber_roar(0.35, f, ep(m, [(0, 0.0), (0.35, 1.0)]), drive=5.0, bits=7, down=4.0, ring=170.0, growl=60.0)
    put(y, yl * adsr(m, 0.004, 0.08, 0.6, 0.12), 0.01, 0.6)
    put(y, crush(hit_noise(0.2, 1500, 1.0, 0.04), 3, 6), 0.0, 0.5)
    y = glitchify(y, 0.05, 0.5, events=4, max_slice=25, seed_off=4)
    return reverb(y, 0.4, 0.15)


@sfx("boss1_death", 4.5)
def _boss1_death():
    d = 4.5
    n = N(d)
    ta = 3.2
    m = N(ta)
    tm = T(m)
    f0 = ep(m, [(0, 190), (0.5, 150), (ta, 22)], log=True)
    ro = glottal_roar(ta, f0, ("bass", "a"), ("bass", "o"), w=ep(m, [(0, 0.0), (ta, 1.0)]), growl=45.0, drive=4.0, breath=0.3)
    ro *= ep(m, [(0, 0.0), (0.04, 1.0), (1.8, 0.8), (ta, 0.35)])
    zaps = crackle(ta, ep(m, [(0, 30), (ta, 350)]), fc=3800, q=1.0) * 0.6
    blips = np.zeros(m)
    for _ in range(34):
        t0 = R().uniform(0.2, ta - 0.2)
        mm = N(R().uniform(0.02, 0.07))
        fb = ep(mm, [(0, R().uniform(600, 3000)), (R().uniform(0.02, 0.07), 80)], log=True)
        put(blips, square(fb, mm) * np.exp(-T(mm) / 0.03), t0, R().uniform(0.15, 0.4) * (1.0 - t0 / ta * 0.5))
    a = ro * 0.9 + zaps + blips * 0.7
    a = crush(a, 6, ep(m, [(0, 1.0), (1.0, 2.0), (ta, 34.0)]))
    a = glitchify(a, 0.5, ta - 0.1, events=22, max_slice=70, seed_off=5)
    y = np.zeros(n)
    put(y, a, 0.0, 0.9)
    put(y, sine(ep(m, [(0, 80), (ta, 20)], log=True), m) * ep(m, [(0, 0.0), (1.0, 0.4), (ta, 0.0)]), 0.0, 0.5)
    # explosion
    te = 3.2
    me = N(d - te)
    tt = T(me)
    ex = sweep(wn(me), "lp", ep(me, [(0, 9000), (1.3, 120), (d - te, 80)], log=True), 0.7) * np.exp(-tt / 0.55) * (1 - np.exp(-tt / 0.002))
    put(y, ex, te, 1.3)
    put(y, thump(1.3, 70, 24, 0.12, 0.8, 2.3, 0.4), te, 1.3)
    put(y, hit_noise(0.3, 2500, 0.7, 0.04), te, 0.7)
    put(y, debris(1.2, 90, 250, 4000, decay=0.7, g=0.5), te + 0.05, 0.8)
    put(y, sparkle(1.2, 30, 3000, 9000, g=0.3, t_env=[1, 0.3]), te + 0.2, 1.0)
    return reverb(y, 1.6, 0.3)


@sfx("boss1_slam", 1.3)
def _boss1_slam():
    d = 1.3
    n = N(d)
    y = np.zeros(n)
    put(y, thump(1.0, 85, 26, 0.06, 0.45, 2.2, 0.4), 0.0, 1.0)
    put(y, hit_noise(0.25, 3000, 0.7, 0.02), 0.0, 0.8)
    put(y, lp(hit_noise(0.3, 900, 0.7, 0.05), 1200), 0.0, 0.8)
    put(y, debris(1.0, 70, 250, 4000, decay=0.4, g=1.0), 0.03, 0.45)
    put(y, whoosh(0.7, 3500, 160, 0.9, 0.12, 1.0), 0.0, 0.55)
    put(y, bell(65.0, 1.0, 0.3, GONG_P), 0.0, 0.1)
    return reverb(y, 1.0, 0.35)


@sfx("boss1_laser", 2.2)
def _boss1_laser():
    d = 2.2
    n = N(d)
    t = T(n)
    drop = ep(n, [(0, 1.0), (d - 0.4, 1.0), (d, 0.55)])
    f0 = 196.0 * (1 + 0.012 * np.sin(TAU * 5.0 * t)) * drop
    core = saw(f0, n) + saw(f0 * 1.005, n) * 0.8
    core += fm(f0 * 2, 1.0, 2.0 + 1.5 * np.sin(TAU * 7.0 * t), n) * 0.7
    core = flanger(core, 0.002, 0.0018, 0.7, 0.8)
    core *= (0.8 + 0.2 * np.sin(TAU * 60.0 * t))
    bright = hp(saw(f0 * 4.0, n), 2500) * 0.35
    y = core + bright + sine(98.0 * drop, n) * 0.6
    y += hp(wn(n), 6000) * 0.1
    y = sat(y * 0.6, 2.5) * adsr(n, 0.06, 0.2, 0.85, 0.35, k=2.0)
    put(y, sine(ep(N(0.15), [(0, 3000), (0.15, 400)], log=True), N(0.15)) * np.exp(-T(N(0.15)) / 0.05), 0.0, 0.6)
    put(y, crackle(0.2, 250, fc=5000, q=1.2), 0.0, 0.4)
    return reverb(y, 0.5, 0.15)


@sfx("boss1_charge", 1.5)
def _boss1_charge():
    d = 1.5
    n = N(d)
    ramp = ep(n, [(0, 0.0), (d - 0.08, 1.0), (d, 0.0)]) ** 1.4
    f = ep(n, [(0, 80), (d, 1100)], log=True)
    s = saw(f, n) + square(f * 1.005, n) * 0.6
    s = sweep(s, "lp", ep(n, [(0, 200), (d, 8000)], log=True), 1.5)
    rt = ep(n, [(0, 6.0), (d, 34.0)])
    am = 0.6 + 0.4 * np.sin(TAU * np.cumsum(rt) / SR)
    y = s * am * 0.5 + sine(ep(n, [(0, 600), (d, 3500)], log=True), n) * 0.18
    y += crackle(d, ep(n, [(0, 5), (d, 320)]), fc=4200, q=1.2) * 0.45
    y *= ramp
    put(y, bell(fr("E7"), 0.18, 0.06, GLASS_P), d - 0.16, 0.3)
    return reverb(y, 0.4, 0.12)


@sfx("boss1_teleport", 0.7)
def _boss1_teleport():
    d = 0.7
    n = N(d)
    y = np.zeros(n)
    m = N(0.12)
    put(y, sine(ep(m, [(0, 2200), (0.12, 180)], log=True), m) * np.exp(-T(m) / 0.06), 0.0, 0.6)
    w = hp(whoosh(0.3, 400, 9000, 1.0, 0.8, 1.0), 1500)
    put(y, w, 0.02, 0.5)
    put(y, crush(wn(N(0.2)), 3, 7) * np.exp(-T(N(0.2)) / 0.06), 0.05, 0.45)
    put(y, ringmod(saw(ep(N(0.25), [(0, 300), (0.25, 2500)], log=True), N(0.25)), 220.0) * bump(N(0.25), 0.6), 0.1, 0.35)
    y = dropout(y, 0.3, 0.07)
    put(y, sine(ep(N(0.1), [(0, 300), (0.1, 2400)], log=True), N(0.1)) * np.exp(-T(N(0.1)) / 0.05), 0.4, 0.55)
    put(y, thump(0.15, 160, 60, 0.015, 0.04, 1.4, 0.4), 0.45, 0.5)
    put(y, bell(fr("A6"), 0.25, 0.09, GLASS_P), 0.46, 0.3)
    y = glitchify(y, 0.05, 0.6, events=5, max_slice=20, seed_off=6)
    return reverb(y, 0.4, 0.15)


@sfx("boss1_shoot", 0.4)
def _boss1_shoot():
    d = 0.4
    n = N(d)
    y = np.zeros(n)
    m = N(0.09)
    z = square(ep(m, [(0, 3200), (0.07, 260), (0.09, 250)], log=True), m)
    put(y, sat(z, 2.0) * np.exp(-T(m) / 0.04), 0.0, 0.8)
    put(y, hit_noise(0.02, 5000, 1.0, 0.002), 0.0, 0.7)
    steps = 24
    for i in range(steps):
        t0 = 0.012 + i * 0.008
        mm = N(0.008)
        f = math.exp(R().uniform(math.log(1000), math.log(5000)))
        put(y, square(f, mm) * np.exp(-T(mm) / 0.006), t0, 0.28 * (1.0 - i / steps))
    put(y, bp(wn(N(0.25)), 4200, 2.0) * np.exp(-T(N(0.25)) / 0.07), 0.05, 0.25)
    y = crush(y, 8, 2.0)
    return reverb(y, 0.3, 0.1)


@sfx("boss1_phase", 2.6)
def _boss1_phase():
    d = 2.6
    n = N(d)
    y = np.zeros(n)
    put(y, thump(1.5, 80, 28, 0.08, 0.7, 2.0, 0.5), 0.0, 1.0)
    put(y, whoosh(1.0, 6000, 150, 0.8, 0.1, 1.0), 0.0, 0.5)
    put(y, hit_noise(0.3, 2500, 0.7, 0.04), 0.0, 0.6)
    m = N(2.4)
    f0 = ep(m, [(0, 380), (0.9, 1300), (2.1, 880), (2.4, 300)])
    f0 = f0 * (1 + 0.02 * np.sin(TAU * 7.0 * T(m)))
    sh = glottal_roar(2.4, f0, ("soprano", "a"), ("soprano", "o"), w=ep(m, [(0, 0.0), (2.4, 1.0)]), growl=90.0, growl_depth=0.5, drive=5.0, breath=0.3, sub=0.2, shift=1.3)
    sh2 = glottal_roar(2.4, f0 * 1.5, ("soprano", "a"), ("soprano", "o"), w=0.5, growl=70.0, drive=4.0, breath=0.2, sub=0.0, shift=1.2)
    s = crush(sh + sh2 * 0.6, 7, 2.5)
    s = (ringmod(s, 220.0) * 0.4 + s * 0.8) * ep(m, [(0, 0.0), (0.1, 1.0), (2.0, 0.9), (2.4, 0.0)])
    put(y, s, 0.1, 0.75)
    y = glitchify(y, 0.2, 2.3, events=10, max_slice=40, seed_off=7)
    return reverb(y, 1.4, 0.3)


@sfx("boss1_step", 0.6)
def _boss1_step():
    d = 0.6
    n = N(d)
    y = np.zeros(n)
    put(y, thump(0.4, 90, 40, 0.03, 0.15, 2.0, 0.3), 0.0, 1.0)
    put(y, metal(260, 0.4, 0.1, 7), 0.0, 0.45)
    put(y, metal(780, 0.2, 0.05, 5), 0.0, 0.3)
    put(y, hiss(0.3, 2500, 10000, att=0.01, tau=0.09), 0.08, 0.3)
    m = N(0.1)
    put(y, saw(ep(m, [(0, 900), (0.1, 400)]), m) * np.exp(-T(m) / 0.05) * 0.3, 0.05, 1.0)
    put(y, hit_noise(0.02, 3000, 1.0, 0.003), 0.0, 0.5)
    return reverb(y, 0.5, 0.2)


# ---- boss 2: Omega Root --------------------------------------------------------------------------------------------
@sfx("boss2_ambient", 4.0)
def _boss2_ambient():
    d = 4.0
    n = N(d)
    t = T(n)

    def pf(fq):
        return round(fq * d) / d

    y = np.zeros(n)
    for f, a in ((73.42, 1.0), (110.0, 0.8), (146.83, 0.7), (174.61, 0.45), (220.0, 0.4)):
        for dt in (0.0, 0.25, -0.25):
            y += sine(pf(f) + dt, n, R().random()) * a * 0.33
    ch = np.zeros(n)
    for f in (146.83, 220.0, 293.66):
        ch += saw(pf(f) + 0.25, n) + saw(pf(f) - 0.25, n)
    ch = formant(lp(ch / 6.0, 3000), "o", "alto") * 0.9
    ch *= 0.7 + 0.3 * np.sin(TAU * t / d * 1.0 + 0.5)
    air = bandnoise(n, 3500, 12000, tilt=0.2) * 0.04 * (0.5 + 0.5 * np.sin(TAU * 2.0 * t / d))
    sp = np.zeros(n)
    for nt in ("D6", "A6", "F6", "D7", "A5"):
        put(sp, bell(fr(nt), 1.6, 0.7, CRYSTAL_P), R().uniform(0.0, d - 0.5), 0.05)
    y = y * 0.45 + ch * 0.6 + air + sp
    return reverb_loop(y, rt60=1.6, wet=0.45, dry=0.9, damp=0.5)


@sfx("boss2_summon", 6.0)
def _boss2_summon():
    d = 6.0
    n = N(d)
    t = T(n)
    tc = 4.75
    ramp = ep(n, [(0, 0.0), (tc, 1.0), (tc + 0.05, 0.0), (d, 0.0)]) ** 1.6
    y = np.zeros(n)
    sub = sine(ep(n, [(0, 34), (tc, 55)], log=True), n) * ramp * 0.9
    rum = bandnoise(n, 20, 140, edge=2) * ramp * 0.8
    y += sub + rum
    # fabric-ripping tear
    tear = hp(bandnoise(n, 1200, 9000, tilt=0.2), 800)
    env = lp(np.abs(R().standard_normal(n)), 25) * 6
    tear = tear * np.clip(env, 0, 2.5) ** 1.5
    tear *= ep(n, [(0, 0.0), (0.8, 0.05), (tc, 1.0), (tc + 0.03, 0.0), (d, 0.0)])
    y += tear * 0.4
    y += sweep(wn(n), "bp", ep(n, [(0, 300), (tc, 12000)], log=True), 1.5) * ramp ** 1.3 * 0.5
    # choir
    for i, (nt, vc) in enumerate((("D3", "bass"), ("A3", "tenor"), ("D4", "alto"), ("F4", "alto"), ("A4", "soprano"), ("D5", "soprano"))):
        m = N(4.9)
        v = choir_voice(fr(nt), 4.9, "a", vc, a=2.2 + 0.1 * i, r=0.7, vowel2="o", morph=np.linspace(1, 0, m) ** 1.2)
        put(y, v, 0.9, 1.1)
    # climax
    put(y, thump(1.6, 82, 24, 0.1, 0.8, 2.2, 0.4), tc, 1.5)
    put(y, hit_noise(0.4, 2600, 0.7, 0.05), tc, 0.8)
    put(y, whoosh(0.9, 7000, 150, 0.9, 0.08, 1.0), tc, 0.5)
    for i, (nt, vc) in enumerate((("D2", "bass"), ("A2", "bass"), ("D3", "tenor"), ("F3", "tenor"), ("A3", "alto"), ("D4", "alto"), ("A4", "soprano"))):
        put(y, choir_voice(fr(nt), 1.25, "a", vc, a=0.02, r=0.9), tc, 1.3)
    put(y, sparkle(1.2, 45, 3000, 12000, g=0.35, t_env=[1, 0.3]), tc + 0.05, 1.0)
    y = glitchify(y, tc - 0.5, tc, events=3, max_slice=30, seed_off=8)
    return reverb(y, 2.6, 0.38, dry=0.9)


@sfx("boss2_roar", 3.2)
def _boss2_roar():
    d = 3.2
    n = N(d)
    y = np.zeros(n)
    f0 = ep(n, [(0, 58), (0.2, 80), (1.2, 72), (d, 46)])
    w = ep(n, [(0, 0.0), (1.6, 1.0), (d, 1.0)])
    lo = glottal_roar(d, f0, ("bass", "a"), ("bass", "o"), w=w, growl=36.0, growl_depth=0.5, drive=3.0, breath=0.3)
    hi = glottal_roar(d, f0 * 2.01, ("tenor", "a"), ("tenor", "o"), w=w, growl=55.0, growl_depth=0.45, drive=2.5, breath=0.2, sub=0.0)
    env = ep(n, [(0, 0.0), (0.04, 1.0), (1.5, 0.85), (d, 0.0)])
    y += (lo * 0.9 + hi * 0.45) * env
    for i, (nt, vc) in enumerate((("D3", "bass"), ("A3", "tenor"), ("D4", "alto"), ("F4", "alto"))):
        put(y, choir_voice(fr(nt), 2.9, "a", vc, a=0.9, r=1.1, vowel2="o", morph=np.linspace(0, 1, N(2.9))), 0.3, 0.9)
    put(y, thump(1.4, 82, 26, 0.1, 0.7, 2.0, 0.4), 0.0, 0.9)
    put(y, hiss(0.6, 1200, 9000, att=0.01, tau=0.2), 0.0, 0.3)
    return reverb(y, 2.2, 0.4, dry=0.9)


@sfx("boss2_hurt", 0.7)
def _boss2_hurt():
    d = 0.7
    n = N(d)
    y = np.zeros(n)
    put(y, bell(620.0, 0.7, 0.45, CRYSTAL_P), 0.0, 0.8)
    put(y, metal(380, 0.5, 0.1, 8), 0.0, 0.5)
    put(y, hit_noise(0.04, 3500, 0.9, 0.006), 0.0, 0.8)
    put(y, thump(0.15, 200, 80, 0.015, 0.04, 1.5, 0.0), 0.0, 0.5)
    put(y, choir_voice(fr("D4"), 0.45, "a", "alto", a=0.02, r=0.3), 0.0, 0.7)
    put(y, sparkle(0.4, 50, 3500, 11000, g=0.3), 0.03, 1.0)
    return reverb(y, 1.0, 0.3)


@sfx("boss2_death", 6.0)
def _boss2_death():
    d = 6.0
    n = N(d)
    tn = 4.1
    # collapsing choir
    mc = N(4.1)
    ch = np.zeros(mc)
    for nt, vc in (("D2", "bass"), ("A2", "bass"), ("D3", "tenor"), ("F3", "tenor"), ("A3", "alto"), ("D4", "alto"), ("F4", "soprano"), ("A4", "soprano")):
        put(ch, choir_voice(fr(nt), 4.1, "a", vc, a=1.4, r=0.3, vowel2="o", morph=np.linspace(0, 1, N(4.1))), 0.0, 0.9)
    rate = ep(mc, [(0, 1.0), (2.2, 1.0), (3.2, 0.55), (4.1, 0.2)])
    ch = varispeed(ch, rate)
    ch = crush(ch, 7, ep(mc, [(0, 1.0), (2.3, 1.5), (4.0, 20.0)]))
    ch = glitchify(ch, 2.3, 4.0, events=16, max_slice=70, seed_off=9)
    y = np.zeros(n)
    put(y, ch, 0.0, 1.0)
    put(y, sine(ep(mc, [(0, 110), (4.1, 18)], log=True), mc) * ep(mc, [(0, 0.1), (3.0, 0.5), (4.1, 0.0)]), 0.0, 0.7)
    put(y, sine(ep(N(1.6), [(0, 1800), (1.6, 14000)], log=True), N(1.6)) * ep(N(1.6), [(0, 0.0), (1.6, 0.25)]), 2.5, 1.0)
    put(y, sweep(wn(N(2.2)), "bp", ep(N(2.2), [(0, 300), (2.2, 12000)], log=True), 1.5) * ep(N(2.2), [(0, 0.0), (2.2, 1.0)]) ** 1.5, 1.9, 0.5)
    # supernova
    me = N(d - tn)
    tt = T(me)
    ex = sweep(wn(me), "lp", ep(me, [(0, 16000), (1.2, 400), (d - tn, 100)], log=True), 0.7) * np.exp(-tt / 0.7) * (1 - np.exp(-tt / 0.003))
    put(y, ex, tn, 1.4)
    put(y, thump(1.9, 66, 20, 0.12, 1.0, 2.4, 0.4), tn, 1.4)
    put(y, sparkle(1.8, 70, 3000, 13000, g=0.35, t_env=[1, 0.6, 0.1]), tn, 1.0)
    for i, nt in enumerate(["D6", "A6", "F6", "D7", "A7"]):
        put(y, bell(fr(nt), 1.8, 0.8, CRYSTAL_P), tn + 0.15 + i * 0.09, 0.12)
    for nt, vc in (("D3", "tenor"), ("A3", "alto"), ("D4", "alto"), ("F4", "soprano")):
        put(y, choir_voice(fr(nt), 1.9, "a", vc, a=0.5, r=1.2), tn + 0.05, 0.55)
    return reverb(y, 2.6, 0.42, dry=0.9)


@sfx("boss2_slam", 1.6)
def _boss2_slam():
    d = 1.6
    n = N(d)
    y = np.zeros(n)
    put(y, thump(1.4, 70, 22, 0.1, 0.8, 2.4, 0.4), 0.0, 1.0)
    put(y, hit_noise(0.3, 2800, 0.7, 0.03), 0.0, 0.7)
    put(y, lp(hit_noise(0.4, 800, 0.7, 0.07), 1000), 0.0, 0.7)
    put(y, debris(1.2, 80, 200, 3500, decay=0.5), 0.03, 0.45)
    put(y, whoosh(0.9, 3000, 100, 0.9, 0.1, 1.0), 0.0, 0.5)
    put(y, bell(55.0, 1.4, 0.5, GONG_P), 0.0, 0.18)
    put(y, choir_voice(fr("D2"), 0.9, "o", "bass", a=0.01, r=0.6), 0.0, 0.5)
    return reverb(y, 1.6, 0.45)


@sfx("boss2_beam", 3.0)
def _boss2_beam():
    d = 3.0
    n = N(d)
    t = T(n)
    f0 = fr("D3")
    sw = supersaw(f0, n, voices=5, detune=14.0)
    sw = sweep(sw, "lp", ep(n, [(0, 400), (0.8, 6500), (d, 6500)], log=True), 1.2)
    add = sum(sine(fr("D4") * k, n, R().random()) / (k ** 0.8) for k in range(1, 13)) * 0.3
    sh = fm(fr("D6"), 2.0, 2.0 + 1.0 * np.sin(TAU * 4.0 * t), n) * 0.12
    y = sw * 0.6 + add + sh + sine(fr("D2"), n) * 0.5
    y += hp(sweep(wn(n), "bp", 7000, 3.0), 3000) * 0.18 * (0.6 + 0.4 * np.sin(TAU * 9.0 * t))
    y = flanger(y, 0.003, 0.0022, 0.4, 0.5)
    y = sat(y * 0.6, 1.8) * adsr(n, 0.25, 0.3, 0.9, 0.5, k=2.0) * (0.9 + 0.1 * np.sin(TAU * 9.0 * t))
    for nt, vc in (("D4", "alto"), ("A4", "soprano")):
        put(y, choir_voice(fr(nt), 2.8, "a", vc, a=0.6, r=0.7), 0.1, 0.45)
    put(y, thump(0.6, 100, 40, 0.04, 0.3, 1.5, 0.3), 0.0, 0.5)
    return reverb(y, 1.2, 0.25)


@sfx("boss2_charge", 2.0)
def _boss2_charge():
    d = 2.0
    n = N(d)
    ramp = ep(n, [(0, 0.0), (1.9, 1.0), (d, 0.0)]) ** 1.5
    f = ep(n, [(0, fr("D3")), (1.9, fr("D4"))], log=True)
    vib = 1 + 0.004 * np.sin(TAU * 5.5 * T(n))
    src = np.zeros(n)
    for c in (-9, -3, 3, 9):
        src += saw(f * vib * 2 ** (c / 1200.0), n, R().random())
    ch = formant(lp(src / 4.0, 5000), "a", "alto") * 0.6
    sw = sweep(saw(f * 2, n) + saw(f * 2.01, n), "lp", ep(n, [(0, 300), (d, 7000)], log=True), 1.3) * 0.4
    y = (ch + sw) * ramp
    y += sweep(wn(n), "bp", ep(n, [(0, 500), (d, 11000)], log=True), 2.0) * ramp ** 2 * 0.3
    arp = ["D5", "F5", "A5", "D6", "F6", "A6", "D7", "F7", "A7"]
    t0 = 0.2
    gap = 0.2
    for i in range(18):
        put(y, bell(fr(arp[i % len(arp)]) * (2 if i >= 9 and False else 1), 0.5, 0.25, GLASS_P), t0, 0.12 + 0.28 * (t0 / d))
        t0 += gap
        gap = max(0.045, gap * 0.82)
        if t0 > d - 0.1:
            break
    put(y, sparkle(1.0, 80, 3000, 12000, g=0.3), 1.0, 1.0)
    return reverb(y, 0.9, 0.25)


@sfx("boss2_teleport", 0.8)
def _boss2_teleport():
    d = 0.8
    n = N(d)
    y = np.zeros(n)
    put(y, reverse(bell(fr("D6"), 0.4, 0.3, CRYSTAL_P)), 0.0, 0.5)
    put(y, whoosh(0.35, 300, 6000, 1.2, 0.8, 1.0), 0.0, 0.5)
    m = N(0.1)
    put(y, sine(ep(m, [(0, 400), (0.1, 5000)], log=True), m) * np.sin(np.linspace(0, np.pi, m)) * 0.4, 0.28, 1.0)
    put(y, thump(0.3, 140, 45, 0.02, 0.1, 1.5, 0.4), 0.38, 0.8)
    put(y, bell(fr("A6"), 0.5, 0.2, GLASS_P), 0.38, 0.4)
    put(y, bell(fr("F7"), 0.5, 0.2, GLASS_P), 0.4, 0.25)
    put(y, crush(hit_noise(0.05, 4000, 0.8, 0.015), 4, 8), 0.38, 0.3)
    put(y, sparkle(0.4, 60, 3500, 11000, g=0.3), 0.4, 1.0)
    return reverb(y, 0.9, 0.3)


@sfx("boss2_shoot", 0.4)
def _boss2_shoot():
    d = 0.4
    n = N(d)
    t = T(n)
    y = sine(ep(n, [(0, 2600), (0.12, 500), (d, 400)], log=True), n) * np.exp(-t / 0.06) * 0.8
    y += fm(ep(n, [(0, 1800), (0.2, 900), (d, 850)], log=True), 3.5, 3.0 * np.exp(-t / 0.08), n) * np.exp(-t / 0.09) * 0.5
    put(y, hp(whoosh(0.15, 1500, 9000, 1.0, 0.3), 2000), 0.0, 0.35)
    put(y, bell(fr("D7"), 0.3, 0.12, GLASS_P), 0.03, 0.18)
    put(y, sparkle(0.3, 60, 4000, 12000, g=0.3), 0.04, 1.0)
    return reverb(y, 0.5, 0.2)


@sfx("boss2_nova", 3.0)
def _boss2_nova():
    d = 3.0
    n = N(d)
    y = np.zeros(n)
    tb = 0.3
    put(y, reverse(whoosh(0.3, 400, 9000, 1.0, 0.8, 1.0)), 0.0, 0.5)
    put(y, sine(ep(N(0.3), [(0, 200), (0.3, 2200)], log=True), N(0.3)) * np.linspace(0, 1, N(0.3)) ** 2, 0.0, 0.3)
    me = N(d - tb)
    tt = T(me)
    ex = sweep(wn(me), "lp", ep(me, [(0, 14000), (1.4, 300), (d - tb, 120)], log=True), 0.7) * np.exp(-tt / 0.8) * (1 - np.exp(-tt / 0.003))
    put(y, ex, tb, 1.2)
    put(y, thump(2.2, 62, 20, 0.12, 1.2, 2.4, 0.4), tb, 1.3)
    put(y, hit_noise(0.4, 2800, 0.7, 0.05), tb, 0.8)
    put(y, sparkle(2.2, 90, 3000, 13000, g=0.35, t_env=[1, 0.6, 0.1]), tb, 1.0)
    for nt, vc in (("D3", "bass"), ("A3", "tenor"), ("D4", "alto"), ("F4", "alto"), ("A4", "soprano")):
        put(y, choir_voice(fr(nt), 2.6, "a", vc, a=0.4, r=1.3), tb + 0.05, 0.6)
    for i, nt in enumerate(["D6", "F6", "A6", "D7", "F7", "A7"]):
        put(y, bell(fr(nt), 1.6, 0.7, CRYSTAL_P), tb + 0.2 + i * 0.12, 0.12)
    return reverb(y, 2.6, 0.5)


@sfx("boss2_phase", 3.0)
def _boss2_phase():
    d = 3.0
    n = N(d)
    y = np.zeros(n)
    tb = 0.55
    put(y, whoosh(0.55, 300, 12000, 1.2, 0.95, 1.0) * 0.7, 0.0, 0.7)
    put(y, sine(ep(N(0.55), [(0, 200), (0.55, 1800)], log=True), N(0.55)) * np.linspace(0, 1, N(0.55)) ** 2, 0.0, 0.25)
    for nt, vc in (("D2", "bass"), ("A2", "bass"), ("D3", "tenor"), ("F3", "tenor"), ("A3", "alto"), ("D4", "alto"), ("F4", "soprano"), ("A4", "soprano")):
        put(y, choir_voice(fr(nt), 2.4, "a", vc, a=0.03, r=1.3), tb, 0.8)
    for f in (fr("D3"), fr("A3"), fr("D4")):
        b = sweep(supersaw(f, N(0.8), 5, 15.0), "lp", ep(N(0.8), [(0, 6000), (0.8, 900)], log=True), 1.0) * adsr(N(0.8), 0.01, 0.2, 0.4, 0.4)
        put(y, b, tb, 0.35)
    put(y, thump(1.6, 78, 24, 0.1, 0.8, 2.2, 0.4), tb, 1.3)
    put(y, hit_noise(0.4, 2600, 0.7, 0.05), tb, 0.8)
    put(y, whoosh(1.2, 6000, 120, 0.9, 0.08, 1.0), tb, 0.5)
    put(y, sparkle(1.5, 50, 3000, 12000, g=0.3, t_env=[1, 0.3]), tb, 1.0)
    put(y, bell(55.0, 2.0, 0.7, GONG_P), tb, 0.15)
    return reverb(y, 2.2, 0.4)


@sfx("boss2_step", 0.7)
def _boss2_step():
    d = 0.7
    n = N(d)
    y = np.zeros(n)
    put(y, thump(0.6, 62, 24, 0.07, 0.25, 2.2, 0.3), 0.0, 1.0)
    put(y, rumble(0.5, 160, tau=0.2) * 0.8, 0.0, 0.9)
    put(y, debris(0.4, 25, 200, 2500, decay=0.2), 0.02, 0.4)
    put(y, bell(95.0, 0.6, 0.2, GONG_P), 0.0, 0.12)
    put(y, metal(190, 0.3, 0.07, 6), 0.0, 0.2)
    return reverb(y, 0.8, 0.3)


# ---- iPhone Duo (soft premium UI) -----------------------------------------------------------------------------------
@sfx("phone_fold", 0.35)
def _phone_fold():
    d = 0.35
    n = N(d)
    y = np.zeros(n)
    m = N(0.2)
    fr_ = sweep(wn(m), "bp", ep(m, [(0, 900), (0.2, 1500)]), 2.0) * bump(m, 0.5, 1.0)
    put(y, fr_, 0.0, 0.25)
    put(y, thump(0.15, 180, 90, 0.015, 0.04, 1.0, 0.0), 0.2, 0.7)
    put(y, hit_noise(0.015, 3000, 1.2, 0.0025), 0.2, 0.45)
    put(y, np.sin(TAU * 320 * T(N(0.05))) * np.exp(-T(N(0.05)) / 0.008), 0.2, 0.4)
    return reverb(y, 0.25, 0.1)


@sfx("phone_unfold", 0.5)
def _phone_unfold():
    d = 0.5
    n = N(d)
    y = np.zeros(n)
    put(y, hit_noise(0.015, 3200, 1.2, 0.0025), 0.0, 0.4)
    put(y, thump(0.1, 220, 110, 0.01, 0.025, 1.0, 0.0), 0.0, 0.35)
    m = N(0.35)
    h = sweep(wn(m), "bp", ep(m, [(0, 800), (0.35, 1700)]), 2.2) * bump(m, 0.5, 1.0)
    put(y, h, 0.03, 0.22)
    put(y, sine(ep(m, [(0, 1400), (0.35, 2100)]), m) * bump(m, 0.5, 1.5) * 0.04, 0.03, 1.0)
    put(y, thump(0.15, 140, 80, 0.02, 0.05, 1.0, 0.0), 0.37, 0.4)
    return reverb(y, 0.25, 0.1)


@sfx("phone_unlock", 0.4)
def _phone_unlock():
    d = 0.4
    n = N(d)
    y = np.zeros(n)
    soft = [(1.0, 1.0, 1.0), (2.0, 0.22, 0.5), (3.0, 0.08, 0.3), (4.2, 0.05, 0.2)]
    put(y, bell(fr("C6"), 0.35, 0.16, soft), 0.0, 0.7)
    put(y, bell(fr("G6"), 0.3, 0.14, soft), 0.13, 0.7)
    put(y, tri(fr("C6"), N(0.2)) * np.exp(-T(N(0.2)) / 0.06) * 0.15, 0.0, 1.0)
    put(y, tri(fr("G6"), N(0.2)) * np.exp(-T(N(0.2)) / 0.06) * 0.15, 0.13, 1.0)
    return reverb(y, 0.4, 0.2)


@sfx("phone_tap", 0.06)
def _phone_tap():
    n = N(0.06)
    t = T(n)
    y = np.sin(TAU * 1700 * t) * np.exp(-t / 0.005) * (1 - np.exp(-t / 0.0004))
    put(y, hit_noise(0.02, 2500, 1.2, 0.002), 0.0, 0.3)
    return y


@sfx("phone_boot", 2.0)
def _phone_boot():
    d = 2.0
    n = N(d)
    y = np.zeros(n)
    chord = [fr("D3"), fr("A3"), fr("C#4"), fr("E4"), fr("F#4")]
    pad = pad_tone(chord, d, a=0.3, r=0.9, detune=6.0, voices=3, cutoff=2800)
    pad = sweep(pad, "lp", ep(n, [(0, 400), (0.9, 3500), (d, 2000)], log=True), 0.9)
    y += pad * 0.9
    for f in chord:
        y += sine(f, n) * adsr(n, 0.25, 0.3, 0.7, 0.9) * 0.12
    y += sine(fr("D2"), n) * adsr(n, 0.2, 0.3, 0.7, 0.9) * 0.3
    soft = [(1.0, 1.0, 1.0), (2.0, 0.25, 0.5), (3.0, 0.08, 0.3), (5.04, 0.05, 0.2)]
    for i, nt in enumerate(["A5", "C#6", "E6", "F#6"]):
        put(y, bell(fr(nt), 1.0, 0.45, soft), 0.55 + i * 0.2, 0.28)
    return reverb(y, 1.2, 0.35)


@sfx("phone_shutter", 0.2)
def _phone_shutter():
    n = N(0.2)
    t = T(n)
    y = np.zeros(n)
    put(y, hit_noise(0.02, 3600, 1.2, 0.003), 0.0, 0.9)
    put(y, np.sin(TAU * 2100 * t) * np.exp(-t / 0.006), 0.0, 0.5)
    put(y, thump(0.04, 400, 200, 0.006, 0.01, 1.0, 0.0), 0.0, 0.3)
    put(y, hit_noise(0.025, 1900, 1.0, 0.004), 0.07, 0.8)
    put(y, np.sin(TAU * 780 * t) * np.exp(-t / 0.012), 0.07, 0.5)
    put(y, thump(0.06, 300, 120, 0.01, 0.02, 1.0, 0.0), 0.07, 0.45)
    put(y, bp(wn(N(0.05)), 5000, 1.5) * np.exp(-T(N(0.05)) / 0.015), 0.03, 0.15)
    return y


@sfx("phone_notify", 0.8)
def _phone_notify():
    d = 0.8
    n = N(d)
    y = np.zeros(n)
    soft = [(1.0, 1.0, 1.0), (2.0, 0.2, 0.5), (4.0, 0.1, 0.25)]
    for i, nt in enumerate(["D6", "A5", "F#6"]):
        t0 = i * 0.16
        put(y, bell(fr(nt), 0.55, 0.22, soft), t0, 0.7)
        put(y, fmtone(fr(nt), 0.2, 4.0, 0.8, 0.06, 0.03), t0, 0.25)
    return reverb(y, 0.6, 0.25)


@sfx("phone_type", 0.05)
def _phone_type():
    n = N(0.05)
    t = T(n)
    y = np.sin(TAU * 1100 * t) * np.exp(-t / 0.004) * (1 - np.exp(-t / 0.0004)) * 0.7
    put(y, hit_noise(0.015, 3000, 1.2, 0.0018), 0.0, 0.4)
    return y


# ##### SFX-INSERT-POINT #####
# =====================================================================================================================
# 4) MUSIC  --  two seamless stereo loops (events are scheduled on a bar/beat grid, the reverb tails wrap onto the start)
# =====================================================================================================================
CHORDS = {
    "Am": ("A", "C", "E"), "F": ("F", "A", "C"), "C": ("C", "E", "G"), "G": ("G", "B", "D"),
    "Dm": ("D", "F", "A"), "E": ("E", "G#", "B"), "Bb": ("Bb", "D", "F"), "Gm": ("G", "Bb", "D"),
    "A": ("A", "C#", "E"), "Em": ("E", "G", "B"), "D": ("D", "F#", "A"), "B": ("B", "D#", "F#"),
}


def _voicing(name, octave):
    """closed-position midi notes of a triad, root in `octave`"""
    out = []
    for nt in CHORDS[name]:
        m = nn(nt + str(octave))
        while out and m <= out[-1]:
            m += 12
        out.append(m)
    return out


def _root_midi(name, octave):
    return nn(CHORDS[name][0] + str(octave))


class Song:
    """bar/beat scheduler with named stereo group buffers (float32); everything folds back onto [0, L) at the end"""

    def __init__(self, bpm, bars, tail=5.0):
        self.bpm = bpm
        self.sp = 60.0 / bpm
        self.bar = self.sp * 4.0
        self.bars = bars
        self.L = N(bars * self.bar)
        self.n = self.L + N(tail)
        self.g = {}

    def t(self, bar, beat=0.0):
        return (bar * 4.0 + beat) * self.sp

    def buf(self, k):
        if k not in self.g:
            self.g[k] = np.zeros((2, self.n), np.float32)
        return self.g[k]

    def put(self, k, sig, bar, beat=0.0, g=1.0, pan=0.0):
        put(self.buf(k), sig, self.t(bar, beat), g, pan)

    def duck(self, beats_abs, depth, rel):
        """sidechain gain curve: beats_abs = absolute beat positions of the kick hits"""
        return duck_curve(self.n, [b * self.sp for b in beats_abs], depth, rel).astype(np.float32)


def _peak_to(x, peak_db):
    p = float(np.max(np.abs(x))) if x.size else 0.0
    return x * (db(peak_db) / p) if p > 1e-9 else x


def _fold(x, L):
    y = x[:, :L].copy()
    tl = x[:, L:]
    m = min(tl.shape[1], L)
    y[:, :m] += tl[:, :m]
    return y


def _section_gain(S, secs, gains):
    """smooth per-section loudness envelope (ramps over half a bar around every boundary)"""
    pt, pg = [0.0], [gains[0]]
    b = 0
    for i, (_, nb) in enumerate(secs):
        b += nb
        if i + 1 < len(secs):
            tt = b * S.bar
            pt += [tt - 0.25 * S.bar, tt + 0.25 * S.bar]
            pg += [gains[i], gains[i + 1]]
    pt.append(S.n / SR + 1.0)
    pg.append(gains[-1])
    return np.interp(np.arange(S.n) / SR, pt, pg)


def _master(x, peak_db=-3.0, width=1.12):
    x = np.stack([hpf(c, 26, 2) for c in x])
    x = np.tanh(x * 1.05)
    x = midside(x, width)
    return (x * (db(peak_db) / float(np.max(np.abs(x))))).astype(np.float32)


def _reverb_return(send, rt60, damp=0.5, pre=0.012, seed_=3):
    return reverb2(send.astype(np.float64), rt60, damp, pre, seed_).astype(np.float32)


_MC = {}


def _cached(key, fn):
    if key not in _MC:
        _MC[key] = fn()
    return _MC[key]


# ---- shared drum voices ------------------------------------------------------------------------------------------------
def _kick_hard():
    return thump(0.45, f0=175, f1=46, tau=0.032, decay=0.27, drive=2.4, click=0.42)


def _snare_hard():
    n = N(0.42)
    t = T(n)
    tone = sine(170 * np.exp(-t / 0.03) + 150, n) * np.exp(-t / 0.07) * 0.6
    nz = hp(wn(n), 1300) * np.exp(-t / 0.11) * 0.95
    return sat(tone + nz, 1.8)


def _clap_hard():
    n = N(0.5)
    y = np.zeros(n)
    for off in (0.0, 0.011, 0.023):
        m = N(0.05)
        put(y, bp(wn(m), 1500, 0.9) * np.exp(-T(m) / 0.009), off, 0.8)
    t = T(n)
    return y + bp(wn(n), 1400, 0.8) * np.exp(-t / 0.13) * 0.55


def _hat(open_=False):
    if open_:
        return hit_noise(0.32, 8200, 0.8, 0.09, "hp")
    return hit_noise(0.06, 8800, 0.9, 0.012, "hp")


# =====================================================================================================================
# BOSS 1 - "Cipher Protocol":  dark industrial synthwave, A minor, 128 BPM, 56 bars (105 s)
# =====================================================================================================================
def _b1_bass(f, dur):
    def mk():
        n = N(dur)
        x = saw(f, n) * 0.7 + square(f, n, 0.5) * 0.3
        x = lp(x, 1250, 1.4)
        x = x + sine(f, n) * 0.85
        return sat(x * adsr(n, 0.004, 0.08, 0.85, 0.03, k=3.0), 1.9)
    return _cached(("b1bass", round(float(f), 2), round(dur, 3)), mk)


def _b1_pluck(f, dur=0.21):
    def mk():
        n = N(dur)
        x = saw(f, n) + saw(f * 1.007, n) * 0.8 + saw(f * 0.993, n) * 0.8
        x = lp(x * 0.35, 3600, 1.1)
        return x * exp_env(n, 0.085, 0.002)
    return _cached(("b1pluck", round(float(f), 2), round(dur, 3)), mk)


@music("boss1_theme")
def _boss1_theme():
    secs = [("intro", 4), ("verse", 8), ("build", 4), ("chorus", 8), ("verse", 8), ("break", 8), ("build", 4), ("chorus", 8), ("outro", 4)]
    S = Song(128, sum(b for _, b in secs), tail=5.0)
    sp, s16 = S.sp, S.sp / 4.0
    prog = {"intro": ["Am"] * 4, "verse": ["Am", "F", "C", "G"] * 2, "build": ["Am", "F", "G", "E"],
            "chorus": ["Am", "F", "C", "G"] * 2, "break": ["Dm", "Am", "F", "E"] * 2, "outro": ["Am", "F", "C", "G"]}
    kick, snare, clap = _kick_hard(), _snare_hard(), _clap_hard()
    hat_c, hat_o = _hat(False), _hat(True)
    crash = hiss(2.6, 3000, 13000, att=0.004, tau=0.9)
    clang = metal(fr("A4"), 0.5, decay=0.2, n_part=6, spread=1.0)
    kick_beats = []
    arp_pat = [0, 2, 4, 2, 1, 3, 5, 3, 0, 2, 4, 5, 4, 2, 1, 3]
    roll_pat = [1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15]
    # lead phrase (bar, [(note, beat, length)])  A minor, repeated for the second chorus an octave higher
    lead = [
        [("E5", 0, 1.5), ("A5", 1.5, 0.5), ("C6", 2, 1), ("B5", 3, 0.5), ("A5", 3.5, 0.5)],
        [("A5", 0, 1.5), ("G5", 1.5, 0.5), ("F5", 2, 2)],
        [("G5", 0, 1.5), ("E5", 1.5, 0.5), ("G5", 2, 1), ("C6", 3, 1)],
        [("B5", 0, 1.5), ("A5", 1.5, 0.5), ("G5", 2, 2)],
        [("E5", 0, 1), ("A5", 1, 1), ("C6", 2, 1), ("E6", 3, 1)],
        [("D6", 0, 1.5), ("C6", 1.5, 0.5), ("A5", 2, 2)],
        [("C6", 0, 1), ("B5", 1, 0.5), ("C6", 1.5, 0.5), ("G5", 2, 2)],
        [("A5", 0, 0.5), ("B5", 0.5, 0.5), ("D6", 1, 1), ("B5", 2, 1), ("G5", 3, 1)],
    ]
    bells = [
        [("F5", 0, 2), ("D5", 2, 2)], [("E5", 0, 2), ("C5", 2, 1), ("A4", 3, 1)],
        [("C5", 0, 2), ("A4", 2, 2)], [("B4", 0, 1), ("G#4", 1, 1), ("B4", 2, 2)],
    ]
    bar0 = 0
    chorus_no = 0
    for sec, nb in secs:
        if sec == "chorus":
            chorus_no += 1
        for k in range(nb):
            bar = bar0 + k
            ch = prog[sec][k]
            tones = _voicing(ch, 4)
            ctn = tones + [m + 12 for m in tones]
            rm = _root_midi(ch, 1)
            while rm < 28:
                rm += 12
            f_root = float(mtof(rm))
            first_chorus_bar = (sec == "chorus" and k == 0)
            # ------------------------------------------------ drums
            four = sec in ("verse", "chorus") or (sec == "outro" and k < 2) or (sec == "build" and k < 2)
            if four:
                for b in range(4):
                    S.put("kick", kick, bar, b, 1.0)
                    kick_beats.append(bar * 4 + b)
                    if sec == "chorus" and b == 3:
                        S.put("kick", kick, bar, 3.5, 0.55)
            elif sec == "break":
                for b in (0, 2):
                    S.put("kick", kick, bar, b, 0.8)
                    kick_beats.append(bar * 4 + b)
            elif sec in ("build", "outro") and k >= 2:
                S.put("kick", kick, bar, 0, 0.9)
                kick_beats.append(bar * 4)
            if sec in ("verse", "chorus") or (sec == "outro" and k < 2) or (sec == "build" and k < 2):
                for b in (1, 3):
                    S.put("snare", clap, bar, b, 0.9)
                    S.put("snare", snare, bar, b, 0.55 if sec == "chorus" else 0.35)
                    S.put("rev", clap, bar, b, 0.35)
            if sec == "break":
                S.put("snare", clap, bar, 2, 0.7)
                S.put("rev", clap, bar, 2, 0.5)
            if sec == "build" and k >= 2:   # snare roll, accelerating
                steps = 8 if k == 2 else 16
                for i in range(steps):
                    pos = i * (4.0 / steps)
                    vel = (0.25 + 0.75 * ((k - 2) * steps + i) / (steps * 2.0))
                    S.put("snare", snare, bar, pos, vel)
                    S.put("rev", snare, bar, pos, vel * 0.3)
            # hats
            if sec in ("verse", "chorus", "outro", "break", "build"):
                for i in range(8):
                    S.put("hat", hat_c, bar, i * 0.5, 0.6 if i % 2 == 1 else 0.2, pan=0.25 if i % 2 else -0.25)
            if sec == "chorus":
                for i in range(16):
                    if i % 2 == 1:
                        S.put("hat", hat_c, bar, i * 0.25, 0.16, pan=-0.3)
                S.put("hat", hat_o, bar, 1.5, 0.5)
                S.put("hat", hat_o, bar, 3.5, 0.5)
                S.put("perc", clang, bar, 2.75, 0.5, pan=0.4)
            if sec == "verse" and k % 2 == 1:
                S.put("hat", hat_o, bar, 3.5, 0.45)
            if sec == "intro" and k >= 2:
                for i in range(8):
                    S.put("hat", hat_c, bar, i * 0.5, 0.18 + 0.05 * i / 8.0, pan=0.3 * (1 if i % 2 else -1))
            if first_chorus_bar or (sec == "chorus" and k == 4):
                S.put("fx", crash, bar, 0, 0.9)
                S.put("rev", crash, bar, 0, 0.4)
            if first_chorus_bar:
                S.put("fx", thump(1.8, f0=75, f1=28, tau=0.18, decay=0.9, drive=1.4, click=0.2), bar, 0, 1.0)
            # ------------------------------------------------ intro ticks (a clock counting down to the fight)
            if sec == "intro":
                for i in range(8):
                    S.put("perc", hit_noise(0.02, 6800, 3.0, 0.003), bar, i * 0.5, 0.35 if i % 2 == 0 else 0.15, pan=0.5 if i % 2 else -0.5)
            # ------------------------------------------------ bass
            if sec == "verse" or (sec == "outro" and k < 2) or (sec == "build" and k < 2):
                for st_ in (2, 6, 10, 14):
                    S.put("bass", _b1_bass(f_root, s16 * 3.0), bar, st_ / 4.0, 1.0)
            elif sec == "chorus":
                for st_ in roll_pat:
                    f = f_root * (2.0 if st_ in (3, 11) else 1.0)
                    S.put("bass", _b1_bass(f, s16 * 0.95), bar, st_ / 4.0, 0.9 if st_ % 2 else 1.0)
                S.put("bass", _b1_bass(f_root, s16 * 1.8), bar, 0, 0.9)
            elif sec in ("break", "intro", "outro", "build"):
                if not (sec == "build" and k >= 2) and not (sec == "intro" and k < 1):
                    S.put("bass", _b1_bass(f_root, S.bar * 0.97), bar, 0, 0.55 if sec != "break" else 0.8)
            # ------------------------------------------------ arp
            if sec in ("verse", "chorus", "build", "outro") or (sec == "intro" and k >= 1):
                gain = {"intro": 0.35 + 0.15 * k, "verse": 0.75, "chorus": 1.0, "build": 0.9, "outro": 0.7}[sec]
                for i, idx in enumerate(arp_pat):
                    f = float(mtof(ctn[idx % len(ctn)] + (12 if sec == "chorus" and i % 8 == 7 else 0)))
                    S.put("arp", _b1_pluck(f), bar, i / 4.0, gain, pan=0.55 * (1 if i % 2 else -1))
            if sec == "break":   # slow filtered 8th arp under the bells
                for i in range(8):
                    f = float(mtof(ctn[arp_pat[(i * 2) % 16] % len(ctn)]))
                    S.put("arp", _b1_pluck(f, 0.4), bar, i * 0.5, 0.45, pan=0.4 * (1 if i % 2 else -1))
            # ------------------------------------------------ pad
            if sec != "build" or k < 4:
                pg = {"intro": 0.5, "verse": 0.55, "chorus": 0.8, "break": 1.0, "build": 0.7, "outro": 0.6}[sec]
                pf = [float(mtof(m)) for m in _voicing(ch, 3)]
                for pan in (-0.55, 0.55):
                    S.put("pad", pad_tone(pf, S.bar + 0.5, a=0.35, r=0.5, detune=9, voices=3, cutoff=1900 if sec != "break" else 1300),
                          bar, 0, pg * 0.6, pan=pan)
            # ------------------------------------------------ lead / bells
            if sec == "chorus":
                for nt, beat, ln in lead[k % 8]:
                    f = float(mtof(nn(nt) + (12 if chorus_no == 2 else 0)))
                    n_ = N(ln * sp + 0.25)
                    l_, r_ = supersaw(f, n_, voices=5, detune=15, stereo=True, vib=(5.3, 0.0035), spread=0.9)
                    x = np.stack([lp(l_, 5600), lp(r_, 5600)]) * adsr(n_, 0.01, 0.12, 0.8, 0.22, k=3.0)
                    S.put("lead", x, bar, beat, 0.9)
                    S.put("rev", x, bar, beat, 0.35)
                    if chorus_no == 2:   # octave-down double + a fifth above on the long notes
                        x2 = np.stack([lp(l_, 2800), lp(r_, 2800)]) * adsr(n_, 0.01, 0.12, 0.8, 0.22, k=3.0)
                        S.put("lead", x2, bar, beat, 0.35)
            if sec == "break":
                for nt, beat, ln in bells[k % 4]:
                    f = float(mtof(nn(nt) + (12 if k >= 4 else 0)))
                    bl = fmtone(f, ln * sp + 1.6, ratio=3.5, index=2.4, decay=0.9, idx_decay=0.35) * 0.8
                    S.put("bell", bl, bar, beat, 0.9, pan=0.3)
                    S.put("rev", bl, bar, beat, 0.7)
                if k % 2 == 0:   # cipher chirps
                    for i in range(12):
                        fq = float(mtof(nn("A6") + [0, 3, 7, 12, 7, 3][i % 6]))
                        ch_ = sine(fq, N(0.05)) * exp_env(N(0.05), 0.02, 0.001)
                        S.put("perc", ch_, bar, 2.0 + i * 0.125, 0.25, pan=0.7 * (1 if i % 2 else -1))
            # ------------------------------------------------ risers / transitions
            if sec == "build":
                if k == 0:
                    S.put("fx", whoosh(S.bar * 4, 300, 11500, q=1.4, peak=0.96, p=2.3), bar, 0, 0.85)
                    S.put("rev", whoosh(S.bar * 4, 300, 11500, q=1.4, peak=0.96, p=2.3), bar, 0, 0.3)
                if k == 3:
                    S.put("fx", reverse(crash[: N(1.4)]), bar, 2.6, 0.8)
            if sec == "break" and k == 0:
                S.put("fx", reverse(hiss(1.6, 2500, 12000, att=0.004, tau=0.5)), bar - 1, 3.0, 0.5)
            if sec == "outro" and k == 3:
                S.put("fx", whoosh(S.bar, 9000, 250, q=1.6, peak=0.2, p=1.5), bar, 0, 0.7)
        bar0 += nb
    # ------------------------------------------------ mix
    dk = {"bass": S.duck(kick_beats, 0.78, 0.2), "arp": S.duck(kick_beats, 0.5, 0.18), "pad": S.duck(kick_beats, 0.7, 0.24),
          "lead": S.duck(kick_beats, 0.3, 0.16), "bell": S.duck(kick_beats, 0.2, 0.14)}
    lv = {"kick": -1.5, "snare": -4.5, "hat": -15.0, "perc": -15.0, "bass": -5.5, "arp": -13.0, "pad": -16.0,
          "lead": -9.0, "bell": -13.0, "fx": -9.5}
    dry = np.zeros((2, S.n))
    for kname, buf in S.g.items():
        if kname == "rev":
            continue
        x = buf.astype(np.float64)
        if kname in dk:
            x = x * dk[kname]
        dry += _peak_to(x, lv[kname])
    send = S.buf("rev").astype(np.float64)
    for kname in ("arp", "pad", "lead", "bell"):
        send += S.buf(kname).astype(np.float64) * {"arp": 0.22, "pad": 0.45, "lead": 0.2, "bell": 0.5}[kname] * dk[kname]
    wet = _reverb_return(send, 2.6, 0.55, 0.014, 5)
    dry += _peak_to(wet.astype(np.float64), -17.0)
    dry *= _section_gain(S, secs, [0.5, 0.78, 0.85, 1.0, 0.8, 0.62, 0.85, 1.08, 0.6])
    return _master(_fold(dry, S.L), -3.0)


# =====================================================================================================================
# BOSS 2 - "Omega Ascension":  choir + orchestral synth, D minor (lifts to E minor), 150 BPM, 64 bars (102.4 s)
# =====================================================================================================================
def _voice_for(m):
    return "bass" if m < 55 else "tenor" if m < 62 else "alto" if m < 72 else "soprano"


def _choir_note(m, dur, vowel="a", a=0.5, r=0.9):
    def mk():
        return choir_voice(float(mtof(m)), dur, vowel, _voice_for(m), unison=4, detune=10.0, a=a, r=r, breath=0.05)
    return _cached(("choir", int(m), round(dur, 2), vowel, a, r), mk)


def _brass_note(m, dur, bright=1.0, a=0.05, r=0.2):
    def mk():
        n = N(dur)
        l_, r_ = supersaw(float(mtof(m)), n, voices=5, detune=11, stereo=True, vib=(5.0, 0.002), spread=0.5)
        cut = ep(n, [(0, 700), (min(dur * 0.35, 0.5), 3400 * bright), (dur, 2200 * bright)])
        env = adsr(n, a, 0.15, 0.8, r, k=3.0)
        return np.stack([sweep(l_, "lp", cut, 0.9, 128), sweep(r_, "lp", cut, 0.9, 128)]) * env
    return _cached(("brass", int(m), round(dur, 2), bright, a), mk)


def _string_stab(f, dur=0.13):
    def mk():
        n = N(dur)
        x = supersaw(f, n, voices=3, detune=9)
        return lp(x, 3300, 0.9) * adsr(n, 0.004, 0.05, 0.6, 0.04, k=3.0)
    return _cached(("stab", round(float(f), 2), round(dur, 3)), mk)


def _taiko(low=True):
    if low:
        return thump(0.9, f0=120, f1=42, tau=0.07, decay=0.45, drive=1.6, click=0.12)
    return thump(0.55, f0=210, f1=95, tau=0.05, decay=0.25, drive=1.4, click=0.1)


def _snare_orch():
    n = N(0.5)
    t = T(n)
    return sat(sine(200 * np.exp(-t / 0.02) + 175, n) * np.exp(-t / 0.05) * 0.5 + hp(wn(n), 900) * np.exp(-t / 0.12), 1.5)


@music("boss2_theme")
def _boss2_theme():
    secs = [("intro", 8), ("verse", 8), ("build", 4), ("chorus", 12), ("break", 8), ("verse", 8), ("build2", 4), ("final", 8), ("outro", 4)]
    S = Song(150, sum(b for _, b in secs), tail=6.0)
    sp = S.sp
    D = ["Dm", "Bb", "Gm", "A"]
    prog = {"intro": ["Dm"] * 4 + ["Bb", "Bb", "Gm", "A"], "verse": D * 2, "build": ["Bb", "Gm", "A", "A"],
            "chorus": ["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A", "Dm", "Bb", "Gm", "A"],
            "break": ["Gm", "Dm", "Bb", "A"] * 2, "build2": ["Bb", "Gm", "A", "B"],
            "final": ["Em", "C", "G", "D", "Em", "C", "Am", "B"], "outro": ["Gm", "Bb", "A", "A"]}
    theme = [
        [("D5", 0, 2), ("F5", 2, 1), ("A5", 3, 1)], [("G5", 0, 2), ("F5", 2, 1), ("D5", 3, 1)],
        [("C5", 0, 2), ("F5", 2, 1), ("A5", 3, 1)], [("G5", 0, 2), ("E5", 2, 2)],
        [("D5", 0, 1), ("F5", 1, 1), ("A5", 2, 1), ("D6", 3, 1)], [("C6", 0, 2), ("Bb5", 2, 1), ("G5", 3, 1)],
        [("Bb5", 0, 1.5), ("A5", 1.5, 0.5), ("G5", 2, 2)], [("A5", 0, 1), ("C#6", 1, 1), ("E6", 2, 2)],
        [("F6", 0, 2), ("E6", 2, 1), ("D6", 3, 1)], [("D6", 0, 2), ("C6", 2, 1), ("Bb5", 3, 1)],
        [("Bb5", 0, 1), ("D6", 1, 1), ("G6", 2, 2)], [("E6", 0, 1.5), ("C#6", 1.5, 0.5), ("A5", 2, 2)],
    ]
    brk = [[("D5", 0, 3), ("C5", 3, 1)], [("A4", 0, 4)], [("D5", 0, 2), ("F5", 2, 2)], [("E5", 0, 2), ("C#5", 2, 1), ("E5", 3, 1)]]
    intro_bell = [("D5", 0, 4), ("A5", 2, 2)]
    st_pat = [0, 0, 1, 0, 2, 0, 1, 0, 0, 0, 1, 0, 2, 3, 2, 1]
    kick_beats = []
    taiko_l, taiko_h, snr = _taiko(True), _taiko(False), _snare_orch()
    crash = hiss(3.2, 2500, 14000, att=0.01, tau=1.2)
    boom = thump(2.4, f0=70, f1=26, tau=0.22, decay=1.2, drive=1.3, click=0.15)
    chorus_bar = 0
    bar0 = 0
    for sec, nb in secs:
        for k in range(nb):
            bar = bar0 + k
            ch = prog[sec][k]
            tr = 2 if sec == "final" else 0
            big = sec in ("chorus", "final")
            drive_sec = sec in ("verse", "chorus", "final", "build", "build2")
            tones = _voicing(ch, 3)
            rm = _root_midi(ch, 2)
            # ------------------------------------------------ percussion
            if sec == "intro":
                if k >= 2:
                    for b in (0, 2):
                        S.put("drum", taiko_l, bar, b, 0.35 + 0.08 * k)
                        kick_beats.append(bar * 4 + b)
                if k == 7:
                    for i in range(8):
                        S.put("drum", snr, bar, 2.0 + i * 0.25, 0.2 + 0.1 * i)
            elif sec in ("verse", "build", "build2"):
                for b, g in ((0, 1.0), (2, 0.8)):
                    S.put("drum", taiko_l, bar, b, g)
                    kick_beats.append(bar * 4 + b)
                for pos in (1.5, 2.5, 3.5):
                    S.put("drum", taiko_h, bar, pos, 0.55, pan=0.2)
                S.put("drum", snr, bar, 1, 0.55)
                S.put("drum", snr, bar, 3, 0.55)
                S.put("rev", snr, bar, 1, 0.3)
                S.put("rev", snr, bar, 3, 0.3)
                if sec in ("build", "build2") and k >= 2:
                    steps = 8 if k == 2 else 16
                    for i in range(steps):
                        pos = i * (4.0 / steps)
                        S.put("drum", snr, bar, pos, 0.25 + 0.75 * (((k - 2) * steps + i) / (steps * 2.0)))
            elif big:
                for i in range(8):
                    S.put("drum", taiko_l if i % 2 == 0 else taiko_h, bar, i * 0.5, 1.0 if i % 4 == 0 else 0.6, pan=0.0 if i % 2 == 0 else -0.2)
                    if i % 2 == 0:
                        kick_beats.append(bar * 4 + i * 0.5)
                S.put("drum", snr, bar, 1, 0.9)
                S.put("drum", snr, bar, 3, 0.9)
                S.put("rev", snr, bar, 1, 0.45)
                S.put("rev", snr, bar, 3, 0.45)
                if k % 4 == 0:
                    S.put("fx", crash, bar, 0, 0.8)
                    S.put("rev", crash, bar, 0, 0.4)
            elif sec == "break":
                for b in (0, 2.5):
                    S.put("drum", taiko_l, bar, b, 0.7)
                    kick_beats.append(bar * 4 + b)
                if k % 2 == 1:
                    S.put("drum", snr, bar, 3, 0.5)
            elif sec == "outro":
                if k < 2:
                    S.put("drum", taiko_l, bar, 0, 0.7)
                    kick_beats.append(bar * 4)
            if sec in ("chorus", "final") and k == 0:
                S.put("fx", boom, bar, 0, 1.0)
            if sec in ("verse", "break") and k == 0 and bar > 0:
                S.put("fx", boom, bar, 0, 0.7)
            # ------------------------------------------------ sub drone + low choir
            sub = float(mtof(rm - 12 if rm - 12 >= 24 else rm))
            n_ = N(S.bar + 0.4)
            S.put("sub", (sine(sub, n_) * 0.8 + lp(saw(sub, n_), 160) * 0.5) * adsr(n_, 0.2, 0.2, 0.9, 0.4, k=2.0), bar, 0, 1.0)
            cg = {"intro": 0.5, "verse": 0.45, "build": 0.7, "build2": 0.7, "chorus": 1.0, "final": 1.1, "break": 0.8, "outro": 0.5}[sec]
            # whole-bar choir chord (SATB-ish spread), vowel opens from "o" to "a" in the big sections
            vow = "a" if (big or sec in ("build", "build2")) else "o"
            if not (sec == "intro" and k < 2):
                notes = [rm, rm + 7, tones[0] + 12, tones[1] + 12, tones[2] + 12, tones[0] + 24]
                for i, m in enumerate(notes):
                    S.put("choir", _choir_note(m, S.bar * 1.0 + 0.9, vow, 0.45, 0.9), bar, 0, cg * (0.8 if i in (0, 1) else 1.0),
                          pan=[-0.1, 0.1, -0.45, 0.45, -0.2, 0.2][i])
            # ------------------------------------------------ strings ostinato / pad
            if drive_sec:
                ctn = [tones[0] + 12, tones[1] + 12, tones[2] + 12, tones[0] + 24]
                sg_ = 0.6 if sec == "verse" else 0.85
                for i, idx in enumerate(st_pat):
                    f = float(mtof(ctn[idx % 4]))
                    S.put("str", _string_stab(f), bar, i * 0.25, sg_, pan=0.5 * (1 if i % 2 else -1))
            else:
                pf = [float(mtof(m)) for m in _voicing(ch, 3)]
                for pan in (-0.5, 0.5):
                    S.put("pad", pad_tone(pf, S.bar + 0.7, a=0.5, r=0.7, detune=8, voices=3, cutoff=1500), bar, 0, 0.5, pan=pan)
            # ------------------------------------------------ brass
            if sec in ("verse", "build", "build2"):
                bn = [rm + 12, rm + 19]
                for m in bn:
                    S.put("brass", _brass_note(m, 0.55, 0.9, 0.03, 0.25), bar, 0, 0.8)
                    S.put("brass", _brass_note(m, 0.4, 0.8, 0.03, 0.2), bar, 2.5, 0.55)
            # ------------------------------------------------ themes
            if sec in ("chorus", "final"):
                for nt, beat, ln in theme[k % 12]:
                    m = nn(nt) + tr
                    dur = ln * sp + 0.4
                    S.put("choir", _choir_note(m, dur, "a", 0.08, 0.35), bar, beat, 1.3, pan=-0.15)
                    S.put("choir", _choir_note(m - 12, dur, "o", 0.08, 0.35), bar, beat, 0.8, pan=0.15)
                    S.put("brass", _brass_note(m, dur, 1.1, 0.05, 0.3), bar, beat, 0.85)
                    S.put("brass", _brass_note(m - 12, dur, 0.9, 0.05, 0.3), bar, beat, 0.55)
                    S.put("rev", _choir_note(m, dur, "a", 0.08, 0.35), bar, beat, 0.5)
            if sec == "break":
                for nt, beat, ln in brk[k % 4]:
                    m = nn(nt) + (12 if k >= 4 else 0)
                    S.put("choir", _choir_note(m, ln * sp + 1.2, "a", 0.25, 0.8), bar, beat, 1.4, pan=0.0)
                    bl = fmtone(float(mtof(m + 12)), 2.2, ratio=3.5, index=2.2, decay=1.1, idx_decay=0.4) * 0.7
                    S.put("bell", bl, bar, beat, 0.8, pan=0.3)
                    S.put("rev", bl, bar, beat, 0.7)
            if sec == "intro":
                for nt, beat, ln in intro_bell:
                    if k % 2 == 0:
                        m = nn(nt) + (12 if k >= 4 else 0)
                        bl = bell(float(mtof(m)), 5.0, decay=2.6, partials=BELL_P, bright=1.0) * 0.8
                        S.put("bell", bl, bar, beat, 0.9, pan=-0.3 if beat == 0 else 0.3)
                        S.put("rev", bl, bar, beat, 0.9)
            # ------------------------------------------------ risers
            if sec in ("build", "build2") and k == 0:
                S.put("fx", whoosh(S.bar * 4, 250, 12000, q=1.3, peak=0.97, p=2.4), bar, 0, 0.9)
                S.put("rev", whoosh(S.bar * 4, 250, 12000, q=1.3, peak=0.97, p=2.4), bar, 0, 0.3)
            if sec in ("build", "build2") and k == 3:
                S.put("fx", reverse(crash[: N(1.6)]), bar, 2.4, 0.8)
            if sec == "intro" and k == 7:
                S.put("fx", whoosh(S.bar, 300, 9000, q=1.4, peak=0.97, p=2.0), bar, 0, 0.6)
            if sec == "outro" and k == 3:
                S.put("fx", whoosh(S.bar, 7000, 220, q=1.6, peak=0.2, p=1.5), bar, 0, 0.6)
        bar0 += nb
    # ------------------------------------------------ mix
    dk = {"sub": S.duck(kick_beats, 0.35, 0.3), "str": S.duck(kick_beats, 0.25, 0.14), "pad": S.duck(kick_beats, 0.3, 0.3)}
    lv = {"drum": -2.0, "sub": -8.0, "choir": -7.0, "str": -12.0, "pad": -15.0, "brass": -9.0, "bell": -12.0, "fx": -8.5}
    dry = np.zeros((2, S.n))
    for kname, buf in S.g.items():
        if kname == "rev":
            continue
        x = buf.astype(np.float64)
        if kname in dk:
            x = x * dk[kname]
        dry += _peak_to(x, lv[kname])
    send = S.buf("rev").astype(np.float64)
    for kname, gk in (("choir", 0.35), ("brass", 0.25), ("str", 0.18), ("pad", 0.4), ("bell", 0.5), ("drum", 0.12)):
        send += S.buf(kname).astype(np.float64) * gk
    wet = _reverb_return(send, 3.8, 0.45, 0.02, 9)
    dry += _peak_to(wet.astype(np.float64), -14.0)
    dry *= _section_gain(S, secs, [0.4, 0.7, 0.85, 1.0, 0.6, 0.78, 0.9, 1.1, 0.6])
    return _master(_fold(dry, S.L), -3.0, 1.25)


# ##### MUSIC-INSERT-POINT #####

# =====================================================================================================================
# 5) PIPELINE
# =====================================================================================================================
SUBTITLES = {}


def encode_ogg(path, data, channels):
    data = np.ascontiguousarray(data, dtype=np.float32)
    if channels == 2:
        data = np.ascontiguousarray(data.T)  # (n,2) interleaved
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "f32le", "-ar", str(SR), "-ac", str(channels), "-i", "pipe:0",
           "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
           "-c:a", "libvorbis", "-q:a", "4", str(path)]
    subprocess.run(cmd, input=data.tobytes(), check=True)


def render_one(name):
    t0 = time.time()
    seed(name)
    OUT_SOUNDS.mkdir(parents=True, exist_ok=True)
    path = OUT_SOUNDS / (name + ".ogg")
    if name in SFX:
        fn, dur = SFX[name]
        x = finish_sfx(fn(), dur)
        encode_ogg(path, x, 1)
        info = "%.2fs mono" % (len(x) / SR)
    elif name in MUSIC:
        st = MUSIC[name]()
        st = np.nan_to_num(st)
        st = st.astype(np.float32)
        encode_ogg(path, st, 2)
        info = "%.1fs stereo peak %.2f dBFS" % (st.shape[1] / SR, 20 * math.log10(np.max(np.abs(st))))
    else:
        raise KeyError(name)
    return "%-18s %s  (%.1fs to render, %d KB)" % (name, info, time.time() - t0, path.stat().st_size // 1024)


def write_trackinfo():
    """TrackInfo.java carries the theme lengths (seconds) for the phone's Music app; measured from the encoded files."""
    secs = []
    for nme in ("boss1_theme", "boss2_theme"):
        p = OUT_SOUNDS / (nme + ".ogg")
        if not p.exists():
            return
        try:
            out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)],
                                 capture_output=True, text=True, check=True).stdout.strip()
            secs.append(int(round(float(out))))
        except Exception:
            return
    f = ROOT / "src/main/java/com/darkwebduo/client/screen/TrackInfo.java"
    if f.exists():
        txt = f.read_text(encoding="utf-8")
        txt = re.sub(r"SECONDS = \{[^}]*\}", "SECONDS = {%d, %d}" % (secs[0], secs[1]), txt)
        f.write_text(txt, encoding="utf-8")
        print("TrackInfo.SECONDS = {%d, %d}" % (secs[0], secs[1]))


def modsounds_names():
    if MODSOUNDS_JAVA.exists():
        return re.findall(r'reg\("([a-z0-9_]+)"\)', MODSOUNDS_JAVA.read_text(encoding="utf-8"))
    return list(SFX) + list(MUSIC)


def write_json(names):
    sounds = {}
    lang = {}
    for nme in names:
        if nme in MUSIC:
            sounds[nme] = {"sounds": [{"name": "%s:%s" % (MODID, nme), "stream": True}]}
        else:
            sounds[nme] = {"sounds": [{"name": "%s:%s" % (MODID, nme)}], "subtitle": "subtitles.%s.%s" % (MODID, nme)}
            lang["subtitles.%s.%s" % (MODID, nme)] = SUBTITLES.get(nme, nme.replace("_", " ").capitalize())
    OUT_JSON.write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")
    OUT_LANG.parent.mkdir(parents=True, exist_ok=True)
    OUT_LANG.write_text(json.dumps(lang, indent=2) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--sfx", action="store_true")
    ap.add_argument("--music", action="store_true")
    ap.add_argument("-j", type=int, default=2)
    a = ap.parse_args()
    names = modsounds_names()
    missing = [n_ for n_ in names if n_ not in SFX and n_ not in MUSIC]
    for m in missing:
        print("WARNING: no generator for sound '%s'" % m)
    todo = [n_ for n_ in names if n_ in SFX or n_ in MUSIC]
    if a.names:
        todo = [n_ for n_ in todo if n_ in a.names]
    elif a.sfx:
        todo = [n_ for n_ in todo if n_ in SFX]
    elif a.music:
        todo = [n_ for n_ in todo if n_ in MUSIC]
    # longest jobs first so the two worker processes stay balanced
    todo.sort(key=lambda n_: (n_ not in MUSIC, n_))
    t0 = time.time()
    if a.j > 1 and len(todo) > 1:
        from concurrent.futures import ProcessPoolExecutor
        with ProcessPoolExecutor(max_workers=a.j) as ex:
            for line in ex.map(render_one, todo):
                print(line, flush=True)
    else:
        for n_ in todo:
            print(render_one(n_), flush=True)
    write_json([n_ for n_ in names if n_ in SFX or n_ in MUSIC])
    write_trackinfo()
    tot = sum(p.stat().st_size for p in OUT_SOUNDS.glob("*.ogg"))
    print("done: %d sounds rendered in %.1fs, %d ogg files, %.2f MB total" % (len(todo), time.time() - t0, len(list(OUT_SOUNDS.glob('*.ogg'))), tot / 1e6))


if __name__ == "__main__":
    main()
