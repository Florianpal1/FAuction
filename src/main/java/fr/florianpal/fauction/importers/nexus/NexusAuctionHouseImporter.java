package fr.florianpal.fauction.importers.nexus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.florianpal.fauction.api.importer.DataImporter;
import fr.florianpal.fauction.api.importer.ImportAvailability;
import fr.florianpal.fauction.api.importer.ImportContext;
import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportSink;
import fr.florianpal.fauction.api.importer.ImportedAuction;
import fr.florianpal.fauction.api.importer.ImportedExpired;
import fr.florianpal.fauction.api.importer.ImportedHistoric;
import fr.florianpal.fauction.api.importer.ImportedItem;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import module of NexusAuctionHouse (tested format : 2.4.7).
 * <p>
 * Two JSON files in {@code plugins/NexusAuctionHouse/data/} :
 * <ul>
 *     <li>{@code bins.json} : the sales, live ones and the history of the last {@code log-keep-time}
 *     mixed together ;</li>
 *     <li>{@code expired-retrieve.json} : the items each player has to collect (expired sales, but
 *     also purchases and withdrawals that did not fit in the inventory).</li>
 * </ul>
 * The source pays the seller at once, even offline : there is never money pending. A sale that
 * expired is not imported from {@code bins.json}, its item being already in
 * {@code expired-retrieve.json} — importing both would hand it out twice. {@code --opt
 * recover-orphans=true} gives back the expired sales whose item is not found in the seller's list
 * (the source loses it when a sale expires while the server is stopped).
 * <p>
 * The ids of {@code bins.json} are renumbered by the source when it loads : the {@code sourceId} is a
 * hash of the content of the sale instead. The {@code sourceId} of a collectable item is its owner,
 * the hash of the item and its rank among the identical items of that owner.
 */
public class NexusAuctionHouseImporter implements DataImporter {

    public static final String ID = "nexusauctionhouse";

    static final String PLUGIN_NAME = "NexusAuctionHouse";

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(mo|y|w|d|h|m|s)?", Pattern.CASE_INSENSITIVE);

