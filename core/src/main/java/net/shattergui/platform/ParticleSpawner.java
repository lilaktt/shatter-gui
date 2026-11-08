package net.shattergui.platform;

import java.util.Collection;

public interface ParticleSpawner {
    record Simple(String id, String path, Object type) {
    }

    Simple resolveSimple(String id);

    Collection<String> simpleIds();

    boolean allowed();
}
