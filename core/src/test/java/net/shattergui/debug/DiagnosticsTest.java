package net.shattergui.debug;

import net.shattergui.params.FollowCamera;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The copied diagnostics have every field a bug report needs and nothing that identifies the player. */
class DiagnosticsTest {
    private static final String HOME = "C:\\Users\\alice";
    private static final String USER = "alice";

    @Test
    void pathsUserNamesAndMailsAreRemoved() {
        assertEquals("Could not read <path>", Diagnostics.sanitize("Could not read C:\\Users\\alice\\AppData\\x.json", HOME, USER));
        assertEquals("Could not read <path>", Diagnostics.sanitize("Could not read C:/Users/alice/x.json", HOME, USER));
        assertEquals("open <path> failed", Diagnostics.sanitize("open /home/bob/.minecraft/config/a.json failed", "/home/bob", "bob"));
        assertEquals("pack <path>", Diagnostics.sanitize("pack file:///D:/packs/My%20Pack.zip", HOME, USER));
        assertEquals("share <path>", Diagnostics.sanitize("share \\\\nas\\games\\mc", HOME, USER));
        assertEquals("by <email>", Diagnostics.sanitize("by alice.smith+mc@example.com", HOME, USER));
        assertEquals("player <user> joined", Diagnostics.sanitize("player Alice joined", HOME, USER));
        assertEquals("unknown", Diagnostics.sanitize(null, HOME, USER));
    }

    @Test
    void ordinaryValuesStay() {
        // GPU strings have slashes but are not paths; versions, URLs and relative config names stay readable.
        assertEquals("NVIDIA GeForce RTX 3070/PCIe/SSE2", Diagnostics.sanitize("NVIDIA GeForce RTX 3070/PCIe/SSE2", HOME, USER));
        assertEquals("4.6.0 NVIDIA 560.94", Diagnostics.sanitize("4.6.0 NVIDIA 560.94", HOME, USER));
        assertEquals("0.155.3+26.1.2", Diagnostics.sanitize("0.155.3+26.1.2", HOME, USER));
        assertEquals("see https://github.com/x/y", Diagnostics.sanitize("see https://github.com/x/y", HOME, USER));
        assertEquals("config/shatter_gui.json", Diagnostics.sanitize("config/shatter_gui.json", HOME, USER));
    }

    @Test
    void everyFieldIsThere() {
        Map<String, String> env = new LinkedHashMap<>();
        for (String key : List.of("Shatter GUI", "Minecraft", "Fabric Loader", "Fabric API", "Iris", "Sodium",
                "Spatial GUI", "GPU", "Driver (OpenGL)", "Shader pack")) {
            env.put(key, "x");
        }
        String text = Diagnostics.build(env, "fly_from_block", "glass", true, List.of("WARN something at " + HOME + "\\a"));
        for (String key : List.of("Shatter GUI:", "Minecraft:", "Fabric Loader:", "Fabric API:", "Iris:", "Sodium:",
                "Spatial GUI:", "GPU:", "Driver (OpenGL):", "Shader pack:", "Open effect: fly_from_block",
                "Close effect: glass", "Thematic mode: on", "Recent warnings:")) {
            assertTrue(text.contains(key), key + " in\n" + text);
        }
        assertFalse(text.contains(System.getProperty("user.home")), text);
        assertTrue(Diagnostics.build(env, "none", "glass", false, List.of()).contains("Recent warnings: none"));
    }

    @Test
    void warningsKeepTheNewest() {
        RecentWarnings.clear();
        for (int i = 0; i < RecentWarnings.KEPT + 5; i++) {
            RecentWarnings.add("WARN", "line " + i + "\nsecond");
        }
        List<String> all = RecentWarnings.all();
        assertEquals(RecentWarnings.KEPT, all.size());
        assertEquals("WARN line 5 second", all.getFirst());
        RecentWarnings.clear();
    }

    @Test
    void followCameraDefaultsByRole() {
        assertTrue(FollowCamera.follows(FollowCamera.AUTO, null, true));
        assertFalse(FollowCamera.follows(FollowCamera.AUTO, null, false));
        assertFalse(FollowCamera.follows(FollowCamera.AUTO, false, true));
        assertTrue(FollowCamera.follows(FollowCamera.AUTO, true, false));
        assertTrue(FollowCamera.follows(FollowCamera.ALWAYS, false, false));
        assertFalse(FollowCamera.follows(FollowCamera.NEVER, true, true));
    }
}
