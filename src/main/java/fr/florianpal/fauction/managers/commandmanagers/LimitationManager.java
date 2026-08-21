package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;

import java.util.Map;

public class LimitationManager {

    private final FAuction plugin;

    public LimitationManager(FAuction plugin) {
        this.plugin = plugin;
    }

    public int getAuctionLimitationByConfig(Player player) {
        Permission perms = plugin.getVaultIntegrationManager().getPerms();
        Map<String, Integer> limitations = plugin.getConfigurationManager().getGlobalConfig().getLimitations();
        String[] playerGroup;
        // Deny by default rather than NPE/unlimited if "limitations" or its "default" key is missing.
        int limit = limitations.getOrDefault("default", 0);
        if (perms != null) {
            String primaryGroup = perms.getPrimaryGroup(player);
            if (limitations.containsKey(primaryGroup) && (limit < limitations.get(primaryGroup))) {
                limit = limitations.get(primaryGroup);
            }
            playerGroup = perms.getPlayerGroups(player);
            for (String s : playerGroup) {
                if (limitations.containsKey(s) && (limit < limitations.get(s))) {
                    limit = limitations.get(s);
                }
            }
        }
        return limit;
    }

    public int getAuctionLimitationByMeta(Player player) {
        return plugin.getLuckPermsImplementation().getMetaData(player);
    }
}
