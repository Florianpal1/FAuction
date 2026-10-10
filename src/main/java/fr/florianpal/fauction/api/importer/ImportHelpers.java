package fr.florianpal.fauction.api.importer;

import com.google.gson.JsonElement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Tools that keep the modules short. Provided by FAuction through {@link ImportContext#helpers()} ;
 * not meant to be implemented by modules (methods may be added). In tests, use
 * {@link fr.florianpal.fauction.api.importer.testing.TestImportContext}.
 */
public interface ImportHelpers {

    /**
     * Opens a connection to the database of the source plugin, read only when the driver supports it.
     * Works for a SQLite file of the source too : only the storage of FAuction is restricted to a
     * shared SQL database. The caller closes it.
     */
    Connection openJdbc(String url, String user, String password) throws SQLException;

    /**
     * {@code plugins/<pluginName>/}, whether the source plugin is loaded or not (the expected case
     * being that it is disabled).
     */
    File sourceDataFolder(String pluginName);

    /**
     * Loads a YAML file, the {@code config.yml} of the source for instance. An absent file gives an
     * empty configuration.
     *
     * @throws IOException if the file exists but cannot be read or parsed.
     */
    YamlConfiguration loadYaml(File file) throws IOException;

    /**
     * Loads a JSON file with the Gson shipped by the server. An absent or empty file gives
     * {@code JsonNull}, never an exception.
     *
     * @throws IOException if the file cannot be read or is not valid JSON (a truncated file, say).
     */
    JsonElement loadJson(File file) throws IOException;

    /**
     * Same as {@link #loadJson(File)}, mapped onto {@code type}. An absent or empty file gives
     * {@code null}.
     */
    <T> T loadJson(File file, Type type) throws IOException;

    /**
     * The name of a player from their UUID (server cache of offline players), or the UUID itself when
     * the server never saw them.
     */
    String resolvePlayerName(UUID uuid);

    /**
     * When {@code file} was last written, empty when unknown. The source plugin writes its files when
     * it stops : evaluating the expirations at that instant rather than "now" gives the same answer
     * at every run of the import, so a sale can never be imported once as on sale and once as
     * expired.
     */
    Optional<Instant> lastModified(File file);

    /**
     * Decodes an item, for a module that has to look into it (split a stack partially sold...).
     *
     * @throws Exception if the item cannot be decoded on this server.
     */
    ItemStack decodeItem(ImportedItem item) throws Exception;

    Instant fromEpochMillis(long epochMillis);

    Instant fromEpochSeconds(long epochSeconds);

    /**
     * Runs {@code task} on the main thread (the global region under Folia) and waits for its result,
     * for the modules that need the Bukkit API. Never call it from the main thread.
     */
    <T> T callSync(Callable<T> task) throws Exception;
}
