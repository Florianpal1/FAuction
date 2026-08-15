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
        return getLimitationByConfig(player, plugin.getConfigurationManager().getGlobalConfig().getLimitations());
    }

    public int getAuctionLimitationByMeta(Player player) {
        return plugin.getLuckPermsImplementation().getMetaData(player);
    }

    /**
     * Bids have their own cap, independent from the classic auctions one, so a player can be allowed
     * a different number of active bids than active sales.
     */
    public int getBidLimitationByConfig(Player player) {
        return getLimitationByConfig(player, plugin.getConfigurationManager().getGlobalConfig().getBidLimitations());
    }

    public int getBidLimitationByMeta(Player player) {
        return plugin.getLuckPermsImplementation().getBidMetaData(player);
    }

    /**
     * @return the highest cap found across the player's primary and secondary groups, or "default" if
     * none of his groups (or no Vault permission provider) is configured. Deny by default (0) rather
     * than NPE/unlimited if the map or its "default" key is missing.
     */
    private int getLimitationByConfig(Player player, Map<String, Integer> limitations) {
        Permission perms = plugin.getVaultIntegrationManager().getPerms();
        int limit = limitations.getOrDefault("default", 0);
        if (perms != null) {
            String primaryGroup = perms.getPrimaryGroup(player);
            if (limitations.containsKey(primaryGroup) && (limit < limitations.get(primaryGroup))) {
                limit = limitations.get(primaryGroup);
            }
            for (String s : perms.getPlayerGroups(player)) {
                if (limitations.containsKey(s) && (limit < limitations.get(s))) {
                    limit = limitations.get(s);
                }
            }
        }
        return limit;
    }
}
