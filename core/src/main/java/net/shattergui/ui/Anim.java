package net.shattergui.ui;

public final class Anim {
    private float value;
    private float target;
    private final float speed;

    public Anim(float start, float speed) {
        this.value = start;
        this.target = start;
        this.speed = speed;
    }

    public Anim target(float newTarget) {
        target = newTarget;
        return this;
    }

    public Anim target(boolean on) {
        return target(on ? 1f : 0f);
    }

    public void snap(float newValue) {
        value = newValue;
        target = newValue;
    }

    public float tick() {
        float dt = UiClock.dt();
        value += (target - value) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - value) < 0.0005f) {
            value = target;
        }
        return value;
    }

    public float value() {
        return value;
    }

    public float targetValue() {
        return target;
    }

    public static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float easeOut(float t) {
        t = Math.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float easeIn(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t;
    }
}
