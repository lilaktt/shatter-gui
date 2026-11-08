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

public final class LinkStatus {
    public enum State {
        NONE,
        CHECKING,
        AVAILABLE,
        NOT_PUBLISHED,
        UNKNOWN
    }

    public enum Page {
        MODRINTH("https://api.modrinth.com/v2/project/shattergui"),
        GITHUB("https://api.github.com/repos/lilaktt/shatter-gui");

        public final String api;

        Page(String api) {
            this.api = api;
        }
    }

    public interface Http {
        int get(String url, String userAgent, Duration timeout) throws IOException, InterruptedException;
    }

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    public static final Http JDK = new Http() {
        private HttpClient client;

        @Override
        public int get(String url, String userAgent, Duration timeout) throws IOException, InterruptedException {
            if (client == null) {
                client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("User-Agent", userAgent)
                    .header("Accept", "application/json").GET().build();
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

    public static State fromStatus(int code) {
        return switch (code) {
            case 200 -> State.AVAILABLE;
            case 404 -> State.NOT_PUBLISHED;
            default -> State.UNKNOWN;
        };
    }

    public static String userAgent(String version) {
        return "lilaktt/shatter-gui/" + version;
    }

    public static State state(Page page) {
        return STATES.getOrDefault(page, State.NONE);
    }

    public static void check(String version) {
        for (Page page : Page.values()) {
            if (STATES.putIfAbsent(page, State.CHECKING) == null) {
                Http client = http;
                CompletableFuture.runAsync(() -> STATES.put(page, ask(client, page, version)), EXECUTOR);
            }
        }
    }

    static State ask(Http client, Page page, String version) {
        try {
            return fromStatus(client.get(page.api, userAgent(version), TIMEOUT));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return State.UNKNOWN;
        } catch (Exception e) {
            return State.UNKNOWN;
        }
    }

    public static void useForTests(Http client) {
        http = client == null ? JDK : client;
        STATES.clear();
    }

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
