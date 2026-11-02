package fr.florianpal.fauction.api.importer.testing;

import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportHelpers;
import fr.florianpal.fauction.managers.importer.ImportHelpersImpl;
import fr.florianpal.fauction.managers.importer.ItemCodec;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An {@link ImportContext} to test a module without FAuction, with {@link RecordingImportSink} :
 * <pre>{@code
 * TestImportContext context = new TestImportContext(tempPluginsFolder).option("mode", "db");
 * RecordingImportSink sink = new RecordingImportSink();
 * new MyImporter().read(context, sink);
 * }</pre>
 * The clock is fixed at {@link #NOW} by default, so the expiration computations of a module are
 * tested at a known date ; the helpers are the real ones of FAuction. {@code ImportContext} and
 * {@code ImportHelpers} are not meant to be implemented by modules : methods may be added to them,
 * this class follows.
 */
public class TestImportContext implements ImportContext {

    public static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private final File pluginsFolder;

    private final Map<String, String> options = new HashMap<>();

    private final Map<String, Plugin> plugins = new HashMap<>();

    private Set<ImportDataType> types = EnumSet.allOf(ImportDataType.class);

    private boolean convertBids = true;

    private boolean dryRun;

    private Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    /**
     * @param pluginsFolder the {@code plugins/} folder the module reads its source from.
     */
    public TestImportContext(File pluginsFolder) {
        this.pluginsFolder = pluginsFolder;
    }

    /**
     * A {@code --opt key=value} of the command.
     */
    public TestImportContext option(String key, String value) {
        options.put(key, value);
        return this;
    }

    public TestImportContext types(Set<ImportDataType> types) {
        this.types = types;
        return this;
    }

    public TestImportContext convertBids(boolean convertBids) {
        this.convertBids = convertBids;
        return this;
    }

    public TestImportContext dryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    /**
     * A plugin {@link #sourcePlugin} returns, to test the refusal while the source is enabled.
     */
    public TestImportContext plugin(String name, Plugin plugin) {
        plugins.put(name, plugin);
        return this;
    }

    public TestImportContext clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    @Override
    public Logger logger() {
        return Logger.getLogger("TestImportContext");
    }

    @Override
    public Map<String, String> options() {
        return options;
    }

    @Override
    public boolean dryRun() {
        return dryRun;
    }

    @Override
    public Set<ImportDataType> requestedTypes() {
        return types;
    }

    @Override
    public boolean convertBids() {
        return convertBids;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public File pluginsFolder() {
        return pluginsFolder;
    }

    @Override
    public Optional<Plugin> sourcePlugin(String name) {
        return Optional.ofNullable(plugins.get(name));
    }

    /**
     * The real helpers ; {@code callSync} runs the task on the calling thread.
     */
    @Override
    public ImportHelpers helpers() {
        return new ImportHelpersImpl(pluginsFolder, uuid -> null, Runnable::run, ItemCodec.SERVER, () -> false);
    }
}
