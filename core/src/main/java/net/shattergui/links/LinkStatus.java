package net.shattergui.links;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Whether the mod's pages are up, for the indicators next to the buttons in "About": one GET per page and session, off the
 * render thread, 3 seconds at most. Only a clear answer counts: 200 = the page is there (green), 404 = not published yet or
 * still in moderation (yellow). Anything else (no network, a timeout, 403, 429, 5xx, a redirect) is "could not check"
 * (grey), never yellow. CurseForge has no public API without a key, so it is never checked (no indicator, no warning).
 *
 * <p>Nothing is sent but the request itself: the User-Agent is {@code lilaktt/shatter-gui/<version>}, no e-mail, no
 * user data. The player can turn it off ({@code linkStatusCheck}).
 */
public final class LinkStatus {
    /** What a page's indicator shows. */
    public enum State {
        /** Not asked (CurseForge, the setting is off) or not answered yet: no indicator. */
        NONE,
        /** The request is under way: grey, pulsing. */
        CHECKING,
        /** 200: green. */
        AVAILABLE,
        /** 404: yellow, "not published yet or in moderation". */
        NOT_PUBLISHED,
        /** Anything else: grey. */
        UNKNOWN
    }

    /** The pages that can be checked, with the API address that answers for each. */
    public enum Page {
        MODRINTH("https://api.modrinth.com/v2/project/shattergui"),
        GITHUB("https://api.github.com/repos/lilaktt/shatter-gui");

        public final String api;

        Page(String api) {
            this.api = api;
        }
    }

    /** One GET: the status code, or an exception (no network, timeout). */
    public interface Http {
        int get(String url, String userAgent, Duration timeout) throws IOException, InterruptedException;
    }

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** The real client: {@link HttpClient}, no redirects followed (a redirect is not a clear answer). */
    public static final Http JDK = new Http() {
        private HttpClient client;

        @Override
        public int get(String url, String userAgent, Duration timeout) throws IOException, InterruptedException {
            if (client == null) {
                client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("User-Agent", userAgent)
                    .header("Accept", "application/json").GET().build();
            // The whole request within the timeout (connecting and the answer together).
            CompletableFuture<HttpResponse<Void>> response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
            try {
                return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS).statusCode();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                response.cancel(true);
                throw new IOException(e);
            }
        }
    };

    private static final Map<Page, State> STATES = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Shatter GUI link status");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Http http = JDK;

    private LinkStatus() {
    }

    /** What a status code means. */
    public static State fromStatus(int code) {
        return switch (code) {
            case 200 -> State.AVAILABLE;
            case 404 -> State.NOT_PUBLISHED;
            default -> State.UNKNOWN;
        };
    }

    /** The User-Agent of the requests: the project and its version, nothing about the player. */
    public static String userAgent(String version) {
        return "lilaktt/shatter-gui/" + version;
    }

    /** The state of a page now; NONE until {@link #check} was called for it this session. Any thread. */
    public static State state(Page page) {
        return STATES.getOrDefault(page, State.NONE);
    }

    /**
     * Starts the check of every page once per session (later calls do nothing) and returns at once. The answer lands in
     * {@link #state} from the request thread.
     */
    public static void check(String version) {
        for (Page page : Page.values()) {
            if (STATES.putIfAbsent(page, State.CHECKING) == null) {
                Http client = http;
                CompletableFuture.runAsync(() -> STATES.put(page, ask(client, page, version)), EXECUTOR);
            }
        }
    }

    /** One request: the state it shows. Never throws. */
    static State ask(Http client, Page page, String version) {
        try {
            return fromStatus(client.get(page.api, userAgent(version), TIMEOUT));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return State.UNKNOWN;
        } catch (Exception e) {
            // No network, unknown host, timeout, TLS: could not check.
            return State.UNKNOWN;
        }
    }

    /** Tests: another client, and the session's answers forgotten. */
    public static void useForTests(Http client) {
        http = client == null ? JDK : client;
        STATES.clear();
    }

    /** Tests: waits until no request is under way (up to {@code millis}). */
    public static boolean awaitForTests(long millis) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (STATES.containsValue(State.CHECKING)) {
            if (System.nanoTime() > end) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }
}
