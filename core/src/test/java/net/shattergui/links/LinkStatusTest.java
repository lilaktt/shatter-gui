package net.shattergui.links;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The page checks of "About" with a fake HTTP client: what each answer shows, offline, one request per session. */
class LinkStatusTest {
    /** Answers from a table; records what it was asked. */
    private static final class FakeHttp implements LinkStatus.Http {
        final Map<String, Object> answers;
        final List<String> userAgents = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        FakeHttp(Map<String, Object> answers) {
            this.answers = answers;
        }

        @Override
        public int get(String url, String userAgent, Duration timeout) throws IOException, InterruptedException {
            calls.incrementAndGet();
            synchronized (userAgents) {
                userAgents.add(userAgent);
            }
            assertEquals(LinkStatus.TIMEOUT, timeout);
            Object answer = answers.get(url);
            if (answer instanceof IOException e) {
                throw e;
            }
            if (answer instanceof Integer code) {
                return code;
            }
            throw new IOException("no answer for " + url);
        }
    }

    @AfterEach
    void reset() {
        LinkStatus.useForTests(null);
    }

    @Test
    void onlyOkAndNotFoundAreClearAnswers() {
        assertEquals(LinkStatus.State.AVAILABLE, LinkStatus.fromStatus(200));
        assertEquals(LinkStatus.State.NOT_PUBLISHED, LinkStatus.fromStatus(404));
        for (int code : new int[]{201, 204, 301, 302, 400, 401, 403, 410, 429, 500, 502, 503, 504}) {
            assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.fromStatus(code), "HTTP " + code + " is grey, never yellow");
        }
    }

    @Test
    void answersOfTheFakeClient() {
        FakeHttp http = new FakeHttp(Map.of(
                LinkStatus.Page.MODRINTH.api, 404,
                LinkStatus.Page.GITHUB.api, 200));
        assertEquals(LinkStatus.State.NOT_PUBLISHED, LinkStatus.ask(http, LinkStatus.Page.MODRINTH, "0.1.0-beta"));
        assertEquals(LinkStatus.State.AVAILABLE, LinkStatus.ask(http, LinkStatus.Page.GITHUB, "0.1.0-beta"));
        // No e-mail or anything about the player: the project and its version.
        for (String agent : http.userAgents) {
            assertEquals("lilaktt/shatter-gui/0.1.0-beta", agent);
            assertFalse(agent.contains("@"));
        }
    }

    @Test
    void timeoutRateLimitAndServerErrorsAreGrey() {
        FakeHttp http = new FakeHttp(Map.of(
                LinkStatus.Page.MODRINTH.api, new HttpTimeoutException("request timed out"),
                LinkStatus.Page.GITHUB.api, 429));
        assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.ask(http, LinkStatus.Page.MODRINTH, "1"));
        assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.ask(http, LinkStatus.Page.GITHUB, "1"));
        FakeHttp errors = new FakeHttp(Map.of(LinkStatus.Page.MODRINTH.api, 503, LinkStatus.Page.GITHUB.api, 403));
        assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.ask(errors, LinkStatus.Page.MODRINTH, "1"));
        assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.ask(errors, LinkStatus.Page.GITHUB, "1"));
    }

    @Test
    void offlineIsGreyAndDoesNotThrow() throws InterruptedException {
        FakeHttp offline = new FakeHttp(Map.of(
                LinkStatus.Page.MODRINTH.api, new UnknownHostException("api.modrinth.com"),
                LinkStatus.Page.GITHUB.api, new java.net.ConnectException("Network is unreachable")));
        LinkStatus.useForTests(offline);
        long start = System.nanoTime();
        LinkStatus.check("1");
        // check() returns at once: the requests run on their own thread.
        assertTrue(System.nanoTime() - start < 200_000_000L, "check must not wait for the network");
        assertTrue(LinkStatus.awaitForTests(2000));
        assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.state(LinkStatus.Page.MODRINTH));
        assertEquals(LinkStatus.State.UNKNOWN, LinkStatus.state(LinkStatus.Page.GITHUB));
    }

    @Test
    void checkedOncePerSession() throws InterruptedException {
        FakeHttp http = new FakeHttp(Map.of(LinkStatus.Page.MODRINTH.api, 404, LinkStatus.Page.GITHUB.api, 200));
        LinkStatus.useForTests(http);
        assertEquals(LinkStatus.State.NONE, LinkStatus.state(LinkStatus.Page.MODRINTH), "nothing before About is opened");
        LinkStatus.check("1");
        LinkStatus.check("1");
        assertTrue(LinkStatus.awaitForTests(2000));
        LinkStatus.check("1");
        assertTrue(LinkStatus.awaitForTests(2000));
        assertEquals(2, http.calls.get(), "one request per page and session");
        assertEquals(LinkStatus.State.NOT_PUBLISHED, LinkStatus.state(LinkStatus.Page.MODRINTH));
        assertEquals(LinkStatus.State.AVAILABLE, LinkStatus.state(LinkStatus.Page.GITHUB));
    }

    @Test
    void aSlowAnswerShowsCheckingMeanwhile() throws InterruptedException {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        LinkStatus.useForTests((url, agent, timeout) -> {
            release.await();
            return 200;
        });
        LinkStatus.check("1");
        assertEquals(LinkStatus.State.CHECKING, LinkStatus.state(LinkStatus.Page.MODRINTH));
        release.countDown();
        assertTrue(LinkStatus.awaitForTests(2000));
        assertEquals(LinkStatus.State.AVAILABLE, LinkStatus.state(LinkStatus.Page.MODRINTH));
    }
}
