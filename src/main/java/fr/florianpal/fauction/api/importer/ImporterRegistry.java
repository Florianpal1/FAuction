package fr.florianpal.fauction.api.importer;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * The import modules known to FAuction. Obtained from the Bukkit {@code ServicesManager} :
 * <pre>{@code
 * ImporterRegistry registry = Bukkit.getServicesManager().load(ImporterRegistry.class);
 * registry.register(this, new MyImporter());
 * }</pre>
 * The modules of a plugin are unregistered by themselves when that plugin is disabled.
 */
public interface ImporterRegistry {

    /**
     * @param owner the plugin providing the module.
     * @throws IllegalArgumentException if the id is invalid or already taken, or if the module needs a
     *                                  newer {@link ImporterApi#API_VERSION}.
     */
    void register(Plugin owner, DataImporter importer);

    /**
     * @return whether the module was registered.
     */
    boolean unregister(DataImporter importer);

    /**
     * The registered modules, sorted by id.
     */
    List<DataImporter> list();

    Optional<DataImporter> find(String id);
}