    private record Bin(String sourceId, UUID seller, String item, long price, long expiry, String buyer) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "NexusAuctionHouse (tested 2.4.7)";
    }

    @Override
    public Set<ImportDataType> supportedTypes() {
        return EnumSet.of(ImportDataType.AUCTION, ImportDataType.EXPIRED, ImportDataType.HISTORIC);
    }

    @Override
    public ImportAvailability checkAvailability(ImportContext context) {
        if (context.sourcePlugin(PLUGIN_NAME).map(plugin -> plugin.isEnabled()).orElse(false)) {
            return ImportAvailability.unavailable(PLUGIN_NAME + " is still enabled : it saves its memory over its files and "
                    + "would still hand out the imported items. Stop the server, remove its jar, start again, then import.");
        }
        File data = dataFolder(context);
        if (!new File(data, "bins.json").isFile() && !new File(data, "expired-retrieve.json").isFile()) {
            return ImportAvailability.unavailable("Neither bins.json nor expired-retrieve.json in " + data.getPath()
                    + " (the old .yml files are converted by NexusAuctionHouse itself when it starts once)");
        }
        return ImportAvailability.available("Data found in " + data.getPath());
    }

    private static File dataFolder(ImportContext context) {
        return new File(context.helpers().sourceDataFolder(PLUGIN_NAME), "data");
    }

    @Override
    public void read(ImportContext context, ImportSink sink) throws Exception {
        File data = dataFolder(context);
        YamlConfiguration config = context.helpers().loadYaml(new File(context.helpers().sourceDataFolder(PLUGIN_NAME), "config.yml"));
        boolean expiryEnabled = config.getBoolean("expiry.enable", true);
        long expiryTime = parseDuration(config.getString("expiry.time", "7d"));
        boolean recoverOrphans = "true".equalsIgnoreCase(context.options().getOrDefault("recover-orphans", "false"));
        // The sales are evaluated when the source last saved bins.json, never "now" : a sale on the
        // market at the first run and expired at the second would otherwise be imported twice.
        Instant clockNow = context.clock().instant();
        Instant now = context.helpers().lastModified(new File(data, "bins.json"))
                .filter(saved -> saved.isBefore(clockNow)).orElse(clockNow);
        context.logger().info("Sales evaluated as of " + now + ", when NexusAuctionHouse last saved bins.json");

        // The items waiting in each player's list, as encoded by the source : needed to tell an
        // expired sale already given back from an orphan, whatever the requested types.
        Map<UUID, List<String>> stash = readStash(context, new File(data, "expired-retrieve.json"), sink);

        for (Map.Entry<UUID, List<String>> player : stash.entrySet()) {
            Map<String, Integer> occurrences = new HashMap<>();
            for (String item : player.getValue()) {
                if (sink.isCancelled()) {
                    return;
                }
                String hash = sha256(normalize(item));
                int occurrence = occurrences.merge(hash, 1, Integer::sum);
                String sourceId = "nexus:stash:" + player.getKey() + ":" + hash + ":" + occurrence;
                try {
                    sink.accept(new ImportedExpired(sourceId, player.getKey(), null, ImportedItem.ofBukkitBase64(item), 0, now));
                } catch (IllegalArgumentException e) {
                    sink.skip(sourceId, e.getMessage());
                }
            }
        }

        Map<UUID, Map<String, Integer>> stashLeft = new HashMap<>();
        stash.forEach((uuid, items) -> {
            Map<String, Integer> counts = new HashMap<>();
            items.forEach(item -> counts.merge(normalize(item), 1, Integer::sum));
            stashLeft.put(uuid, counts);
        });

        for (Bin bin : readBins(context, new File(data, "bins.json"), sink)) {
            if (sink.isCancelled()) {
                return;
            }
            try {
                readBin(bin, expiryEnabled, expiryTime, recoverOrphans, now, stashLeft, sink);
            } catch (IllegalArgumentException e) {
                sink.skip(bin.sourceId(), e.getMessage());
            }
        }
    }

    private void readBin(Bin bin, boolean expiryEnabled, long expiryTime, boolean recoverOrphans, Instant now,
                         Map<UUID, Map<String, Integer>> stashLeft, ImportSink sink) {
        Instant expiry = Instant.ofEpochSecond(bin.expiry());
        // With expiry.enable false the source stores the listing date in "expiry".
        Instant listedAt = expiryEnabled ? expiry.minusSeconds(expiryTime) : expiry;

        if (bin.buyer().isEmpty()) {
            if (!expiryEnabled || expiry.isAfter(now)) {
                sink.accept(new ImportedAuction(bin.sourceId(), bin.seller(), null, ImportedItem.ofBukkitBase64(bin.item()), bin.price(), listedAt));
                return;
            }

            // Expired : given back by the source into the list of the seller, imported from there.
            Map<String, Integer> sellerStash = stashLeft.getOrDefault(bin.seller(), new HashMap<>());
            String item = normalize(bin.item());
            if (sellerStash.getOrDefault(item, 0) > 0) {
                sellerStash.merge(item, -1, Integer::sum);
                sink.skip(bin.sourceId(), "Expired sale : its item is imported from expired-retrieve.json");
                return;
            }
            if (!recoverOrphans) {
                sink.skip(bin.sourceId(), "Expired sale whose item is not in expired-retrieve.json : collected by the seller, or lost "
                        + "by the source. --opt recover-orphans=true gives it back, a second time if it had been collected");
                return;
            }
            sink.accept(new ImportedExpired(bin.sourceId(), bin.seller(), null, ImportedItem.ofBukkitBase64(bin.item()), bin.price(), listedAt));
            return;
        }

        UUID buyer;
        try {
            buyer = UUID.fromString(bin.buyer());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unreadable buyer \"" + bin.buyer() + "\"");
        }
        if (buyer.equals(bin.seller())) {
            sink.skip(bin.sourceId(), "Withdrawn by its seller, nothing to import");
            return;
        }
        // Bought : the seller was paid and the item delivered (or put in the buyer's list) by the source.
        sink.accept(new ImportedHistoric(bin.sourceId(), bin.seller(), null, buyer, null, ImportedItem.ofBukkitBase64(bin.item()),
                bin.price(), expiryEnabled ? listedAt : null, null));
    }

    private Map<UUID, List<String>> readStash(ImportContext context, File file, ImportSink sink) throws Exception {
        Map<UUID, List<String>> stash = new HashMap<>();
        JsonElement root = context.helpers().loadJson(file);
        if (root.isJsonNull()) {
            return stash;
        }
        if (!root.isJsonObject() || !root.getAsJsonObject().has("players")) {
            throw new IllegalStateException("expired-retrieve.json has no \"players\" : unknown format");
        }
        int index = 0;
        for (JsonElement element : root.getAsJsonObject().getAsJsonArray("players")) {
            index++;
            try {
                JsonObject player = element.getAsJsonObject();
                UUID uuid = UUID.fromString(player.get("uuid").getAsString());
                List<String> items = stash.computeIfAbsent(uuid, key -> new ArrayList<>());
                for (JsonElement item : player.getAsJsonArray("items")) {
                    items.add(item.getAsString());
                }
            } catch (RuntimeException e) {
                sink.skip("nexus:stash:#" + index, "Unreadable entry of expired-retrieve.json : " + e.getMessage());
            }
        }
        return stash;
    }

    private List<Bin> readBins(ImportContext context, File file, ImportSink sink) throws Exception {
        List<Bin> bins = new ArrayList<>();
        JsonElement root = context.helpers().loadJson(file);
        if (root.isJsonNull()) {
            return bins;
        }
        if (!root.isJsonObject() || !root.getAsJsonObject().has("bins")) {
            throw new IllegalStateException("bins.json has no \"bins\" : unknown format");
        }

        Set<String> seen = new HashSet<>();
        int index = 0;
        for (JsonElement element : root.getAsJsonObject().getAsJsonArray("bins")) {
            index++;
            try {
                JsonObject bin = element.getAsJsonObject();
                UUID seller = UUID.fromString(bin.get("seller").getAsString());
                String item = bin.get("item").getAsString();
                long price = bin.get("price").getAsLong();
                long expiry = bin.get("expiry").getAsLong();
                String buyer = bin.has("buyer") && !bin.get("buyer").isJsonNull() ? bin.get("buyer").getAsString().trim() : "";

                String base = "nexus:bin:" + sha256(seller + "|" + expiry + "|" + price + "|" + normalize(item));
                // Two identical sales listed in the same second : still two rows.
                String sourceId = base;
                for (int rank = 2; !seen.add(sourceId); rank++) {
                    sourceId = base + ":" + rank;
                }
                bins.add(new Bin(sourceId, seller, item, price, expiry, buyer));
            } catch (RuntimeException e) {
                sink.skip("nexus:bin:#" + index, "Unreadable entry of bins.json : " + e.getMessage());
            }
        }
        return bins;
    }

    /**
     * The duration syntax of the source : {@code 7d}, {@code 12h}, {@code 1w2d}..., a bare number
     * being seconds.
     */
    static long parseDuration(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long seconds = 0;
        Matcher matcher = DURATION.matcher(text.trim());
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.ROOT);
            seconds += value * switch (unit) {
                case "y" -> 365L * 86_400;
                case "mo" -> 30L * 86_400;
                case "w" -> 7L * 86_400;
                case "d" -> 86_400L;
                case "h" -> 3_600L;
                case "m" -> 60L;
                default -> 1L;
            };
        }
        return seconds;
    }

    private static String normalize(String base64) {
        return base64.replaceAll("\\s", "");
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
