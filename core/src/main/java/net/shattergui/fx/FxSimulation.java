package net.shattergui.fx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class FxSimulation {
    public interface Host {
        float[] point(FxDef.Point.Kind kind);

        float sampleMask(String texture, float u, float v);

        float[] gravity();

        boolean hasSprite(String texture);

        void spawnParticle(FxDef.Emitter emitter, float x, float y, float z, float vx, float vy, float vz, float size);

        void playSound(String sound, float volume, float pitch);

        int MOVED = 0;
        int HIT = 1;
        int LANDED = 2;
        int SKIPPED = 3;

        default int move(float[] s, float radius, float bounce, float friction) {
            s[0] += s[3];
            s[1] += s[4];
            s[2] += s[5];
            return MOVED;
        }

        default int inside(float x, float y, float z) {
            return 0;
        }
    }

    static final float CHECK_SPEED = 0.003f;
    static final float SLEEP_SPEED = 0.01f;
    static final float WAKE_SPEED = 0.03f;
    static final int REST_FADE_TICKS = 10;

    public static final class Piece {
        public final int index;
        public final float u;
        public final float v;
        public final boolean inRegion;
        public final float[] triangles;
        public final float trigger;
        public final float start;
        public final float rnd;
        public float x;
        public float y;
        public float z;
        public float vx;
        public float vy;
        public float vz;
        public float angle;
        public float angVel;
        public float tilt;
        public float tiltVel;
        public float tiltAxis;
        public float scale = 1f;
        public float px;
        public float py;
        public float pz;
        public float pAngle;
        public float pTilt;
        public float pScale = 1f;
        public boolean finished;
        public boolean asleep;
        float radius;
        boolean collides;
        byte clip;
        int restTime = -1;
        float maskValue = Float.NaN;
        private long launched;
        private float[][] pathStart;
        private final float restX;
        private final float restY;
        private float hx;
        private float hy;
        private float hz;

        Piece(int index, Fracture.Piece cut, float x, float y, float trigger, float start, float rnd, float tiltAxis) {
            this.index = index;
            this.u = cut.cu();
            this.v = cut.cv();
            this.inRegion = cut.inRegion();
            this.triangles = cut.triangles();
            this.trigger = trigger;
            this.start = start;
            this.rnd = rnd;
            this.tiltAxis = tiltAxis;
            this.x = x;
            this.y = y;
            this.px = x;
            this.py = y;
            this.restX = x;
            this.restY = y;
        }
    }

    public static final class Sprite {
        public final FxDef.Emitter emitter;
        public float x;
        public float y;
        public float z;
        public float px;
        public float py;
        public float pz;
        float vx;
        float vy;
        float vz;
        public int age;
        public final int life;
        public final float rnd;
        public int stillTicks;

        Sprite(FxDef.Emitter emitter, float x, float y, float z, float vx, float vy, float vz, int life, float rnd) {
            this.emitter = emitter;
            this.x = this.px = x;
            this.y = this.py = y;
            this.z = this.pz = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.life = life;
            this.rnd = rnd;
        }

        public float progress(float partialTick) {
            return Math.clamp((age - 1 + partialTick) / life, 0f, 1f);
        }
    }

    private static int liveSpritesTotal;

    public final FxDef def;
    public final float width;
    public final float height;
    public final int duration;
    public final float span;
    private final Host host;
    private final Random random;
    private float motionScale;
    private final float spinScale;
    private final float particleScale;
    private final List<Piece> pieces = new ArrayList<>();
    private final boolean[][] targets;
    private final List<Sprite> sprites = new ArrayList<>();
    private final float[] emitterCarry;
    private final boolean[] soundPlayed;
    private final float[] edgeDir;
    private int time;
    private boolean emitting = true;
    private boolean tracked = true;
    private int vanillaSpawned;
    private int spritePeak;
    private boolean maskMissing;
    private float edgeX;
    private float edgeY;
    private float pEdgeX;
    private float pEdgeY;
    private Recording recording;
    private int playback;

    public FxSimulation(FxDef def, List<Fracture.Piece> cut, float width, float height, Host host, Random random,
                        float durationScale, boolean reduceMotion, float particleScale) {
        this.def = def;
        this.width = width;
        this.height = height;
        this.host = host;
        this.random = random;
        this.duration = Math.clamp(Math.round(def.duration * durationScale), FxLimits.MIN_DURATION, FxLimits.MAX_DURATION * 2);
        this.span = Math.max(1f, duration * (1f - def.trigger.spread()));
        this.motionScale = reduceMotion ? 0.5f : 1f;
        this.spinScale = reduceMotion ? 0.3f : 1f;
        this.particleScale = particleScale;

        float[] triggers = triggerValues(cut);
        for (int i = 0; i < cut.size(); i++) {
            Fracture.Piece c = cut.get(i);
            float t = def.trigger.curve().apply(triggers[i]);
            float start = t * def.trigger.spread() * duration;
            pieces.add(new Piece(i, c, (c.cu() - 0.5f) * width, (0.5f - c.cv()) * height, triggers[i], start,
                    random.nextFloat(), random.nextFloat() * (float) Math.PI * 2));
        }
        targets = new boolean[def.layers.size()][pieces.size()];
        for (int l = 0; l < def.layers.size(); l++) {
            FxDef.Target target = def.layers.get(l).target();
            for (Piece p : pieces) {
                targets[l][p.index] = targets(target, p);
            }
        }
        chooseColliding();
        if (def.appearance.mask() != null) {
            for (Piece p : pieces) {
                float m = host.sampleMask(def.appearance.mask().texture(), p.u, p.v);
                p.maskValue = Float.isNaN(m) ? 1f : (def.appearance.mask().invert() ? 1f - m : m);
            }
        }
        emitterCarry = new float[def.emitters.size()];
        soundPlayed = new boolean[def.sounds.size()];
        edgeDir = edgeDirection(def.edge, random);
        updateEdge();
        pEdgeX = edgeX;
        pEdgeY = edgeY;
    }

    private float[] triggerValues(List<Fracture.Piece> cut) {
        FxDef.Trigger trig = def.trigger;
        float[] values = new float[cut.size()];
        float aspect = width / Math.max(1e-4f, height);
        int seed = random.nextInt();
        for (int i = 0; i < cut.size(); i++) {
            Fracture.Piece c = cut.get(i);
            float value = switch (trig.type()) {
                case "none" -> 0;
                case "radial" -> {
                    float[] uv = pointUv(trig.from());
                    yield (float) Math.hypot((c.cu() - uv[0]) * aspect, c.cv() - uv[1]);
                }
                case "noise" -> fbm(c.cu() * trig.scale() * aspect, c.cv() * trig.scale(), seed);
                case "mask" -> {
                    float m = host.sampleMask(trig.texture(), c.cu(), c.cv());
                    if (Float.isNaN(m)) {
                        maskMissing = true;
                        yield fbm(c.cu() * 4 * aspect, c.cv() * 4, seed);
                    }
                    yield m;
                }
                case "random" -> random.nextFloat();
                default -> c.cu() * trig.dirU() + c.cv() * trig.dirV();
            };
            values[i] = value;
        }
        boolean normalize = switch (trig.type()) {
            case "wave", "radial", "noise" -> true;
            default -> false;
        };
        if (normalize && values.length > 0) {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (float v : values) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            float range = max - min;
            for (int i = 0; i < values.length; i++) {
                values[i] = range < 1e-6f ? 0 : (values[i] - min) / range;
            }
        }
        if (trig.invert()) {
            for (int i = 0; i < values.length; i++) {
                values[i] = 1 - values[i];
            }
        }
        if (trig.noise() > 0) {
            for (int i = 0; i < values.length; i++) {
                Fracture.Piece c = cut.get(i);
                float n = fbm(c.cu() * 9 * aspect, c.cv() * 9, seed + 17) - 0.5f;
                values[i] = Math.clamp(values[i] + n * trig.noise(), 0f, 1f);
            }
        }
        return values;
    }

    private void chooseColliding() {
        List<Piece> candidates = new ArrayList<>();
        for (Piece p : pieces) {
            for (int l = 0; l < def.layers.size(); l++) {
                if (def.layers.get(l).module() == FxDef.Module.COLLIDE && targets[l][p.index]) {
                    candidates.add(p);
                    break;
                }
            }
        }
        if (candidates.size() > FxLimits.MAX_COLLIDING_PIECES) {
            candidates.sort((a, b) -> Float.compare(hash(a.index * 7 + 11), hash(b.index * 7 + 11)));
            candidates = candidates.subList(0, FxLimits.MAX_COLLIDING_PIECES);
        }
        for (Piece p : candidates) {
            p.collides = true;
            float[] t = p.triangles;
            for (int i = 0; i + 1 < t.length; i += 2) {
                p.radius = Math.max(p.radius, (float) Math.hypot((t[i] - p.u) * width, (t[i + 1] - p.v) * height));
            }
        }
    }

    public int collidingCount() {
        int n = 0;
        for (Piece p : pieces) {
            if (p.collides) {
                n++;
            }
        }
        return n;
    }

    private boolean targets(FxDef.Target target, Piece p) {
        boolean in = switch (target.group()) {
            case "wave" -> p.trigger >= target.a() && p.trigger <= target.b();
            case "edge" -> Math.min(Math.min(p.u, 1 - p.u), Math.min(p.v, 1 - p.v)) <= target.a();
            case "random" -> hash(p.index * 31 + target.seed() * 7919) < target.a();
            case "region" -> p.inRegion;
            case "outside" -> !p.inRegion;
            case "alternate" -> p.index % 2 == 0;
            default -> true;
        };
        return in != target.invert();
    }

    private static float[] edgeDirection(FxDef.Edge edge, Random random) {
        if (edge == null) {
            return new float[]{0, 0};
        }
        String dir = edge.direction();
        if ("random".equals(dir)) {
            dir = FxDef.Edge.DIRECTIONS.get(random.nextInt(4));
        }
        return switch (dir) {
            case "left" -> new float[]{-1, 0};
            case "right" -> new float[]{1, 0};
            case "top" -> new float[]{0, 1};
            default -> new float[]{0, -1};
        };
    }

    private static float[] pointUv(FxDef.Point p) {
        return p.kind() == FxDef.Point.Kind.FIXED ? new float[]{p.u(), p.v()} : new float[]{0.5f, 0.5f};
    }

    public float[] point(FxDef.Point p) {
        switch (p.kind()) {
            case FIXED -> {
                return new float[]{(p.u() - 0.5f) * width, (0.5f - p.v()) * height, 0};
            }
            case SOURCE -> {
                float[] source = host.point(FxDef.Point.Kind.SOURCE);
                if (source != null) {
                    return source;
                }
                return point(new FxDef.Point(FxDef.Point.Kind.PLAYER, 0, 0));
            }
            case PLAYER -> {
                float[] player = host.point(FxDef.Point.Kind.PLAYER);
                return player != null ? player : new float[]{0, -height * 0.35f, height * 0.5f};
            }
            default -> {
                return new float[]{0, 0, 0};
            }
        }
    }

    public int time() {
        return time;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<Sprite> sprites() {
        return sprites;
    }

    public boolean maskMissing() {
        return maskMissing;
    }

    public int vanillaSpawned() {
        return vanillaSpawned;
    }

    public int spritePeak() {
        return spritePeak;
    }

    public boolean alive() {
        return time <= (recording != null ? playback + 1 : duration) || !sprites.isEmpty();
    }

    public float shownForward(float partialTick) {
        return shownTime(partialTick);
    }

    public void hurry(int ticks) {
        emitting = false;
        for (Sprite s : sprites) {
            s.age = Math.max(s.age, s.life - ticks);
        }
    }

    public void untracked() {
        tracked = false;
    }

    public void dispose() {
        if (tracked) {
            liveSpritesTotal = Math.max(0, liveSpritesTotal - sprites.size());
        }
        sprites.clear();
        emitting = false;
    }

    public void strength(float factor) {
        motionScale *= Math.clamp(factor, 0f, 4f);
    }

    public void reverse() {
        reverse(duration);
    }

    public void reverse(int ticks) {
        if (recording != null) {
            return;
        }
        playback = Math.clamp(ticks, 1, duration);
        emitting = false;
        Recording rec = new Recording(pieces.size(), duration);
        rec.store(0, pieces, edgeX, edgeY);
        while (time < duration) {
            tickForward(false);
            if (time % rec.stride == 0 || time == duration) {
                rec.store(time, pieces, edgeX, edgeY);
            }
        }
        recording = rec;
        time = 0;
        Arrays.fill(soundPlayed, false);
        for (Piece p : pieces) {
            p.finished = false;
        }
        rec.apply(duration, pieces, false);
        rec.apply(duration, pieces, true);
        edgeX = pEdgeX = rec.edgeAt(duration)[0];
        edgeY = pEdgeY = rec.edgeAt(duration)[1];
    }

    public boolean reversed() {
        return recording != null;
    }

    public int length() {
        return recording != null ? playback + 1 : duration;
    }

    private float forwardAt(float t) {
        return Math.clamp(duration - t * duration / (float) playback, 0f, duration);
    }

    public boolean gone(Piece p) {
        return recording == null && p.finished && time > p.start + span + 1;
    }

    public void tick() {
        if (recording != null) {
            time++;
            float forward = forwardAt(time);
            float before = forwardAt(time - 1);
            recording.apply(before, pieces, true);
            recording.apply(forward, pieces, false);
            float[] prev = recording.edgeAt(before);
            float[] now = recording.edgeAt(forward);
            pEdgeX = prev[0];
            pEdgeY = prev[1];
            edgeX = now[0];
            edgeY = now[1];
            sounds(Math.clamp((float) time / playback, 0f, 1f));
            return;
        }
        tickForward(true);
    }

    private void tickForward(boolean live) {
        time++;
        pEdgeX = edgeX;
        pEdgeY = edgeY;
        updateEdge();
        float g = Math.clamp((float) time / duration, 0f, 1f);
        int n = pieces.size();
        int first = n == 0 ? 0 : Math.floorMod(time * 7919, n);
        for (int i = 0; i < n; i++) {
            tickPiece(pieces.get((first + i) % n), g);
        }
        if (!live) {
            return;
        }
        if (emitting && time <= duration) {
            emit(g);
        }
        tickSprites();
        sounds(g);
    }

    private static final class Recording {
        private static final int FIELDS = 6;
        private static final int MAX_NUMBERS = 1_000_000;
        final int stride;
        private final int[] ticks;
        private final float[][] frames;
        private final float[][] edges;
        private int count;

        Recording(int pieces, int duration) {
            int perTick = Math.max(1, pieces * FIELDS);
            stride = Math.max(1, (int) Math.ceil((double) perTick * (duration + 1) / MAX_NUMBERS));
            int size = duration / stride + 2;
            ticks = new int[size];
            frames = new float[size][];
            edges = new float[size][];
        }

        void store(int tick, List<Piece> pieces, float ex, float ey) {
            float[] f = new float[pieces.size() * FIELDS];
            for (int i = 0; i < pieces.size(); i++) {
                Piece p = pieces.get(i);
                int o = i * FIELDS;
                f[o] = p.x;
                f[o + 1] = p.y;
                f[o + 2] = p.z;
                f[o + 3] = p.angle;
                f[o + 4] = p.tilt;
                f[o + 5] = p.scale;
            }
            if (count > 0 && ticks[count - 1] == tick) {
                count--;
            }
            ticks[count] = tick;
            frames[count] = f;
            edges[count] = new float[]{ex, ey};
            count++;
        }

        private int index(float tick, float[] blend) {
            int i = Math.clamp((int) (tick / stride), 0, count - 1);
            while (i > 0 && ticks[i] > tick) {
                i--;
            }
            while (i + 1 < count && ticks[i + 1] <= tick) {
                i++;
            }
            blend[0] = i + 1 < count ? (tick - ticks[i]) / (float) (ticks[i + 1] - ticks[i]) : 0f;
            return i;
        }

        void apply(float tick, List<Piece> pieces, boolean previous) {
            float[] blend = new float[1];
            int i = index(tick, blend);
            float[] a = frames[i];
            float[] b = blend[0] > 0 ? frames[i + 1] : a;
            float t = blend[0];
            for (int k = 0; k < pieces.size(); k++) {
                Piece p = pieces.get(k);
                int o = k * FIELDS;
                float x = lerp(a[o], b[o], t);
                float y = lerp(a[o + 1], b[o + 1], t);
                float z = lerp(a[o + 2], b[o + 2], t);
                float angle = lerp(a[o + 3], b[o + 3], t);
                float tilt = lerp(a[o + 4], b[o + 4], t);
                float scale = lerp(a[o + 5], b[o + 5], t);
                if (previous) {
                    p.px = x;
                    p.py = y;
                    p.pz = z;
                    p.pAngle = angle;
                    p.pTilt = tilt;
                    p.pScale = scale;
                } else {
                    p.x = x;
                    p.y = y;
                    p.z = z;
                    p.angle = angle;
                    p.tilt = tilt;
                    p.scale = scale;
                }
            }
        }

        float[] edgeAt(float tick) {
            float[] blend = new float[1];
            int i = index(tick, blend);
            float[] a = edges[i];
            float[] b = blend[0] > 0 ? edges[i + 1] : a;
            return new float[]{lerp(a[0], b[0], blend[0]), lerp(a[1], b[1], blend[0])};
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    private void tickPiece(Piece p, float g) {
        p.px = p.x;
        p.py = p.y;
        p.pz = p.z;
        p.pAngle = p.angle;
        p.pTilt = p.tilt;
        p.pScale = p.scale;
        if (p.finished || time < p.start) {
            return;
        }
        float local = (time - p.start) / span;
        float progress = Math.clamp(local, 0f, 1f);
        float scale = 1f;
        float absorb = 1f;
        boolean pathControlled = false;
        FxDef.Layer hinge = null;
        FxDef.Layer collide = null;
        float bounce = 0;
        float friction = 0;
        for (int l = 0; l < def.layers.size(); l++) {
            if (!targets[l][p.index]) {
                continue;
            }
            FxDef.Layer layer = def.layers.get(l);
            float base = layer.global() ? g : progress;
            if (base < layer.from() || base > layer.to()) {
                continue;
            }
            float window = layer.to() - layer.from();
            float wt = window <= 1e-6f ? 1f : (base - layer.from()) / window;
            float r = hash(p.index * 131 + l * 977 + 3);
            switch (layer.module()) {
                case STAY -> {
                    p.vx = 0;
                    p.vy = 0;
                    p.vz = 0;
                    p.angVel = 0;
                    p.tiltVel = 0;
                }
                case LAUNCH -> launch(p, layer, l, wt, r);
                case GRAVITY -> {
                    float s = layer.value("strength").at(wt, r);
                    float[] dir = layer.world() ? host.gravity() : new float[]{0, -1, 0};
                    p.vx += dir[0] * s;
                    p.vy += dir[1] * s;
                    p.vz += dir[2] * s;
                }
                case DRAG -> {
                    float keep = 1f - layer.value("amount").at(wt, r);
                    p.vx *= keep;
                    p.vy *= keep;
                    p.vz *= keep;
                    p.angVel *= keep;
                    p.tiltVel *= keep;
                }
                case ATTRACT -> absorb = Math.min(absorb, attract(p, layer, wt, r));
                case DRIFT -> {
                    float amount = layer.value("amount").at(wt, r) * motionScale;
                    float f = layer.value("frequency").at(wt, r);
                    float out = layer.value("out").at(wt, r) * motionScale;
                    float s = time * f;
                    p.vx += (noise1(s, p.index * 3 + 1) * 2 - 1) * amount;
                    p.vy += (noise1(s, p.index * 3 + 2) * 2 - 1) * amount;
                    p.vz += (noise1(s, p.index * 3 + 3) * 2 - 1) * out;
                }
                case SPIN -> {
                    p.angVel = layer.value("speed").at(wt, r) * spinScale;
                    Value turn = layer.value("turn");
                    if (turn != null) {
                        p.tilt = turn.at(wt, r);
                        p.tiltVel = 0;
                    } else {
                        p.tiltVel = layer.value("tumble").at(wt, r) * spinScale;
                    }
                    if (!Float.isNaN(layer.axis())) {
                        p.tiltAxis = layer.axis();
                    }
                    if (layer.pivot() != null) {
                        hinge = layer;
                    }
                }
                case SCALE -> scale *= layer.value("value").at(wt, r);
                case FORCE -> {
                    p.vx += layer.value("x").at(wt, r) * motionScale;
                    p.vy += layer.value("y").at(wt, r) * motionScale;
                    p.vz += layer.value("z").at(wt, r) * motionScale;
                }
                case FOLLOW_PATH -> {
                    followPath(p, layer, l, wt, r);
                    pathControlled = true;
                }
                case COLLIDE -> {
                    collide = layer;
                    bounce = layer.value("bounce").at(wt, r);
                    friction = layer.value("friction").at(wt, r);
                }
            }
        }
        if (!pathControlled) {
            if (collide != null && p.collides) {
                moveColliding(p, collide, bounce, friction);
            } else {
                p.asleep = false;
                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;
            }
        }
        p.angle += p.angVel;
        p.tilt += p.tiltVel;
        if (hinge != null) {
            hinge(p, hinge.pivot());
        }
        p.scale = scale * absorb;
        if (local >= 1f) {
            p.finished = true;
        }
    }

    private final float[] moveState = new float[6];

    private void moveColliding(Piece p, FxDef.Layer layer, float bounce, float friction) {
        if (p.asleep) {
            float[] down = host.gravity();
            float into = Math.max(0f, p.vx * down[0] + p.vy * down[1] + p.vz * down[2]);
            float ox = p.vx - down[0] * into;
            float oy = p.vy - down[1] * into;
            float oz = p.vz - down[2] * into;
            if (ox * ox + oy * oy + oz * oz < WAKE_SPEED * WAKE_SPEED) {
                stop(p);
                return;
            }
            p.asleep = false;
        }
        if (p.clip == 0) {
            int in = host.inside(p.x + edgeX, p.y + edgeY, p.z);
            if (in < 0) {
                return;
            }
            p.clip = (byte) (in == 1 ? 1 : 2);
        }
        if (p.clip == 1) {
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            if (host.inside(p.x + edgeX, p.y + edgeY, p.z) == 0) {
                p.clip = 2;
            }
            return;
        }
        float speed2 = p.vx * p.vx + p.vy * p.vy + p.vz * p.vz;
        if (speed2 < CHECK_SPEED * CHECK_SPEED) {
            return;
        }
        float[] s = moveState;
        s[0] = p.x + edgeX;
        s[1] = p.y + edgeY;
        s[2] = p.z;
        s[3] = p.vx;
        s[4] = p.vy;
        s[5] = p.vz;
        int contact = host.move(s, p.radius * Math.max(0f, p.scale), bounce, friction);
        if (contact == Host.SKIPPED) {
            return;
        }
        p.x = s[0] - edgeX;
        p.y = s[1] - edgeY;
        p.z = s[2];
        p.vx = s[3];
        p.vy = s[4];
        p.vz = s[5];
        if (contact == Host.HIT) {
            p.angVel *= 0.7f;
            p.tiltVel *= 0.7f;
        } else if (contact == Host.LANDED) {
            p.angVel *= 0.5f;
            p.tiltVel *= 0.5f;
            if (p.vx * p.vx + p.vy * p.vy + p.vz * p.vz < SLEEP_SPEED * SLEEP_SPEED) {
                p.asleep = true;
                stop(p);
                if ("fade".equals(layer.rest()) && p.restTime < 0) {
                    p.restTime = time;
                }
            }
        }
    }

    private static void stop(Piece p) {
        p.vx = 0;
        p.vy = 0;
        p.vz = 0;
        p.angVel = 0;
        p.tiltVel = 0;
    }

    private void launch(Piece p, FxDef.Layer layer, int l, float wt, float r) {
        boolean once = layer.once();
        long bit = 1L << Math.min(l, 63);
        if (once && (p.launched & bit) != 0) {
            return;
        }
        p.launched |= bit;
        float[] from = point(layer.point());
        float dx = p.x - from[0];
        float dy = p.y - from[1];
        float len = (float) Math.hypot(dx, dy);
        double angle = len < 1e-5f ? r * Math.PI * 2 : Math.atan2(dy, dx);
        angle += (hash(p.index * 17 + l) - 0.5f) * layer.value("spread").at(wt, r);
        float speed = layer.value("speed").at(wt, r) * motionScale;
        p.vx += (float) Math.cos(angle) * speed;
        p.vy += (float) Math.sin(angle) * speed;
        p.vz += layer.value("out").at(wt, r) * motionScale;
    }

    private void hinge(Piece p, float[] pivot) {
        float dx = p.restX - (pivot[0] - 0.5f) * width;
        float dy = p.restY - (0.5f - pivot[1]) * height;
        float[] v = rotate(dx, dy, 0, p.tiltAxis, p.tilt, p.angle);
        float nx = v[0] - dx;
        float ny = v[1] - dy;
        float nz = v[2];
        p.x += nx - p.hx;
        p.y += ny - p.hy;
        p.z += nz - p.hz;
        p.hx = nx;
        p.hy = ny;
        p.hz = nz;
    }

    public static float[] rotate(float x, float y, float z, float axis, float tilt, float angle) {
        if (tilt != 0f) {
            float ax = (float) Math.cos(axis);
            float ay = (float) Math.sin(axis);
            float c = (float) Math.cos(tilt);
            float s = (float) Math.sin(tilt);
            float dot = ax * x + ay * y;
            float cx = ay * z;
            float cy = -ax * z;
            float cz = ax * y - ay * x;
            float rx = x * c + cx * s + ax * dot * (1 - c);
            float ry = y * c + cy * s + ay * dot * (1 - c);
            float rz = z * c + cz * s;
            x = rx;
            y = ry;
            z = rz;
        }
        if (angle != 0f) {
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
            float rx = x * c - y * s;
            float ry = x * s + y * c;
            x = rx;
            y = ry;
        }
        return new float[]{x, y, z};
    }

    private float attract(Piece p, FxDef.Layer layer, float wt, float r) {
        float[] to = point(layer.point());
        float dx = to[0] - p.x;
        float dy = to[1] - p.y;
        float dz = to[2] - p.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float strength = layer.value("strength").at(wt, r) * motionScale;
        if (dist > 1e-4f) {
            float nx = dx / dist;
            float ny = dy / dist;
            float nz = dz / dist;
            p.vx += nx * strength;
            p.vy += ny * strength;
            p.vz += nz * strength;
            float swirl = layer.value("swirl").at(wt, r);
            p.vx += -ny * swirl * strength;
            p.vy += nx * swirl * strength;
        }
        float radius = layer.value("absorb").at(wt, r) * (float) Math.hypot(width, height);
        return radius <= 1e-5f ? 1f : Math.clamp(dist / radius, 0f, 1f);
    }

    private void followPath(Piece p, FxDef.Layer layer, int l, float wt, float r) {
        if (p.pathStart == null) {
            p.pathStart = new float[def.layers.size()][];
        }
        if (p.pathStart[l] == null) {
            p.pathStart[l] = new float[]{p.x - p.hx, p.y - p.hy, p.z - p.hz};
        }
        float progress = layer.value("progress").at(wt, r);
        float[] offset = spline(layer.path(), progress);
        float nx = p.pathStart[l][0] + offset[0] * width + p.hx;
        float ny = p.pathStart[l][1] - offset[1] * width + p.hy;
        float nz = p.pathStart[l][2] + offset[2] * width + p.hz;
        p.vx = nx - p.x;
        p.vy = ny - p.y;
        p.vz = nz - p.z;
        p.x = nx;
        p.y = ny;
        p.z = nz;
    }

    static float[] spline(float[][] points, float t) {
        int n = points.length + 1;
        float[][] all = new float[n][];
        all[0] = new float[]{0, 0, 0};
        System.arraycopy(points, 0, all, 1, points.length);
        if (n == 1) {
            return all[0];
        }
        float f = Math.clamp(t, 0f, 1f) * (n - 1);
        int i = Math.min(n - 2, (int) Math.floor(f));
        float s = f - i;
        float[] p0 = all[Math.max(0, i - 1)];
        float[] p1 = all[i];
        float[] p2 = all[i + 1];
        float[] p3 = all[Math.min(n - 1, i + 2)];
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            out[k] = 0.5f * (2 * p1[k] + (-p0[k] + p2[k]) * s + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * s * s
                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * s * s * s);
        }
        return out;
    }

    private void updateEdge() {
        FxDef.Edge edge = def.edge;
        if (edge == null) {
            return;
        }
        float g = Math.clamp((float) time / duration, 0f, 1f);
        float distance = edge.distance() * Math.max(width, height);
        float f = 0;
        if (edge.exit() && g > edge.exitStart()) {
            f += edge.curve().apply((g - edge.exitStart()) / (1 - edge.exitStart()));
        }
        if (edge.enter() && g < edge.enterTime()) {
            f += 1f - Easing.EASE_OUT_CUBIC.apply(g / edge.enterTime());
        }
        edgeX = edgeDir[0] * distance * f;
        edgeY = edgeDir[1] * distance * f;
    }

    public float[] edgeOffset(float partialTick) {
        return new float[]{pEdgeX + (edgeX - pEdgeX) * partialTick, pEdgeY + (edgeY - pEdgeY) * partialTick};
    }

    private void emit(float g) {
        for (int e = 0; e < def.emitters.size(); e++) {
            FxDef.Emitter emitter = def.emitters.get(e);
            if (g < emitter.from() || g > emitter.to()) {
                continue;
            }
            if (emitter.sprite() != null && !host.hasSprite(emitter.sprite())) {
                continue;
            }
            float window = emitter.to() - emitter.from();
            float wt = window <= 1e-6f ? 1f : (g - emitter.from()) / window;
            float count = emitter.rate().at(wt, 0.5f) * particleScale + emitterCarry[e];
            int n = (int) count;
            emitterCarry[e] = count - n;
            boolean firstTick = (time - 1f) / duration < emitter.from() || time == 1;
            if (firstTick && emitter.burst() > 0) {
                n += Math.round(emitter.burst() * particleScale);
            }
            for (int i = 0; i < n; i++) {
                spawn(emitter, wt);
            }
        }
    }

    private void spawn(FxDef.Emitter emitter, float wt) {
        float[] at = spawnPoint(emitter);
        if (at == null) {
            return;
        }
        float spread = emitter.spread().at(wt, random.nextFloat());
        float vx = emitter.velocity()[0] * motionScale + (float) random.nextGaussian() * spread;
        float vy = emitter.velocity()[1] * motionScale + (float) random.nextGaussian() * spread;
        float vz = emitter.velocity()[2] * motionScale + (float) random.nextGaussian() * spread * 0.5f;
        if (emitter.particle() != null) {
            if (vanillaSpawned >= FxLimits.MAX_VANILLA_PER_WAVE || vanillaThisTick() >= FxLimits.MAX_VANILLA_PER_TICK) {
                return;
            }
            vanillaTick = time;
            vanillaCount++;
            vanillaSpawned++;
            host.spawnParticle(emitter, at[0], at[1], at[2], vx, vy, vz, emitter.size().at(0, random.nextFloat()));
            return;
        }
        int own = 0;
        for (Sprite s : sprites) {
            if (s.emitter == emitter) {
                own++;
            }
        }
        if (own >= emitter.max() || sprites.size() >= FxLimits.MAX_SPRITES_PER_WAVE
                || tracked && liveSpritesTotal >= FxLimits.MAX_SPRITES_TOTAL) {
            return;
        }
        float rnd = random.nextFloat();
        int life = Math.max(1, Math.round(emitter.life().at(wt, rnd)));
        sprites.add(new Sprite(emitter, at[0], at[1], at[2], vx, vy, vz, life, rnd));
        if (tracked) {
            liveSpritesTotal++;
        }
        spritePeak = Math.max(spritePeak, sprites.size());
    }

    private int vanillaTick = -1;
    private int vanillaCount;

    private int vanillaThisTick() {
        if (vanillaTick != time) {
            vanillaCount = 0;
        }
        return vanillaCount;
    }

    private float[] spawnPoint(FxDef.Emitter emitter) {
        switch (emitter.zone()) {
            case "point" -> {
                return withEdge(point(emitter.at()));
            }
            case "line" -> {
                float[] a = point(emitter.lineFrom());
                float[] b = point(emitter.lineTo());
                float t = random.nextFloat();
                return withEdge(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
            }
            case "edge" -> {
                float t = random.nextFloat() * 2 * (width + height);
                float x;
                float y;
                if (t < width) {
                    x = t - width / 2;
                    y = height / 2;
                } else if (t < width + height) {
                    x = width / 2;
                    y = height / 2 - (t - width);
                } else if (t < 2 * width + height) {
                    x = width / 2 - (t - width - height);
                    y = -height / 2;
                } else {
                    x = -width / 2;
                    y = -height / 2 + (t - 2 * width - height);
                }
                return withEdge(new float[]{x, y, 0});
            }
            case "front" -> {
                Piece p = null;
                for (int tries = 0; tries < 12 && !pieces.isEmpty(); tries++) {
                    Piece candidate = pieces.get(random.nextInt(pieces.size()));
                    if (!candidate.finished && time >= candidate.start && time - candidate.start <= 3) {
                        p = candidate;
                        break;
                    }
                }
                return p == null ? null : withEdge(new float[]{p.x, p.y, p.z});
            }
            default -> {
                Piece p = null;
                for (int tries = 0; tries < 8 && !pieces.isEmpty(); tries++) {
                    Piece candidate = pieces.get(random.nextInt(pieces.size()));
                    if (!candidate.finished) {
                        p = candidate;
                        break;
                    }
                }
                if (p == null) {
                    return null;
                }
                float jitter = Math.min(width, height) * 0.03f;
                return withEdge(new float[]{p.x + (random.nextFloat() - 0.5f) * jitter,
                        p.y + (random.nextFloat() - 0.5f) * jitter, p.z});
            }
        }
    }

    private float[] withEdge(float[] p) {
        p[0] += edgeX;
        p[1] += edgeY;
        return p;
    }

    private void tickSprites() {
        float[] down = host.gravity();
        int before = sprites.size();
        sprites.removeIf(s -> {
            s.age++;
            if (s.age > s.life) {
                return true;
            }
            s.px = s.x;
            s.py = s.y;
            s.pz = s.z;
            float gravity = s.emitter.gravity();
            s.vx += down[0] * gravity;
            s.vy += down[1] * gravity;
            s.vz += down[2] * gravity;
            float keep = 1f - s.emitter.drag();
            s.vx *= keep;
            s.vy *= keep;
            s.vz *= keep;
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            s.stillTicks = net.shattergui.shatter.Settle.stillTicks(s.stillTicks,
                    net.shattergui.shatter.Settle.moved(s.vx, s.vy, s.vz, 0f));
            return false;
        });
        if (tracked) {
            liveSpritesTotal = Math.max(0, liveSpritesTotal - (before - sprites.size()));
        }
    }

    private void sounds(float g) {
        for (int i = 0; i < def.sounds.size(); i++) {
            if (soundPlayed[i]) {
                continue;
            }
            FxDef.Sound sound = def.sounds.get(i);
            boolean back = recording != null;
            boolean now = switch (sound.at()) {
                case "start" -> back ? time >= playback : time == 1;
                case "end" -> back ? time == 1 : time >= duration;
                default -> back ? 1f - g <= sound.time() : g >= sound.time();
            };
            if (now) {
                soundPlayed[i] = true;
                host.playSound(sound.sound(), sound.volume(), sound.pitch());
            }
        }
    }

    private float shownTime(float partialTick) {
        float t = time - 1 + partialTick;
        return recording != null ? forwardAt(t) : t;
    }

    public float progress(Piece p, float partialTick) {
        return Math.clamp((shownTime(partialTick) - p.start) / span, 0f, 1f);
    }

    public float globalProgress(float partialTick) {
        return Math.clamp(shownTime(partialTick) / duration, 0f, 1f);
    }

    public float mosaic(float partialTick) {
        Value m = def.appearance.mosaic();
        return m.isConstant() && m.at(0, 0) == 0 ? 0 : m.at(globalProgress(partialTick), 0.5f);
    }

    public int color(Piece p, float partialTick) {
        if (recording == null ? p.finished && time - 1 + partialTick >= p.start + span
                : shownTime(partialTick) >= p.start + span) {
            return 0;
        }
        FxDef.Appearance look = def.appearance;
        float progress = progress(p, partialTick);
        int rgb = look.color().at(look.colorGlobal() ? globalProgress(partialTick) : progress);
        if (!look.palette().isEmpty()) {
            int tinted = multiply(rgb, look.palette().get((int) (hash(p.index * 613 + 29) * look.palette().size())));
            rgb = Gradient.lerp(rgb, tinted, Math.clamp(progress / 0.15f, 0f, 1f));
        }
        float alpha = (rgb >>> 24) / 255f * look.alpha().at(progress, p.rnd) * maskFactor(p, progress)
                * restFactor(p, partialTick);
        return (Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int multiply(int a, int b) {
        int alpha = (a >>> 24) * (b >>> 24) / 255;
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return alpha << 24 | r << 16 | g << 8 | bl;
    }

    public float emissive(Piece p, float partialTick) {
        return def.appearance.emissive().at(progress(p, partialTick), p.rnd);
    }

    public int emissiveColor(Piece p, float partialTick) {
        return def.appearance.emissiveColor().at(progress(p, partialTick));
    }

    public float overlay(Piece p, int k, float partialTick) {
        float progress = progress(p, partialTick);
        return def.appearance.overlays().get(k).opacity().at(progress, p.rnd) * maskFactor(p, progress)
                * restFactor(p, partialTick);
    }

    private float restFactor(Piece p, float partialTick) {
        if (p.restTime < 0) {
            return 1f;
        }
        return Math.clamp(1f - (shownTime(partialTick) - p.restTime) / REST_FADE_TICKS, 0f, 1f);
    }

    private float maskFactor(Piece p, float progress) {
        FxDef.Mask mask = def.appearance.mask();
        if (mask == null || Float.isNaN(p.maskValue)) {
            return 1f;
        }
        float th = mask.threshold().at(progress, p.rnd);
        float s = mask.softness();
        return Math.clamp((p.maskValue - th * (1 + s)) / s + 1, 0f, 1f);
    }

    public float scale(Piece p, float partialTick) {
        return p.pScale + (p.scale - p.pScale) * partialTick;
    }

    static float hash(int n) {
        int h = n * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float hash2(int x, int y, int seed) {
        return hash(x * 73856093 ^ y * 19349663 ^ seed * 83492791);
    }

    static float noise2(float x, float y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;
        float u = xf * xf * (3 - 2 * xf);
        float v = yf * yf * (3 - 2 * yf);
        float a = hash2(xi, yi, seed);
        float b = hash2(xi + 1, yi, seed);
        float c = hash2(xi, yi + 1, seed);
        float d = hash2(xi + 1, yi + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    static float fbm(float x, float y, int seed) {
        return noise2(x, y, seed) * 0.6f + noise2(x * 2.1f, y * 2.1f, seed + 1) * 0.3f + noise2(x * 4.3f, y * 4.3f, seed + 2) * 0.1f;
    }

    static float noise1(float x, int seed) {
        int xi = (int) Math.floor(x);
        float xf = x - xi;
        float u = xf * xf * (3 - 2 * xf);
        return hash2(xi, seed, 7) + (hash2(xi + 1, seed, 7) - hash2(xi, seed, 7)) * u;
    }

    public static int liveSpritesTotal() {
        return liveSpritesTotal;
    }
}
