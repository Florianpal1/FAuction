package fr.florianpal.fauction.utils;

import io.papermc.lib.PaperLib;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;

public class SerializationUtil {
    public static byte[] serialize(ItemStack itemStack) throws IllegalStateException {
        if (PaperLib.isPaper()) {
            return itemStack.serializeAsBytes();
        } else {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

                // Write the size of the inventory
                dataOutput.writeObject(itemStack);

                // Serialize that array
                dataOutput.close();
                return outputStream.toByteArray();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to save item stacks.", e);
            }
        }
    }

    public static ItemStack deserialize(byte[] data) throws IOException {
        if (PaperLib.isPaper()) {
            return ItemStack.deserializeBytes(data);
        } else {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

                // Read the serialized inventory
                ItemStack itemStack = (ItemStack) dataInput.readObject();
                dataInput.close();
                return itemStack;
            } catch (ClassNotFoundException e) {
                throw new IOException("Unable to decode class type.", e);
            }
        }
    }

    public static byte[] serializeBukkit(ItemStack itemStack) throws IllegalStateException {

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeObject(itemStack);

            // Serialize that array
            dataOutput.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static byte[] serializePaper(ItemStack itemStack) throws IllegalStateException {

        return itemStack.serializeAsBytes();
    }

    public static ItemStack deserializePaper(byte[] data) {

        return ItemStack.deserializeBytes(data);
    }

    /**
     * What a {@code BukkitObjectOutputStream} of an item holds : the wrapper of Bukkit around the
     * maps of {@code ItemStack.serialize()}, Guava's immutable collections, boxed primitives and
     * strings, then the item the server rebuilds from them through {@code readResolve} (CraftItemStack
     * on a server, the ItemStack of MockBukkit in the tests). Everything else is refused, as are deep
     * or huge graphs.
     */
    private static final ObjectInputFilter UNTRUSTED_ITEM_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=64;maxrefs=100000;maxbytes=1048576;maxarray=100000;"
                    + "java.lang.*;java.util.*;java.math.*;org.bukkit.**;com.google.common.collect.*;"
                    + "io.papermc.**;com.destroystokyo.paper.**;net.kyori.**;net.md_5.bungee.**;org.mockbukkit.**;!*");

    /**
     * {@link #deserializeBukkit} for data read from a file of another plugin : only the classes an
     * item is made of can be instantiated.
     */
    public static ItemStack deserializeBukkitUntrusted(byte[] data) throws IOException {

        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            dataInput.setObjectInputFilter(UNTRUSTED_ITEM_FILTER);
            Object read = dataInput.readObject();
            if (!(read instanceof ItemStack itemStack)) {
                throw new IOException("Not an item : " + (read == null ? "null" : read.getClass().getName()));
            }
            return itemStack;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    public static ItemStack deserializeBukkit(byte[] data) throws IOException {

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Read the serialized inventory
            ItemStack itemStack = (ItemStack) dataInput.readObject();
            dataInput.close();
            return itemStack;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}
