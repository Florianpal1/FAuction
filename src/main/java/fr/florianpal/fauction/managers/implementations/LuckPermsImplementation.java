package fr.florianpal.fauction.managers.implementations;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class LuckPermsImplementation {

    private LuckPerms luckPerms;

    public LuckPermsImplementation() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        }
    }

    public int getMetaData(Player player) {
        CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
        return metaData.getMetaValue("maxAuctions", Integer::parseInt).orElse(0);
    }
}
