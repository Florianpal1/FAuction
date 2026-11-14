package fr.florianpal.fauction.importers.auctionhouse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.florianpal.fauction.api.importer.DataImporter;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportSink;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedItem;
import fr.florianpal.fauction.api.importer.ImportedPendingCurrency;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Import module of Auction-House by ElaineQheart (tested format : 1.5.6).
 * <p>
 * The whole plugin is a single file, {@code plugins/AuctionHouse/data/notes.json} : a JSON array of
 * "notes", one per sale, written by Gson. Each note is converted following its state :
 * <ul>
 *     <li>sale on the market : {@code AUCTION} ; the part already bought of a partially sold one is
 *     money owed to the seller ;</li>
 *     <li>expired, or expired by an administrator : {@code EXPIRED} for the seller ;</li>
 *     <li>deleted by an administrator (the item was replaced by the "deleted" dirt item) : skipped,
 *     the original item no longer exists ;</li>
 *     <li>sold and not collected by the seller : the money owed ({@code PENDING_CURRENCY}, net of the
 *     tax of the source unless {@code --opt apply-source-tax=false}) and the sale ({@code HISTORIC}) ;</li>
 *     <li>auctions with bids : converted without losing any item nor money, see {@link #readBid}.</li>
 * </ul>
 * Options : {@code --opt apply-source-tax=true|false}.
 */
public class AuctionHouseImporter implements DataImporter {

    public static final String ID = "auctionhouse-elaine";

    static final String PLUGIN_NAME = "AuctionHouse";

    /**
     * The only currency of the source : Vault.
     */
    static final String CURRENCY = "Vault";

    private static final Gson GSON = new Gson();

    /**
     * The default formats of Gson for a {@code Date}, US locale : with a comma after the year since
     * JDK 9, without before. The narrow no-break space JDK 20+ puts before AM/PM is normalized first.
     */
    private static final String[] DATE_PATTERNS = {"MMM d, yyyy, h:mm:ss a", "MMM d, yyyy h:mm:ss a", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"};

    private record SourceConfig(double tax, long setupTime, long binDuration, long bidDuration, String deletedItemName) {
    }

    private record Bid(UUID player, String playerName, double amount) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Auction-House by ElaineQheart (tested 1.5.6)";
    }

    @Override
    public Set<ImportDataType> supportedTypes() {
        return EnumSet.allOf(ImportDataType.class);
    }

    @Override
    public ImportAvailability checkAvailability(ImportContext context) {
        if (context.sourcePlugin(PLUGIN_NAME).map(plugin -> plugin.isEnabled()).orElse(false)) {
            return ImportAvailability.unavailable(PLUGIN_NAME + " is still enabled : it keeps its sales in memory and "
                    + "would still hand out the imported items and money. Stop the server, remove its jar, start again, then import.");
        }
        File notes = notesFile(context);
        if (notes == null) {
            return ImportAvailability.unavailable("No notes.json in " + context.helpers().sourceDataFolder(PLUGIN_NAME).getPath()
                    + " (data/notes.json or notes.json)");
        }
        return ImportAvailability.available("Sales found in " + notes.getPath());
    }

    /**
     * {@code data/notes.json}, or the location of the versions before it.
     */
    static File notesFile(ImportContext context) {
        File folder = context.helpers().sourceDataFolder(PLUGIN_NAME);
        File current = new File(folder, "data/notes.json");
        if (current.isFile()) {
            return current;
        }
        File legacy = new File(folder, "notes.json");
        return legacy.isFile() ? legacy : null;
    }

    @Override
    public void read(ImportContext context, ImportSink sink) throws Exception {
        File notesFile = notesFile(context);
        if (notesFile == null) {
            throw new IllegalStateException("notes.json not found");
        }

        SourceConfig config = sourceConfig(context);
        Instant asOf = asOf(context, notesFile);
        context.logger().info("Sales evaluated as of " + asOf + ", when Auction-House last saved notes.json");
        boolean applyTax = !"false".equalsIgnoreCase(context.options().getOrDefault("apply-source-tax", "true"));

        JsonElement root = context.helpers().loadJson(notesFile);
        if (root.isJsonNull()) {
            context.logger().info("notes.json is empty, nothing to import");
            return;
        }
        if (!root.isJsonArray()) {
            throw new IllegalStateException("notes.json is not a JSON array : unknown format");
        }

        JsonArray notes = root.getAsJsonArray();
        for (int index = 0; index < notes.size(); index++) {
            if (sink.isCancelled()) {
                return;
            }
            JsonElement element = notes.get(index);
            String sourceId = "#" + index;
            try {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Not a note");
                }
                JsonObject note = element.getAsJsonObject();
                sourceId = requiredString(note, "noteID");
                readNote(sourceId, note, config, applyTax, asOf, context, sink);
            } catch (RuntimeException e) {
                sink.skip(sourceId, "Unreadable note : " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
    }

    private void readNote(String noteId, JsonObject note, SourceConfig config, boolean applyTax, Instant asOf, ImportContext context,
                          ImportSink sink) {
        UUID seller = UUID.fromString(requiredString(note, "playerUUID"));
        String sellerName = optionalString(note, "playerName");
        String itemData = requiredString(note, "itemData");
        ImportedItem item = ImportedItem.ofBukkitBase64(itemData);
        double price = note.has("price") ? note.get("price").getAsDouble() : 0;
        Instant created = parseDate(note.get("dateCreated"));
        long auctionTime = note.has("auctionTime") ? note.get("auctionTime").getAsLong() : 0;
        boolean sold = optionalBoolean(note, "isSold");
        boolean isBid = optionalBoolean(note, "isBIDAuction");
        int partialLeft = note.has("partiallySoldAmountLeft") ? note.get("partiallySoldAmountLeft").getAsInt() : 0;
        String adminMessage = optionalString(note, "adminMessage");

        Instant now = asOf;
        Instant end = endOf(created, auctionTime, isBid, config);
        boolean expired = now.isAfter(end);

        if (adminMessage != null && auctionTime == -1 && isDeletedItem(item, config, context)) {
            sink.skip(noteId, "Deleted by an administrator (" + adminMessage + ") : the source replaced the item, the original no longer exists");
            return;
        }

        if (isBid) {
            if (!context.convertBids()) {
                sink.skip(noteId, "Auction with bids, skipped (--bids=skip) : " + price + " " + CURRENCY);
                return;
            }
            readBid(noteId, note, seller, sellerName, item, price, created, end, expired, sold, config, applyTax, sink);
            return;
        }

        if (!sold) {
            if (expired) {
                sink.accept(new ImportedExpired(noteId, seller, sellerName, item, price, created));
            } else {
                sink.accept(new ImportedAuction(noteId, seller, sellerName, item, price, created, CURRENCY));
            }
            return;
        }

        UUID buyer = optionalUuid(note, "buyerUUID");
        String buyerName = optionalString(note, "buyerName");

        if (partialLeft <= 0) {
            // Fully sold, the item already went to the buyer : only the money of the seller is left.
            sink.accept(new ImportedPendingCurrency(noteId, seller, CURRENCY, net(price, config, applyTax), "Sale not collected"));
            historic(noteId, seller, sellerName, buyer, buyerName, item, price, created, null, sink);
            return;
        }

        // Partially sold : price is the price of the whole stack still stored, the part bought is owed
        // to the seller, the rest is still for sale at the same unit price.
        ItemStack stack = decode(item, context);
        int amount = stack.getAmount();
        if (partialLeft >= amount) {
            throw new IllegalArgumentException("partiallySoldAmountLeft " + partialLeft + " for a stack of " + amount);
        }
        double unitPrice = price / amount;
        double leftPrice = unitPrice * partialLeft;
        double soldPrice = price - leftPrice;

        ItemStack leftStack = stack.clone();
        leftStack.setAmount(partialLeft);
        ItemStack soldStack = stack.clone();
        soldStack.setAmount(amount - partialLeft);

        if (expired) {
            sink.accept(new ImportedExpired(noteId, seller, sellerName, ImportedItem.of(leftStack), leftPrice, created));
        } else {
            sink.accept(new ImportedAuction(noteId, seller, sellerName, ImportedItem.of(leftStack), leftPrice, created, CURRENCY));
        }
        sink.accept(new ImportedPendingCurrency(noteId, seller, CURRENCY, net(soldPrice, config, applyTax), "Partial sale not collected"));
        historic(noteId, seller, sellerName, buyer, buyerName, ImportedItem.of(soldStack), soldPrice, created, null, sink);
    }

    /**
     * An auction with bids. The source keeps the money of every bid (escrow) until the end, then each
     * party claims : the winner the item, each loser a refund of their last bid, the seller the final
     * price. Nothing claimed may be lost, nothing already claimed may be handed out twice :
     * <ul>
     *     <li>running, no bid : the item goes back to the seller ;</li>
     *     <li>running, with bids : the item goes back to the seller and every bidder is refunded ;</li>
     *     <li>ended : the item to the winner if they did not claim it, the price to the seller if they
     *     did not collect it ({@code isSold} false), and a refund to each loser who did not claim
     *     one ({@code claimedPlayers}).</li>
     * </ul>
     */
    private void readBid(String noteId, JsonObject note, UUID seller, String sellerName, ImportedItem item, double price,
                         Instant created, Instant end, boolean ended, boolean sellerPaid, SourceConfig config, boolean applyTax,
                         ImportSink sink) {
        List<Bid> history = bids(note);
        Map<UUID, Bid> lastBids = new LinkedHashMap<>();
        for (Bid bid : history) {
            lastBids.remove(bid.player());
            lastBids.put(bid.player(), bid);
        }

        if (history.isEmpty()) {
            sink.accept(new ImportedExpired(noteId, seller, sellerName, item, price, created));
            return;
        }

        Set<UUID> claimed = new HashSet<>();
        if (note.has("claimedPlayers") && note.get("claimedPlayers").isJsonArray()) {
            for (JsonElement uuid : note.getAsJsonArray("claimedPlayers")) {
                claimed.add(UUID.fromString(uuid.getAsString()));
            }
        }

        if (!ended) {
            sink.accept(new ImportedExpired(noteId, seller, sellerName, item, price, created));
            for (Bid bid : lastBids.values()) {
                // The source only lets the bidders claim once the auction ended ; a bidder already in
                // claimedPlayers was settled anyway, and is never refunded a second time.
                if (claimed.contains(bid.player())) {
                    continue;
                }
                sink.accept(new ImportedPendingCurrency(noteId + ":" + bid.player(), bid.player(), CURRENCY, bid.amount(),
                        "Refund of a bid on an auction still running"));
            }
            return;
        }

        Bid winner = history.get(history.size() - 1);
        if (!claimed.contains(winner.player())) {
            sink.accept(new ImportedExpired(noteId, winner.player(), winner.playerName(), item, winner.amount(), end));
        }
        if (!sellerPaid) {
            sink.accept(new ImportedPendingCurrency(noteId, seller, CURRENCY, net(winner.amount(), config, applyTax),
                    "Auction won and not collected by the seller"));
        }
        for (Bid bid : lastBids.values()) {
            if (!bid.player().equals(winner.player()) && !claimed.contains(bid.player())) {
                sink.accept(new ImportedPendingCurrency(noteId + ":" + bid.player(), bid.player(), CURRENCY, bid.amount(),
                        "Refund of a lost bid"));
            }
        }
        sink.accept(new ImportedHistoric(noteId, seller, sellerName, winner.player(), winner.playerName(), item, winner.amount(), created, end));
    }

    private static void historic(String noteId, UUID seller, String sellerName, UUID buyer, String buyerName, ImportedItem item,
                                 double price, Instant listedAt, Instant soldAt, ImportSink sink) {
        if (buyer == null) {
            // Older notes only kept the name of the buyer : no history line rather than a wrong one.
            sink.skip(noteId, "History of the sale not imported : the note has no buyerUUID (buyer " + buyerName + ")");
            return;
        }
        sink.accept(new ImportedHistoric(noteId, seller, sellerName, buyer, buyerName, item, price, listedAt, soldAt));
    }

    private static List<Bid> bids(JsonObject note) {
        List<Bid> bids = new ArrayList<>();
        if (!note.has("bidHistory") || !note.get("bidHistory").isJsonArray()) {
            return bids;
        }
        for (JsonElement element : note.getAsJsonArray("bidHistory")) {
            JsonObject bid = element.getAsJsonObject();
            bids.add(new Bid(UUID.fromString(requiredString(bid, "player")), optionalString(bid, "playerName"), bid.get("bid").getAsDouble()));
        }
        return bids;
    }

    /**
     * When the sale ends : the duration (the default one for the oldest notes, which carry 0) plus
     * the setup time during which a new sale is listed but not buyable yet.
     */
    static Instant endOf(Instant created, long auctionTime, boolean isBid, SourceConfig config) {
        long duration = auctionTime == 0 ? (isBid ? config.bidDuration() : config.binDuration()) : auctionTime;
        return created.plusSeconds(duration + config.setupTime());
    }

    /**
     * The instant the sales are evaluated at : when the source last saved its file, that is when it
     * stopped. Never "now" : a sale on the market at the first run and expired at the second would
     * otherwise be seen once as each, and an auction with bids running then ended would be both
     * refunded and paid.
     */
    static Instant asOf(ImportContext context, File file) {
        Instant now = context.clock().instant();
        return context.helpers().lastModified(file).filter(saved -> saved.isBefore(now)).orElse(now);
    }

    /**
     * What the source pays the seller : {@code floor(amount * 100 * (1 - tax)) / 100}, in the order
     * the source computes it. Without the tax, the amount as is.
     */
    private static double net(double amount, SourceConfig config, boolean applyTax) {
        if (!applyTax) {
            return amount;
        }
        return Math.floor(amount * 100 * (1 - config.tax())) / 100;
    }

    private boolean isDeletedItem(ImportedItem item, SourceConfig config, ImportContext context) {
        ItemStack stack;
        try {
            stack = decode(item, context);
        } catch (RuntimeException e) {
            return false;
        }
        if (stack.getType() != Material.DIRT) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        if (config.deletedItemName() == null) {
            return true;
        }
        return strip(meta.getDisplayName()).equalsIgnoreCase(strip(config.deletedItemName()));
    }

    private static String strip(String text) {
        String translated = ChatColor.translateAlternateColorCodes('&', text);
        String stripped = ChatColor.stripColor(translated);
        return stripped == null ? "" : stripped.trim();
    }

    private static ItemStack decode(ImportedItem item, ImportContext context) {
        try {
            return context.helpers().decodeItem(item);
        } catch (Exception e) {
            throw new IllegalArgumentException("Item cannot be decoded : " + e.getMessage(), e);
        }
    }

    private SourceConfig sourceConfig(ImportContext context) throws Exception {
        File folder = context.helpers().sourceDataFolder(PLUGIN_NAME);
        YamlConfiguration config = context.helpers().loadYaml(new File(folder, "config.yml"));
        YamlConfiguration layout = context.helpers().loadYaml(new File(folder, "layout.yml"));
        return new SourceConfig(
                config.getDouble("tax", 0.01),
                config.getLong("auction-setup-time", 30),
                config.getLong("bin-auction-duration", 172800),
                config.getLong("bid-auction-duration", 7200),
                layout.getString("items.deleted.name"));
    }

    /**
     * A date written by the default Gson : read back by Gson first, then by the US formats of the
     * JDKs, whitespace normalized, which covers the files written before and after JDK 20.
     */
    static Instant parseDate(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("No dateCreated");
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return Instant.ofEpochMilli(element.getAsLong());
        }
        try {
            Date date = GSON.fromJson(element, Date.class);
            if (date != null) {
                return date.toInstant();
            }
        } catch (JsonParseException ignored) {
            // Not the format of this JDK : the known formats below.
        }

        String text = element.getAsString().replace('\u202F', ' ').replace('\u00A0', ' ').trim();
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            try {
                return format.parse(text).toInstant();
            } catch (ParseException ignored) {
                // Next pattern.
            }
        }
        throw new IllegalArgumentException("Unreadable date \"" + element.getAsString() + "\"");
    }

    private static String requiredString(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("No " + key);
        }
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static boolean optionalBoolean(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static UUID optionalUuid(JsonObject object, String key) {
        String value = optionalString(object, key);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
