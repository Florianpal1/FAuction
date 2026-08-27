package fr.florianpal.fauction.languages;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.utils.FormatUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The message system of the plugin : loads {@code lang_<code>.yml}, resolves a key and interpolates
 * it. Replaces the language part of ACF ({@code co.aikar.locales}), which is a command framework
 * concern nowhere else.
 * <p>
 * The file is loaded exactly like the ten other configurations ({@code YamlDocument.create} +
 * {@code BasicVersioning}), so it gains what they always had : new keys are added on update,
 * obsolete keys are removed, and the values edited by the administrator are preserved.
 */
public final class Lang {

    /**
     * Version of the bundled language resources. 1 is the implicit version of a file inherited from
     * the ACF era, which carries no {@code version} key ; BoostedYAML treats a versionless document
     * as the first version of the pattern, so it receives the whole relocation chain with no
     * detection code of ours.
     */
    public static final String VERSION = "2";

    static final String VERSION_ROUTE = "version";

    /**
     * The framework messages that survive ACF, moved out of the {@code acf-core} section : the name
     * of a framework that is gone has nothing to do in a file the administrator edits. Only the keys
     * actually re-wired on the exception handlers are relocated ; the others are removed from the
     * bundled resources and BoostedYAML drops them by itself.
     * <p>
     * {@code acf-core.permission_denied_parameter} is deliberately absent : ACF had two keys for the
     * same case, and relocating both onto the same target would make the result depend on the
     * iteration order.
     */
    static final Map<String, String> RELOCATIONS_V2;

    static {
        Map<String, String> relocations = new LinkedHashMap<>();
        relocations.put("acf-core.permission_denied", "fauction.error.permission_denied");
        relocations.put("acf-core.invalid_syntax", "fauction.error.invalid_syntax");
        relocations.put("acf-core.not_allowed_on_console", "fauction.error.not_allowed_on_console");
        relocations.put("acf-core.must_be_a_number", "fauction.error.must_be_a_number");
        relocations.put("acf-core.error_performing_command", "fauction.error.internal");
        RELOCATIONS_V2 = Map.copyOf(relocations);
    }

    /**
     * Swapped whole rather than cleared and refilled : {@code /ah admin reload} can reload the
     * messages while an asynchronous chain is sending one.
     */
    private volatile Map<String, String> messages = Map.of();

