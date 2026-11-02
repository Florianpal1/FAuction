package fr.florianpal.fauction.managers.importer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import fr.florianpal.fauction.api.importer.ImportHelpers;
import fr.florianpal.fauction.api.importer.ImportedItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class ImportHelpersImpl implements ImportHelpers {

    private static final Gson GSON = new Gson();

    private final File pluginsFolder;

    private final Function<UUID, String> nameLookup;

    static final long SYNC_TIMEOUT_SECONDS = 30;

    private final Executor syncExecutor;

    private final ItemCodec codec;

    private final BooleanSupplier isMainThread;

    /**
     * @param syncExecutor runs a task on the main thread (the global region under Folia).
     * @param isMainThread whether the calling thread is the one {@code syncExecutor} runs on.
     */
    public ImportHelpersImpl(File pluginsFolder, Function<UUID, String> nameLookup, Executor syncExecutor,
                             ItemCodec codec, BooleanSupplier isMainThread) {
        this.pluginsFolder = pluginsFolder;
        this.nameLookup = nameLookup;
        this.syncExecutor = syncExecutor;
        this.codec = codec;
        this.isMainThread = isMainThread;
    }

    @Override
    public Connection openJdbc(String url, String user, String password) throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // A hint only : some drivers refuse it, the modules never write anyway.
        }
        return connection;
    }

    @Override
    public File sourceDataFolder(String pluginName) {
        return new File(pluginsFolder, pluginName);
    }

    @Override
    public YamlConfiguration loadYaml(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.isFile()) {
            return yaml;
        }
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Invalid YAML in " + file.getName() + " : " + e.getMessage(), e);
        }
        return yaml;
    }

    @Override
    public JsonElement loadJson(File file) throws IOException {
        if (!file.isFile()) {
            return JsonNull.INSTANCE;
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return JsonNull.INSTANCE;
        }
        try {
            return JsonParser.parseString(content);
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON in " + file.getName() + " (truncated file ?) : " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T loadJson(File file, Type type) throws IOException {
        JsonElement element = loadJson(file);
        if (element.isJsonNull()) {
            return null;
        }
        try {
            return GSON.fromJson(element, type);
        } catch (JsonParseException e) {
            throw new IOException("Unexpected JSON in " + file.getName() + " : " + e.getMessage(), e);
        }
    }

    @Override
    public String resolvePlayerName(UUID uuid) {
        String name = null;
        try {
            name = nameLookup.apply(uuid);
        } catch (RuntimeException ignored) {
            // Falls back on the UUID.
        }
        return name == null || name.isBlank() ? uuid.toString() : name;
    }

    @Override
    public Instant fromEpochMillis(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
    }

    @Override
    public Instant fromEpochSeconds(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds);
    }

    @Override
    public Optional<Instant> lastModified(File file) {
        long modified = file.lastModified();
        return modified > 0 ? Optional.of(Instant.ofEpochMilli(modified)) : Optional.empty();
    }

    @Override
    public ItemStack decodeItem(ImportedItem item) throws Exception {
        return codec.decode(item);
    }

    /**
     * Waits {@link #SYNC_TIMEOUT_SECONDS} at most : when the server stops, the main thread waits for
     * the import to finish and will never run the task, so waiting forever would leak the import
     * thread.
     */
    @Override
    public <T> T callSync(Callable<T> task) throws Exception {
        if (isMainThread.getAsBoolean()) {
            throw new IllegalStateException("callSync called from the main thread, it would wait for itself");
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        syncExecutor.execute(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            result.cancel(false);
            throw new IllegalStateException("The main thread did not run the task within " + SYNC_TIMEOUT_SECONDS + " s (server stopping ?)", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
