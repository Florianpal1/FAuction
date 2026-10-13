package fr.florianpal.fauction.testing;

import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Base64;

/**
 * Builds the items of the import tests and encodes them the way the source plugins do, at the time of
 * the test : no encoded string tied to a Minecraft version is stored in the fixtures.
 * Needs a running MockBukkit.
 */
public final class TestItems {

    private TestItems() {
    }

    public static ItemStack item(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    public static ItemStack named(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * {@code BukkitObjectOutputStream} + standard base64 : Auction-House and NexusAuctionHouse.
     */
    public static String bukkitBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(SerializationUtil.serializeBukkit(item));
    }

    /**
     * The same, wrapped at 76 characters like the older encoders ({@code Base64Coder}...).
     */
    public static String bukkitBase64Mime(ItemStack item) {
        return Base64.getMimeEncoder().encodeToString(SerializationUtil.serializeBukkit(item));
    }
}
