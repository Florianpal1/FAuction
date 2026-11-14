package fr.florianpal.fauction.docs.examples;

import fr.florianpal.fauction.api.importer.ImporterRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Example of the wiki page Importer-API : what the {@code onEnable} of the plugin providing a module
 * does. The plugin declares {@code depend: [FAuction]} so that FAuction is enabled first.
 */
public final class RegistrationExample {

    private RegistrationExample() {
    }

    public static void onEnable(Plugin plugin) {
        ImporterRegistry registry = Bukkit.getServicesManager().load(ImporterRegistry.class);
        if (registry == null) {
            plugin.getLogger().warning("FAuction is not enabled, the import module is not registered");
            return;
        }
        registry.register(plugin, new MyYamlImporter());
        // Nothing to do in onDisable : FAuction drops the modules of a plugin when it is disabled.
    }
}
