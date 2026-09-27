package fr.florianpal.fauction.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.utils.PluginVersion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Warns the console when a newer release of the plugin is published on GitHub.
 * <p>
 * Checked at startup and on every reload only, never periodically, and never downloads anything.
 * Only official releases count : GitHub's {@code releases/latest} skips the drafts and the
 * prereleases, and a development build of the plugin does not check at all. A server without
 * internet access stays silent, every failure being logged at the {@code fine} level.
 */
public class UpdateCheckerManager {

    static final String API_URL = "https://api.github.com/repos/Florianpal1/FAuction/releases/latest";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final String SEPARATOR = "----------------------------------------------------";

    private final FAuction plugin;

    private final HttpClient client;

    private final String installedVersion;

    /**
     * The newer release found by the last successful check, or null.
     */
    private volatile ReleaseInfo latest;

    public record ReleaseInfo(PluginVersion version, String tag, String url) {
    }

    public UpdateCheckerManager(FAuction plugin) {
        this(plugin,
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                plugin.getPluginMeta().getVersion());
    }

    UpdateCheckerManager(FAuction plugin, HttpClient client, String installedVersion) {
        this.plugin = plugin;
        this.client = client;
        this.installedVersion = installedVersion;
    }

    /**
     * Schedules a check, unless it is disabled or the plugin is not a release build.
     *
     * @param delayTicks delay before the check, 0 to run it right away
     */
    public void checkAsync(long delayTicks) {

        if (!plugin.getConfigurationManager().getGlobalConfig().isUpdateCheckerEnabled()) {
            return;
        }

        if (PluginVersion.parseRelease(installedVersion).isEmpty()) {
            plugin.getLogger().fine("Update check skipped, " + installedVersion + " is not a release build");
            return;
        }

        if (delayTicks > 0) {
            FAuction.getFoliaLib().getScheduler().runLaterAsync(this::check, delayTicks);
        } else {
            FAuction.getFoliaLib().getScheduler().runAsync(task -> check());
        }
    }

    /**
     * Queries GitHub and warns the console if a newer release exists. Blocking : asynchronous thread
     * only.
     */
    void check() {

        Logger logger = plugin.getLogger();
        try {
            Optional<PluginVersion> local = PluginVersion.parseRelease(installedVersion);
            if (local.isEmpty()) {
                return;
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    // GitHub answers 403 to a request without User-Agent.
                    .header("User-Agent", "FAuction/" + installedVersion)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Optional<ReleaseInfo> newer = evaluate(response.statusCode(), response.body(), local.get());
            if (newer.isPresent()) {
                latest = newer.get();
                announce(logger, newer.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.fine("Update check interrupted");
        } catch (Exception e) {
            logger.fine("Update check failed : " + e);
        }
    }

    /**
     * The release to announce, present only when GitHub answered with a release newer than the
     * installed one. Never throws.
     */
    Optional<ReleaseInfo> evaluate(int status, String body, PluginVersion local) {

        Logger logger = plugin.getLogger();
        if (status == 404) {
            logger.fine("Update check : no release published");
            return Optional.empty();
        }
        if (status == 403 || status == 429) {
            // 60 requests per hour and per IP without authentication, shared by every plugin.
            logger.fine("Update check : GitHub rate limit reached (" + status + ")");
            return Optional.empty();
        }
        if (status != 200) {
            logger.fine("Update check : unexpected answer " + status);
            return Optional.empty();
        }

        String tag;
        String url;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            tag = string(json, "tag_name");
            url = string(json, "html_url");
        } catch (RuntimeException e) {
            logger.fine("Update check : unreadable answer : " + e);
            return Optional.empty();
        }
        if (tag == null || url == null) {
            logger.fine("Update check : answer without tag_name or html_url");
            return Optional.empty();
        }

        Optional<PluginVersion> remote = PluginVersion.parseRelease(tag);
        if (remote.isEmpty()) {
            logger.fine("Update check : ignored tag " + tag + ", not a release version");
            return Optional.empty();
        }

        if (remote.get().compareTo(local) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ReleaseInfo(remote.get(), tag, url));
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private void announce(Logger logger, ReleaseInfo release) {
        // The Bukkit logger is thread safe, no need to go back to the main thread.
        logger.warning(String.join(System.lineSeparator(),
                SEPARATOR,
                " A new version of FAuction is available: " + release.version(),
                " You are running: " + installedVersion,
                " Download: " + release.url(),
                SEPARATOR));
    }

    /**
     * The newer release found by the last successful check, if any.
     */
    public Optional<ReleaseInfo> getLatest() {
        return Optional.ofNullable(latest);
    }
}
