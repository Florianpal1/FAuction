package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportHelpers;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ImportContextImpl implements ImportContext {

    private final Logger logger;

    private final ImportOptions options;

    private final Set<ImportDataType> types;

    private final Clock clock;

    private final File pluginsFolder;

    private final Function<String, Plugin> pluginLookup;

    private final ImportHelpers helpers;

    public ImportContextImpl(String importerId, Logger parent, ImportOptions options, Set<ImportDataType> types, Clock clock,
                             File pluginsFolder, Function<String, Plugin> pluginLookup, ImportHelpers helpers) {
        this.logger = new PrefixedLogger(parent, "[Import:" + importerId + "] ");
        this.options = options;
        this.types = Set.copyOf(types);
        this.clock = clock;
        this.pluginsFolder = pluginsFolder;
        this.pluginLookup = pluginLookup;
        this.helpers = helpers;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Map<String, String> options() {
        return options.moduleOptions();
    }

    @Override
    public boolean dryRun() {
        return options.dryRun();
    }

    @Override
    public Set<ImportDataType> requestedTypes() {
        return types;
    }

    @Override
    public boolean convertBids() {
        return options.convertBids();
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
        return Optional.ofNullable(pluginLookup.apply(name));
    }

    @Override
    public ImportHelpers helpers() {
        return helpers;
    }

    /**
     * Same principle as the {@code PluginLogger} of Bukkit : a child logger that prefixes its
     * messages and hands them to the logger of the plugin.
     */
    static final class PrefixedLogger extends Logger {

        private final String prefix;

        PrefixedLogger(Logger parent, String prefix) {
            super(parent.getName() + ".import", null);
            this.prefix = prefix;
            setParent(parent);
            setUseParentHandlers(true);
        }

        @Override
        public void log(LogRecord logRecord) {
            logRecord.setMessage(prefix + logRecord.getMessage());
            super.log(logRecord);
        }
    }
}