    /**
     * Loads the language file of the configured language code. Safe to call again : this is what
     * makes {@code /ah admin reload} reload the messages, which it never did under ACF.
     */
    public void load(FAuction plugin) {
        String code = plugin.getConfigurationManager().getGlobalConfig().getLang();
        File file = new File(plugin.getDataFolder(), "lang_" + code + ".yml");

        try (InputStream defaults = defaultsStream(plugin, code)) {

            boolean bundled = Lang.class.getResource("/lang_" + code + ".yml") != null;
            backupLegacyFile(plugin, file);

            YamlDocument document = YamlDocument.create(
                    file,
                    defaults,
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder()
                            .setVersioning(new BasicVersioning(VERSION_ROUTE))
                            .addRelocations(VERSION, RELOCATIONS_V2, '.')
                            // A language the plugin does not ship : nothing the administrator wrote
                            // may be dropped for being absent from the English defaults.
                            .setKeepAll(!bundled)
                            .setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING)
                            .build()
            );

            load(document);
            plugin.getLogger().info("Loaded " + messages.size() + " messages from lang_" + code + ".yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Error in language file load (lang_" + code + ".yml) : " + e.getMessage());
        }
    }

    /**
     * Flattens the document into full routes ({@code fauction.no_auction}). Package-private so the
     * tests can feed a document straight from a fixture.
     */
    void load(YamlDocument document) {
        Map<String, String> loaded = new HashMap<>();
        for (String route : document.getRoutesAsStrings(true)) {
            if (VERSION_ROUTE.equals(route)) {
                continue;
            }
            String value = document.getString(route);
            if (value != null) {
                loaded.put(route, value);
            }
        }
        messages = Map.copyOf(loaded);
    }

    /**
     * Resolves the defaults to merge against. A server may run a language the plugin does not ship —
     * {@code README} documents it — so a missing resource must not be fatal : the English defaults
     * fill the holes, and {@code setKeepAll} keeps every key the administrator wrote. Resolving the
     * defaults with {@code Objects.requireNonNull}, like the ten other configurations do, would
     * disable the plugin on a server that works today.
     */
    private InputStream defaultsStream(FAuction plugin, String code) {
        InputStream in = Lang.class.getResourceAsStream("/lang_" + code + ".yml");
        if (in != null) {
            return in;
        }

        plugin.getLogger().info("No bundled defaults for language '" + code
                + "', merging against the English defaults ; your own keys are preserved.");
        return Lang.class.getResourceAsStream("/lang_en.yml");
    }

    /**
     * Copies the file aside before the first versioned write. A file inherited from the ACF era has
     * never been rewritten by the plugin, and this update is the first one to touch it ; the copy
     * turns any incident into a file to restore.
     */
    private void backupLegacyFile(FAuction plugin, File file) {
        if (!file.exists()) {
            return;
        }

        try {
            YamlDocument probe = YamlDocument.create(file);
            if (probe.contains(VERSION_ROUTE)) {
                return;
            }

            File backup = new File(file.getParentFile(), file.getName() + ".bak");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Language file backed up to " + backup.getName() + " before its first update.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up " + file.getName() + " : " + e.getMessage());
        }
    }

    /**
     * The message of a key, colours applied and placeholders replaced, or empty when the key is
     * absent from the language file.
     * <p>
     * Nothing is sent for an absent key, which is what ACF did : a language file edited before a
     * key existed, or a language the plugin does not ship, must not print a raw key on screen.
     * {@code MessageKeys.DATABASEERROR} is the visible case — no entry in any of the four language
     * files, and no call site either.
     */
    public Optional<String> message(MessageKeys key, String... replacements) {
        return raw(key.getKey()).map(message -> render(message, replacements));
    }

    /**
     * The message of a raw route, colours applied and placeholders replaced, falling back on
     * {@code fallback} when the key is absent. For the framework texts (help, errors), where showing
     * nothing at all would leave a player without an answer.
     */
    public String messageOr(String route, String fallback, String... replacements) {
        return render(raw(route).orElse(fallback), replacements);
    }

    /**
     * Interpolation, ACF tags, then colours — the last step being the {@code FormatUtil} of the
     * plugin, so {@code &c} and {@code #RRGGBB} keep behaving as everywhere else.
     */
    private static String render(String message, String... replacements) {
        return FormatUtil.format(translateAcfTags(interpolate(message, replacements)));
    }

    /**
     * The stored value of a route, untouched.
     */
    public Optional<String> raw(String route) {
        return Optional.ofNullable(messages.get(route));
    }

    /**
     * Translates the {@code <c1>}/{@code <c2>}/{@code <c3>} tags of ACF into colour codes. Only
     * reached by a value an administrator had customised under {@code acf-core} and that the update
     * relocated : left alone, the tags would show up as such in the message. The mapping follows the
     * colours ACF was configured with (yellow, gold, red).
     */
    private static String translateAcfTags(String message) {
        if (message.indexOf("<c") < 0) {
            return message;
        }

        return message.replace("<c1>", "&e").replace("</c1>", "&r")
                .replace("<c2>", "&6").replace("</c2>", "&r")
                .replace("<c3>", "&c").replace("</c3>", "&r");
    }

    /**
     * Replacements come in pairs, {@code "{item}", value} — the call convention of the 39 call sites
     * inherited from ACF. A trailing element without its value is ignored.
     */
    public static String interpolate(String message, String... replacements) {
        String result = message;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }
}
