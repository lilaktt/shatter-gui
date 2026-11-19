package net.shattergui.compat.spatial;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Vector3f;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class SpatialHandles {
    record Quad(Vector3f centerOffset, Vector3f right, Vector3f up, Vector3f normal, float halfWidth, float halfHeight) {
    }

    private static SpatialHandles instance;

    private final Method renderer;
    private final Method isEnabled;
    private final Method getFirstPerson;
    private final Method setFirstPerson;
    private final Method hookedScreen;
    private final Method plane;
    private final Method holder;
    private final Method texture;
    private final Method setScreenOpenTime;
    private final Method updateQuadBasis;
    private final Method getQuadBasis;
    private final Method getCylinderBasis;
    private final Method isClosing;
    private final Method isClosingFirstPerson;
    private final Method[] quad;

    private SpatialHandles(SpatialGuiProbe.Api api) throws ReflectiveOperationException {
        ClassLoader loader = SpatialHandles.class.getClassLoader();
        Class<?> client = Class.forName(SpatialGuiProbe.CLIENT.replace('/', '.'), true, loader);
        renderer = client.getMethod("renderer");
        isEnabled = client.getMethod("isEnabled");
        getFirstPerson = client.getMethod("getEffectiveFirstPersonMode");
        setFirstPerson = client.getMethod("setEffectiveFirstPersonMode", boolean.class);
        Class<?> rendererType = renderer.getReturnType();
        hookedScreen = rendererType.getMethod("getHookedScreen");
        plane = rendererType.getMethod(api.planeGetter());
        holder = rendererType.getMethod(api.holderGetter());
        texture = holder.getReturnType().getMethod(api.textureGetter());
        Class<?> planeType = plane.getReturnType();
        setScreenOpenTime = planeType.getMethod("setScreenOpenTime", long.class);
        updateQuadBasis = planeType.getMethod("updateQuadBasis");
        getQuadBasis = planeType.getMethod("getQuadBasis");
        getCylinderBasis = planeType.getMethod("getCylinderBasis");
        isClosing = planeType.getMethod("isClosing");
        isClosingFirstPerson = planeType.getMethod("isClosingFirstPerson");
        Class<?> quadType = getQuadBasis.getReturnType();
        quad = new Method[]{quadType.getMethod("centerOffset"), quadType.getMethod("right"), quadType.getMethod("up"),
                quadType.getMethod("normal"), quadType.getMethod("halfWidth"), quadType.getMethod("halfHeight")};
    }

    static synchronized SpatialHandles get() {
        if (instance == null) {
            try {
                instance = new SpatialHandles(SpatialGuiProbe.api());
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Spatial GUI API changed after the start-up check: " + e, e);
            }
        }
        return instance;
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(method + " is not accessible", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    Object renderer() {
        return call(renderer, null);
    }

    boolean enabled() {
        return (Boolean) call(isEnabled, null);
    }

    boolean firstPersonMode() {
        return (Boolean) call(getFirstPerson, null);
    }

    void firstPersonMode(boolean value) {
        call(setFirstPerson, null, value);
    }

    Screen hookedScreen(Object renderer) {
        return (Screen) call(hookedScreen, renderer);
    }

    Object plane(Object renderer) {
        return call(plane, renderer);
    }

    TextureTarget texture(Object renderer) {
        Object holder = call(this.holder, renderer);
        return holder == null ? null : (TextureTarget) call(texture, holder);
    }

    void setScreenOpenTime(Object plane, long time) {
        call(setScreenOpenTime, plane, time);
    }

    void updateQuadBasis(Object plane) {
        call(updateQuadBasis, plane);
    }

    Quad quadBasis(Object plane) {
        Object basis = call(getQuadBasis, plane);
        if (basis == null) {
            return null;
        }
        return new Quad((Vector3f) call(quad[0], basis), (Vector3f) call(quad[1], basis), (Vector3f) call(quad[2], basis),
                (Vector3f) call(quad[3], basis), (Float) call(quad[4], basis), (Float) call(quad[5], basis));
    }

    boolean curved(Object plane) {
        return call(getCylinderBasis, plane) != null;
    }

    boolean closing(Object plane) {
        return (Boolean) call(isClosing, plane);
    }

    boolean closingFirstPerson(Object plane) {
        return (Boolean) call(isClosingFirstPerson, plane);
    }
}
