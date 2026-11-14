package fr.florianpal.fauction.api.importer;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * An item read from the source plugin, in whatever form the source stores it. The conversion to an
 * {@link ItemStack} is done by FAuction, and an item that cannot be decoded rejects its row only.
 * <p>
 * The factories check the shape of the data right away (null, empty, invalid base64, missing GZIP
 * header) and throw an {@link IllegalArgumentException} naming the problem : a module catching it
 * can {@link ImportSink#skip skip} the row with that message.
 * <p>
 * Immutable : the arrays are copied in and out.
 */
public final class ImportedItem {

    /**
     * How the item is encoded.
     */
    public enum Format {

        /**
         * An {@link ItemStack} already built by the module.
         */
        ITEM_STACK,

        /**
         * {@link ItemStack#serializeAsBytes()} of Paper : NBT compressed with GZIP, carrying its
         * {@code DataVersion} so the item is upgraded on read.
         */
        PAPER_BYTES,

        /**
         * A {@code BukkitObjectOutputStream} holding a single {@link ItemStack}, the format of most
         * Spigot plugins.
         */
        BUKKIT_BYTES,

        /**
         * The vanilla item NBT, compressed with GZIP, with its {@code DataVersion} (format of AxAPI).
         * Decoded through the same path as {@link #PAPER_BYTES}.
         */
        VANILLA_NBT_GZIP
    }

    private final Format format;

    private final ItemStack itemStack;

    private final byte[] data;

    private ImportedItem(Format format, ItemStack itemStack, byte[] data) {
        this.format = format;
        this.itemStack = itemStack;
        this.data = data;
    }

    /**
     * An item already decoded by the module. Copied, so a later change of {@code itemStack} by the
     * module does not reach the import.
     */
    public static ImportedItem of(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return new ImportedItem(Format.ITEM_STACK, itemStack.clone(), null);
    }

    public static ImportedItem ofPaperBytes(byte[] bytes) {
        return new ImportedItem(Format.PAPER_BYTES, null, copyNonEmpty(bytes));
    }

    public static ImportedItem ofPaperBase64(String base64) {
        return new ImportedItem(Format.PAPER_BYTES, null, decodeBase64(base64));
    }

    public static ImportedItem ofBukkitBytes(byte[] bytes) {
        return new ImportedItem(Format.BUKKIT_BYTES, null, copyNonEmpty(bytes));
    }

    /**
     * A {@code BukkitObjectOutputStream} encoded in base64. Decoded with the MIME decoder, which
     * tolerates the line breaks older data was written with ({@code Base64Coder} and the like).
     */
    public static ImportedItem ofBukkitBase64(String base64) {
        return new ImportedItem(Format.BUKKIT_BYTES, null, decodeBase64(base64));
    }

    /**
     * @throws IllegalArgumentException if the data does not start with the GZIP header {@code 1F 8B}.
     */
    public static ImportedItem ofVanillaNbtGzip(byte[] bytes) {
        byte[] copy = copyNonEmpty(bytes);
        if (!isGzip(copy)) {
            throw new IllegalArgumentException("Item data is not GZIP compressed (no 1F 8B header)");
        }
        return new ImportedItem(Format.VANILLA_NBT_GZIP, null, copy);
    }

    /**
     * Whether {@code bytes} starts with the GZIP header {@code 1F 8B}.
     */
    public static boolean isGzip(byte[] bytes) {
        return bytes != null && bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    public Format format() {
        return format;
    }

    /**
     * A copy of the item, for {@link Format#ITEM_STACK} only.
     */
    public Optional<ItemStack> itemStack() {
        return Optional.ofNullable(itemStack).map(ItemStack::clone);
    }

    /**
     * A copy of the encoded data, empty for {@link Format#ITEM_STACK}.
     */
    public byte[] data() {
        return data == null ? new byte[0] : data.clone();
    }

    private static byte[] copyNonEmpty(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Item data is empty");
        }
        return bytes.clone();
    }

    private static byte[] decodeBase64(String base64) {
        Objects.requireNonNull(base64, "base64");
        if (base64.isBlank()) {
            throw new IllegalArgumentException("Item data is empty");
        }
        byte[] decoded;
        try {
            decoded = Base64.getMimeDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Item data is not valid base64 : " + e.getMessage(), e);
        }
        if (decoded.length == 0) {
            throw new IllegalArgumentException("Item data is not valid base64 : nothing decoded");
        }
        return decoded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImportedItem other)) {
            return false;
        }
        return format == other.format && Objects.equals(itemStack, other.itemStack) && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(format, itemStack) + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "ImportedItem[" + format + (itemStack != null ? ", " + itemStack.getType() : ", " + data.length + " bytes") + "]";
    }
}
