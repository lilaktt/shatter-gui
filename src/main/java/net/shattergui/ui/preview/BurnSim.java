package net.shattergui.ui.preview;

import net.shattergui.params.ParamValues;
import net.shattergui.params.Params;

/**
 * Burn: a ragged front climbs from the bottom (duration); ahead of it the sample yellows, at it the cells glow, behind
 * it they char and crumble. Flames and sparks rise from the front (flame density), smoke from the front and the burnt
 * area (smoke density), and smoke goes on for the smoke duration — as in the real effect.
 */
final class BurnSim extends Sim {
    private static final float PREHEAT = 5f;
    private static final float EMBER = 3f;
    private static final float CHAR = 7f;
    private static final float CRUMBLE = 7f;
    private static final int SCORCHED = 0xFFE2B4;
    private static final int EMBER_HOT = 0xFFEC96;
    private static final int EMBER_COOL = 0xFF8C28;
    private static final int CHARCOAL = 0x221812;
    private static final int ASH = 0x46403A;
    private final float flames;
    private final float smoke;
    private final float smokeDuration;
    private float lastIgnition;

    BurnSim(Stage stage, ParamValues params, long seed) {
        super(stage, params, seed);
        float duration = params.i(Params.BURN_DURATION) * params.durationScale();
        int cols = stage.small() ? 18 : 30;
        int rows = stage.small() ? 14 : 24;
        int noiseSeed = random.nextInt();
        for (Piece p : squares(cols, rows)) {
            float fx = (p.cx - stage.sx()) / stage.sw();
            float fy = (p.cy - stage.sy()) / stage.sh();
            float n = noise(fx * 5f, fy * 5f, noiseSeed) * 0.65f + noise(fx * 11f, fy * 11f, noiseSeed + 1) * 0.35f;
            float ignition = (1f - fy) * duration * 0.8f + (n - 0.5f) * duration * 0.45f + Math.abs(fx - 0.5f) * duration * 0.15f;
            p.a = ignition;
            lastIgnition = Math.max(lastIgnition, ignition);
            pieces.add(p);
        }
        float particleScale = params.particleScale();
        flames = params.f(Params.BURN_FLAME_DENSITY) * particleScale;
        smoke = params.f(Params.BURN_SMOKE_DENSITY) / 0.35f * particleScale;
        smokeDuration = params.i(Params.BURN_SMOKE_DURATION) * params.durationScale();
    }

    private static int lerp(int a, int b, float t) {
        return net.shattergui.ui.Theme.mix(0xFF000000 | a, 0xFF000000 | b, t) & 0xFFFFFF;
    }

    @Override
    protected void step(float dt) {
        float scale = stage.sw() / 176f;
        int front = 0;
        for (Piece p : pieces) {
            if (p.dead) {
                continue;
            }
            float burning = time - p.a;
            if (burning < -PREHEAT) {
                p.tint = 0xFFFFFF;
                p.overlay = 0;
                continue;
            }
            if (burning < 0) {
                p.tint = lerp(0xFFFFFF, SCORCHED, smoothstep(-PREHEAT, 0f, burning));
                p.overlay = 0;
            } else if (burning < EMBER) {
                float t = burning / EMBER;
                p.tint = lerp(EMBER_HOT, EMBER_COOL, t);
                p.overlay = (Math.round(150 * (1f - t * 0.4f)) << 24) | lerp(0xFFDC5A, 0xFF6E14, t);
                front++;
                if (random.nextFloat() < 0.04f * flames * dt) {
                    spawnFlame(p.cx, p.cy, scale);
                }
            } else if (burning < EMBER + CHAR) {
                float t = (burning - EMBER) / CHAR;
                p.tint = lerp(EMBER_COOL, CHARCOAL, smoothstep(0f, 0.45f, t));
                p.overlay = (Math.round(120 * (1f - smoothstep(0f, 0.3f, t))) << 24) | 0xFF6E14;
            } else if (burning < EMBER + CHAR + CRUMBLE) {
                float t = (burning - EMBER - CHAR) / CRUMBLE;
                p.tint = lerp(CHARCOAL, ASH, t);
                p.overlay = 0;
                p.alpha = 1f - smoothstep(0.2f, 1f, t);
                p.scale = 1f - 0.9f * smoothstep(0f, 1f, t);
                p.vy += 0.12f * scale * dt;
                p.cy += p.vy * dt;
                if (random.nextFloat() < 0.01f * smoke * dt) {
                    spawnSmoke(p.cx, p.cy, scale);
                }
            } else {
                p.dead = true;
            }
        }
        // Rising flames and sparks across the front, smoke after.
        if (front > 0 && random.nextFloat() < 0.5f * flames * dt) {
            Piece p = pieces.get(random.nextInt(pieces.size()));
            float burning = time - p.a;
            if (burning >= 0 && burning < EMBER) {
                particle(p.cx, p.cy, (random.nextFloat() - 0.5f) * 0.6f * scale, -(0.3f + random.nextFloat() * 0.5f) * scale,
                        1.2f * scale + 0.5f, 6 + random.nextFloat() * 8, 0xFFF0A0, 0xFF5010, 1f).rise = 0.02f * scale;
            }
        }
        float end = lastIgnition + EMBER + CHAR + CRUMBLE;
        if (time > end && time < end + smokeDuration) {
            float fade = 1f - (time - end) / Math.max(1f, smokeDuration);
            if (random.nextFloat() < 0.35f * smoke * fade * dt) {
                spawnSmoke(stage.sx() + random.nextFloat() * stage.sw(), stage.sy() + stage.sh() * (0.3f + random.nextFloat() * 0.7f),
                        scale);
            }
        }
    }

    private void spawnFlame(float x, float y, float scale) {
        Particle p = particle(x + (random.nextFloat() - 0.5f) * 4 * scale, y, (random.nextFloat() - 0.5f) * 0.3f * scale,
                -(0.4f + random.nextFloat() * 0.6f) * scale, (1.5f + random.nextFloat() * 1.5f) * scale + 0.5f,
                8 + random.nextFloat() * 8, 0xFFE070, 0xD02808, 0.95f);
        p.grow = -0.08f * scale;
    }

    private void spawnSmoke(float x, float y, float scale) {
        Particle p = particle(x, y, (random.nextFloat() - 0.5f) * 0.25f * scale, -(0.25f + random.nextFloat() * 0.3f) * scale,
                2f * scale + 1, 24 + random.nextFloat() * 16, 0x4A4642, 0x8A8680, 0.45f);
        p.grow = 0.12f * scale;
    }

    @Override
    public boolean done() {
        return time > lastIgnition + EMBER + CHAR + CRUMBLE + smokeDuration && particles.isEmpty();
    }
}
