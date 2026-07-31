package fr.florianpal.fauction;

import fr.florianpal.fauction.configurations.DatabaseConfig;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SQLType;
import fr.florianpal.fauction.managers.ConfigurationManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.AuctionQueries;
import fr.florianpal.fauction.queries.ExpireQueries;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Calendar;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simulated server and plugin, so the managers can be exercised without a real Minecraft server nor
 * a database.
 */
public abstract class FAuctionTestBase {

    protected static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    protected static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    protected ServerMock server;

    protected FAuction plugin;

    protected AuctionQueries auctionQueries;

    protected ExpireQueries expireQueries;

    protected DatabaseConfig databaseConfig;

    protected GlobalConfig globalConfig;

    @BeforeEach
    void setUpServer() {
        server = MockBukkit.mock();

        plugin = mock(FAuction.class);
        auctionQueries = mock(AuctionQueries.class);
        expireQueries = mock(ExpireQueries.class);
        databaseConfig = mock(DatabaseConfig.class);
        globalConfig = mock(GlobalConfig.class);

        ConfigurationManager configurationManager = mock(ConfigurationManager.class);
        when(configurationManager.getDatabase()).thenReturn(databaseConfig);
        when(configurationManager.getGlobalConfig()).thenReturn(globalConfig);

        when(plugin.getConfigurationManager()).thenReturn(configurationManager);
        when(plugin.getAuctionQueries()).thenReturn(auctionQueries);
        when(plugin.getExpireQueries()).thenReturn(expireQueries);

        // The cache mode is the one keeping the auctions in memory, where the reservation is
        // arbitrated by the plugin itself.
        when(databaseConfig.getSqlType()).thenReturn(SQLType.SQLite);
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    protected Auction auction(int id, UUID owner, double price, ItemStack item) {
        return new Auction(id, owner, "Seller", price, SerializationUtil.serialize(item), Calendar.getInstance().getTime().getTime());
    }

    protected Auction auction(int id, UUID owner, double price) {
        return auction(id, owner, price, namedItem(Material.DIAMOND, 1, "Auction " + id));
    }

    protected ItemStack namedItem(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
