package net.shattergui.platform;

import java.util.Collection;

public interface SoundPlayer {
    void buttonClick();

    void closeClick();

    String normalize(String id);

    boolean exists(String id);

    Collection<String> ids();

    void play(String id, float pitch, float volume);
}
