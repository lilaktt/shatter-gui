package net.shattergui.compat.spatial;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class SpatialGuiProbe {
    public static final String CLIENT = "org/tastytrash/spatialGUI/client/SpatialGUIClient";
    public static final String BLUR = "org/tastytrash/spatialGUI/render/WorldBlurRenderer";
    public static final List<String> PLANE_TARGETS = List.of("org/tastytrash/spatialGUI/render/WorldScreenRenderer",
            "org/tastytrash/spatialGUI/render/InventoryRenderer");
    private static final String[] MOD_IDS = {"spatial_gui", "spatial-gui"};
    private static final String POSE_STACK = "Lcom/mojang/blaze3d/vertex/PoseStack;";
    private static final String SCREEN = "Lnet/minecraft/client/gui/screens/Screen;";
    private static final String TEXTURE_TARGET = "Lcom/mojang/blaze3d/pipeline/TextureTarget;";
    private static final String VECTOR = "()Lorg/joml/Vector3f;";
    private static final List<String> TEXTURE_GETTERS = List.of("getInventoryTarget", "getTarget");

    public record Api(String version, String plane, String planeGetter, String holderGetter, String textureGetter,
                      boolean blur, List<String> missing) {
        public boolean installed() {
            return version != null;
        }

        public boolean usable() {
            return installed() && missing.isEmpty();
        }

        public String planeClassName() {
            return plane == null ? null : plane.replace('/', '.');
        }

        public String describe() {
            String simple = plane == null ? "?" : plane.substring(plane.lastIndexOf('/') + 1);
            return "plane " + simple + " via " + planeGetter + "(), texture via " + holderGetter + "()." + textureGetter + "()"
                    + (blur ? "" : ", no blur hook");
        }
    }

    private static Api api;

    private SpatialGuiProbe() {
    }

    public static synchronized Api api() {
        if (api == null) {
            api = detect();
        }
        return api;
    }

    private static Api detect() {
        Optional<ModContainer> mod = Optional.empty();
        for (String id : MOD_IDS) {
            mod = FabricLoader.getInstance().getModContainer(id);
            if (mod.isPresent()) {
                break;
            }
        }
        if (mod.isEmpty()) {
            return new Api(null, null, null, null, null, false, List.of());
        }
        ModContainer container = mod.get();
        String version = container.getMetadata().getVersion().getFriendlyString();
        try {
            return probe(version, name -> read(container, name));
        } catch (RuntimeException e) {
            return new Api(version, null, null, null, null, false, List.of("readable classes (" + e + ")"));
        }
    }

    private static ClassNode read(ModContainer container, String name) {
        Optional<Path> path = container.findPath(name + ".class");
        if (path.isEmpty() || !Files.isRegularFile(path.get())) {
            return null;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(path.get())).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    static Api probe(String version, Function<String, ClassNode> loader) {
        Map<String, ClassNode> cache = new HashMap<>();
        Function<String, ClassNode> classes = name -> cache.computeIfAbsent(name, n -> {
            ClassNode node = loader.apply(n);
            return node == null ? new ClassNode() : node;
        });
        List<String> missing = new ArrayList<>();
        ClassNode client = classes.apply(CLIENT);
        if (client.name == null) {
            return new Api(version, null, null, null, null, false, List.of(simple(CLIENT)));
        }
        MethodNode rendererMethod = method(client, "renderer", null, true);
        need(missing, client, "isEnabled", "()Z", true);
        need(missing, client, "getEffectiveFirstPersonMode", "()Z", true);
        need(missing, client, "setEffectiveFirstPersonMode", "(Z)V", true);
        if (rendererMethod == null || !rendererMethod.desc.startsWith("()L")) {
            missing.add(simple(CLIENT) + ".renderer()");
            return new Api(version, null, null, null, null, false, missing);
        }
        ClassNode renderer = classes.apply(Type.getReturnType(rendererMethod.desc).getInternalName());
        if (renderer.name == null) {
            missing.add(Type.getReturnType(rendererMethod.desc).getClassName());
            return new Api(version, null, null, null, null, false, missing);
        }
        need(missing, renderer, "getHookedScreen", "()" + SCREEN, false);

        String plane = null;
        String planeGetter = null;
        List<String> bestPlaneGaps = null;
        String holderGetter = null;
        String textureGetter = null;
        for (MethodNode m : renderer.methods) {
            if ((m.access & Opcodes.ACC_PUBLIC) == 0 || (m.access & Opcodes.ACC_STATIC) != 0 || !m.desc.startsWith("()L")) {
                continue;
            }
            String type = Type.getReturnType(m.desc).getInternalName();
            if (!type.startsWith("org/tastytrash/")) {
                continue;
            }
            ClassNode candidate = classes.apply(type);
            if (candidate.name == null) {
                continue;
            }
            List<String> gaps = planeGaps(candidate, classes);
            if (gaps.isEmpty() && plane == null) {
                plane = type;
                planeGetter = m.name;
            } else if (!gaps.isEmpty() && looksLikePlane(candidate) && (bestPlaneGaps == null || gaps.size() < bestPlaneGaps.size())) {
                bestPlaneGaps = gaps;
            }
            if (holderGetter == null) {
                for (String getter : TEXTURE_GETTERS) {
                    if (method(candidate, getter, "()" + TEXTURE_TARGET, false) != null) {
                        holderGetter = m.name;
                        textureGetter = getter;
                        break;
                    }
                }
            }
        }
        if (plane == null) {
            if (bestPlaneGaps != null) {
                missing.addAll(bestPlaneGaps);
            } else {
                missing.add("a screen plane class (" + String.join(" or ", PLANE_TARGETS.stream().map(SpatialGuiProbe::simple).toList())
                        + ") reachable from " + simple(renderer.name));
            }
        } else if (!PLANE_TARGETS.contains(plane)) {
            missing.add("Shatter GUI hooks for " + simple(plane) + " (known: "
                    + String.join(", ", PLANE_TARGETS.stream().map(SpatialGuiProbe::simple).toList()) + ")");
        }
        if (holderGetter == null) {
            missing.add("the screen texture (" + simple(renderer.name) + " getter of TextureTarget getTarget())");
        }
        ClassNode blurNode = classes.apply(BLUR);
        boolean blur = blurNode.name != null && method(blurNode, "applyBlur", "(I)V", true) != null;
        return new Api(version, plane, planeGetter, holderGetter, textureGetter, blur, List.copyOf(missing));
    }

    private static boolean looksLikePlane(ClassNode node) {
        return field(node, "closeStartElapsed", "J") != null || method(node, "computeScreenPose", null, false) != null
                || method(node, "getQuadBasis", null, false) != null;
    }

    private static List<String> planeGaps(ClassNode plane, Function<String, ClassNode> classes) {
        List<String> gaps = new ArrayList<>();
        String owner = simple(plane.name);
        FieldNode pose = field(plane, "WORLD_POSE_STACK", POSE_STACK);
        if (pose == null || (pose.access & Opcodes.ACC_STATIC) == 0) {
            gaps.add(owner + ".WORLD_POSE_STACK");
        }
        for (String name : List.of("closeStartElapsed", "closeDurationMs")) {
            if (field(plane, name, "J") == null) {
                gaps.add(owner + "." + name);
            }
        }
        MethodNode screenPose = method(plane, "computeScreenPose", null, false);
        if (screenPose == null || !screenPose.desc.startsWith("(" + POSE_STACK + "Z)L")) {
            gaps.add(owner + ".computeScreenPose(PoseStack, boolean)");
        }
        for (String[] m : new String[][]{{"getFadeAlpha", "()F"}, {"updateQuadBasis", "()V"}, {"isClosing", "()Z"},
                {"isClosingFirstPerson", "()Z"}, {"setScreenOpenTime", "(J)V"}}) {
            if (method(plane, m[0], m[1], false) == null) {
                gaps.add(owner + "." + m[0] + "()");
            }
        }
        MethodNode cylinder = method(plane, "getCylinderBasis", null, false);
        if (cylinder == null || !cylinder.desc.startsWith("()L")) {
            gaps.add(owner + ".getCylinderBasis()");
        }
        MethodNode quad = method(plane, "getQuadBasis", null, false);
        if (quad == null || !quad.desc.startsWith("()L")) {
            gaps.add(owner + ".getQuadBasis()");
        } else {
            ClassNode basis = classes.apply(Type.getReturnType(quad.desc).getInternalName());
            String basisName = simple(Type.getReturnType(quad.desc).getInternalName());
            if (basis.name == null) {
                gaps.add(basisName);
            } else {
                for (String v : List.of("centerOffset", "right", "up", "normal")) {
                    if (method(basis, v, VECTOR, false) == null) {
                        gaps.add(basisName + "." + v + "()");
                    }
                }
                for (String f : List.of("halfWidth", "halfHeight")) {
                    if (method(basis, f, "()F", false) == null) {
                        gaps.add(basisName + "." + f + "()");
                    }
                }
            }
        }
        return gaps;
    }

    private static void need(List<String> missing, ClassNode owner, String name, String desc, boolean isStatic) {
        if (method(owner, name, desc, isStatic) == null) {
            missing.add(simple(owner.name) + "." + name + "()");
        }
    }

    private static MethodNode method(ClassNode owner, String name, String desc, boolean isStatic) {
        for (MethodNode m : owner.methods) {
            if (m.name.equals(name) && (desc == null || m.desc.equals(desc))
                    && ((m.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
                return m;
            }
        }
        return null;
    }

    private static FieldNode field(ClassNode owner, String name, String desc) {
        for (FieldNode f : owner.fields) {
            if (f.name.equals(name) && f.desc.equals(desc)) {
                return f;
            }
        }
        return null;
    }

    private static String simple(String internalName) {
        String name = internalName.substring(internalName.lastIndexOf('/') + 1);
        return name.replace('$', '.');
    }
}
