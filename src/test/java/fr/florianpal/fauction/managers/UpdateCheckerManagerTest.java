package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.utils.PluginVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateCheckerManagerTest extends FAuctionTestBase {

    private static final String RELEASE_URL = "https://github.com/Florianpal1/FAuction/releases/tag/";

    private HttpClient client;

    private final List<LogRecord> warnings = new ArrayList<>();

    @BeforeEach
    void setUpChecker() {
        client = mock(HttpClient.class);

        Logger logger = Logger.getLogger("UpdateCheckerManagerTest");
        logger.setUseParentHandlers(false);
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        when(plugin.getLogger()).thenReturn(logger);
        when(globalConfig.isUpdateCheckerEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("A newer release is announced with its version and its link")
    void newerReleaseIsAnnounced() throws Exception {

        answer(200, release("V_2.3.1"));
        UpdateCheckerManager checker = checker("2.3.0");

        checker.check();

        assertEquals(1, warnings.size());
        String message = warnings.get(0).getMessage();
        assertTrue(message.contains("available: 2.3.1"), message);
        assertTrue(message.contains("running: 2.3.0"), message);
        assertTrue(message.contains(RELEASE_URL + "V_2.3.1"), message);
        assertEquals(new PluginVersion(2, 3, 1), checker.getLatest().orElseThrow().version());
    }

    @Test
    @DisplayName("An up to date server displays nothing")
    void upToDateIsSilent() throws Exception {

        answer(200, release("V_2.3.0"));
        UpdateCheckerManager checker = checker("2.3.0");

        checker.check();

        assertTrue(warnings.isEmpty());
        assertTrue(checker.getLatest().isEmpty());
    }

    @Test
    @DisplayName("A server running a version newer than the latest release displays nothing")
    void newerLocalIsSilent() throws Exception {

        answer(200, release("V_2.2.2"));
        checker("2.3.0").check();

        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("Versions are compared numerically")
    void numericComparison() throws Exception {

        answer(200, release("V_1.9.10"));
        checker("1.9.9").check();

        assertEquals(1, warnings.size());
    }

    @Test
    @DisplayName("A tag that is not a release version is ignored")
    void nonReleaseTagIsIgnored() throws Exception {

        answer(200, release("V_2.3.0-beta"));
        checker("2.2.2").check();

        assertTrue(warnings.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "{}", "{\"tag_name\": \"V_2.3.0\"}", "{\"tag_name\": null, \"html_url\": \"x\"}", ""})
    @DisplayName("An unreadable answer displays nothing and does not throw")
    void unreadableAnswerIsSilent(String body) throws Exception {

        answer(200, body);
        checker("2.2.2").check();

        assertTrue(warnings.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 429, 500})
    @DisplayName("An error status displays nothing and does not throw")
    void errorStatusIsSilent(int status) throws Exception {

        answer(status, release("V_2.3.0"));
        checker("2.2.2").check();

        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("An unreachable GitHub displays nothing and does not throw")
    void networkFailureIsSilent() throws Exception {

        when(client.send(any(HttpRequest.class), any())).thenThrow(new IOException("unreachable"));
        checker("2.2.2").check();

        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("An interrupted check restores the interrupt flag")
    void interruptionIsRestored() throws Exception {

        when(client.send(any(HttpRequest.class), any())).thenThrow(new InterruptedException());
        checker("2.2.2").check();

        assertTrue(Thread.interrupted());
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("The newer release is announced again on every check, a reload included")
    void newerReleaseIsAnnouncedOnEveryCheck() throws Exception {

        answer(200, release("V_2.3.1"));
        UpdateCheckerManager checker = checker("2.3.0");

        checker.check();
        checker.check();

        assertEquals(2, warnings.size());
    }

    @Test
    @DisplayName("A development build never queries GitHub")
    void developmentBuildSendsNothing() {

        checker("2.3.0-SNAPSHOT").checkAsync(0);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("A disabled check never queries GitHub")
    void disabledSendsNothing() {

        when(globalConfig.isUpdateCheckerEnabled()).thenReturn(false);
        checker("2.2.2").checkAsync(0);

        verifyNoInteractions(client);
    }

    private UpdateCheckerManager checker(String installedVersion) {
        return new UpdateCheckerManager(plugin, client, installedVersion);
    }

    @SuppressWarnings("unchecked")
    private void answer(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any())).thenAnswer(invocation -> response);
    }

    private static String release(String tag) {
        return "{\"tag_name\": \"" + tag + "\", \"html_url\": \"" + RELEASE_URL + tag + "\", \"prerelease\": false}";
    }
}
