package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

@Getter
public class VaultIntegrationManager {

    private Permission perms;

    private Economy economy;

    public VaultIntegrationManager(FAuction plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            if (!setupPermissions()) {
                plugin.getLogger().warning("Failed to initialize Vault Permissions");
            } else {
                plugin.getLogger().info("Registered Vault Permissions");
            }

            if (!setupEconomy()) {
                plugin.getLogger().warning("Failed to initialize Vault Economy");
            } else {
                plugin.getLogger().info("Registered Vault Economy");
            }
        } else {
            plugin.getLogger().warning("Vault is not on the server, some features will not work");
        }
    }

    private boolean setupEconomy()
    {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        return (economy != null);
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            perms = rsp.getProvider();
        }
        return perms != null;
    }
}
