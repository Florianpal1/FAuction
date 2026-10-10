package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportedItem;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.inventory.ItemStack;

/**
 * Turns an {@link ImportedItem} into an {@link ItemStack}, then into the bytes FAuction stores.
 * An interface so the tests can stand in for the Paper decoding, which MockBukkit does not provide.
 */
public interface ItemCodec {

    ItemStack decode(ImportedItem item) throws Exception;

    /**
     * The storage format of this server, the one every other row of FAuction is written in.
     */
    byte[] encode(ItemStack item);

    /**
     * Paper decodes both its own bytes and the vanilla NBT : GZIP compressed NBT carrying a
     * {@code DataVersion}, upgraded by the DataFixer of the server.
     */
    ItemCodec SERVER = new ItemCodec() {
        @Override
        public ItemStack decode(ImportedItem item) throws Exception {
            return switch (item.format()) {
                case ITEM_STACK -> item.itemStack().orElseThrow();
                case BUKKIT_BYTES -> SerializationUtil.deserializeBukkitUntrusted(item.data());
                case PAPER_BYTES, VANILLA_NBT_GZIP -> ItemStack.deserializeBytes(item.data());
            };
        }

        @Override
        public byte[] encode(ItemStack item) {
            return SerializationUtil.serialize(item);
        }
    };
}
