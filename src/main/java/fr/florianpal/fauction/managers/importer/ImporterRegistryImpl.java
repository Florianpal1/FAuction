package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.DataImporter;
import fr.florianpal.fauction.api.importer.ImporterApi;
import fr.florianpal.fauction.api.importer.ImporterRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The import modules, keyed on their id. Registered in the Bukkit {@code ServicesManager} under
 * {@link ImporterRegistry}, and listening to the disabling of the plugins to drop their modules.
 */
public class ImporterRegistryImpl implements ImporterRegistry, Listener {

    static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private record Registration(Plugin owner, DataImporter importer) {
    }

    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    private final Logger logger;

    private final int apiVersion;

    public ImporterRegistryImpl(Logger logger) {
        this(logger, ImporterApi.API_VERSION);
    }

    ImporterRegistryImpl(Logger logger, int apiVersion) {
        this.logger = logger;
        this.apiVersion = apiVersion;
    }

    @Override
    public void register(Plugin owner, DataImporter importer) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(importer, "importer");

        String id = importer.id();
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid importer id \"" + id + "\" : 1 to 32 characters among [a-z0-9_-]");
        }
        if (importer.requiredApiVersion() > apiVersion) {
            throw new IllegalArgumentException("Importer " + id + " needs version " + importer.requiredApiVersion()
                    + " of the import API, this FAuction provides version " + apiVersion + " : update FAuction");
        }

        Registration previous = registrations.putIfAbsent(id, new Registration(owner, importer));
        if (previous != null) {
            throw new IllegalArgumentException("Importer id \"" + id + "\" is already registered by " + previous.owner().getName());
        }
        logger.info("Import module registered : " + id + " (" + importer.displayName() + ") by " + owner.getName());
    }

    @Override
    public boolean unregister(DataImporter importer) {
        Registration registration = registrations.get(importer.id());
        if (registration == null || registration.importer() != importer) {
            return false;
        }
        return registrations.remove(importer.id(), registration);
    }

    /**
     * Drops every module of {@code owner}.
     *
     * @return the number of modules dropped.
     */
    public int unregisterAll(Plugin owner) {
        int removed = 0;
        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            if (entry.getValue().owner().equals(owner) && registrations.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public List<DataImporter> list() {
        List<DataImporter> importers = new ArrayList<>();
        registrations.values().forEach(registration -> importers.add(registration.importer()));
        importers.sort(Comparator.comparing(DataImporter::id));
        return importers;
    }

    @Override
    public Optional<DataImporter> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registrations.get(id)).map(Registration::importer);
    }

    public Optional<Plugin> ownerOf(String id) {
        return Optional.ofNullable(registrations.get(id)).map(Registration::owner);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        int removed = unregisterAll(event.getPlugin());
        if (removed > 0) {
            logger.info(removed + " import module(s) of " + event.getPlugin().getName() + " unregistered");
        }
    }
}
