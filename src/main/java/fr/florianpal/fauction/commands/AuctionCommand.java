package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.MigrateVersion;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.events.AuctionAddEvent;
import fr.florianpal.fauction.gui.subGui.*;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.ClaimManager;
import fr.florianpal.fauction.managers.SpamManager;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.ListUtil;
import fr.florianpal.fauction.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.parser.Parser;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.ceil;

public class AuctionCommand {

    /**
     * The root of every command path. {@code hdv} is the historical alias, declared as a literal of
     * the path exactly like ACF's {@code @CommandAlias} did.
     */
    private static final String ROOT = "ah|hdv ";

    static final String PRICE_PARSER = "fauction:price";

    static final String MIGRATE_VERSION_PARSER = "fauction:migrate_version";

    static final String MIGRATE_VERSION_SUGGESTIONS = "fauction:migrate_versions";

    private final FAuction plugin;

    private final AuctionCommandManager auctionCommandManager;

    private final ExpireCommandManager expireCommandManager;

    private final HistoricCommandManager historicCommandManager;

    private final SpamManager spamManager;

    private final GlobalConfig globalConfig;

    private List<Integer> itemHash = new ArrayList<>();

    protected DecimalFormat df;

    public AuctionCommand(FAuction plugin) {
        this.plugin = plugin;
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.expireCommandManager = plugin.getExpireCommandManager();
        this.spamManager = plugin.getSpamManager();
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();
        this.historicCommandManager = plugin.getHistoricCommandManager();

        df = new DecimalFormat(plugin.getConfigurationManager().getGlobalConfig().getDecimalFormat());
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    // Two paths, one handler, as under ACF (@Default + @Subcommand("list")) : a bare /ah is the
    // main entry point of the plugin and must not be lost.
    @Command("ah|hdv")
    @Command(ROOT + "list")
    @Permission("fauction.list")
    @CommandDescription("{@@fauction.auction_list_help_description}")
    public void onList(Player playerSender) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
            return;
        }

        switch (globalConfig.getDefaultGui()) {
            case "AUCTION":
                FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    AuctionsGui auctionsGui = new AuctionsGui(plugin, playerSender, auctions, 1, null, null);
                    auctionsGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "EXPIRE":
                FAuction.newChain().asyncFirst(() -> expireCommandManager.getExpires(playerSender.getUniqueId())).syncLast(expires -> {
                    ExpireGui expireGui = new ExpireGui(plugin, playerSender, expires, 1, null, null);
                    expireGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "HISTORIC":
                FAuction.newChain().asyncFirst(() -> historicCommandManager.getHistorics(playerSender.getUniqueId())).syncLast(historics -> {
                    HistoricGui historicGui = new HistoricGui(plugin, playerSender, ListUtil.historicToAuction(historics), 1, null, null);
                    historicGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "PLAYER":
                FAuction.newChain().asyncFirst(() -> auctionCommandManager.getAuctions(playerSender.getUniqueId())).syncLast(auctions -> {
                    PlayerViewGui playerViewGui = new PlayerViewGui(plugin, playerSender, auctions, 1, null, null);
                    playerViewGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            default:
                String[] id = globalConfig.getDefaultGui().split(":");

                if (!"MENU".equals(id[0])) {
                    return;
                }

                MainGui gui = new MainGui(plugin, id[1], playerSender, 1);
                gui.initialize();
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                break;

        }

    }

    @Command(ROOT + "search <material>")
    @Permission("fauction.search")
    @CommandDescription("{@@fauction.auction_search_help_description}")
    public void onSearch(Player playerSender, Material material) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
            return;
        }

        if (material.isAir()) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SEARCH_AIR);
            return;
        }

        FAuction.newChain().asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
            AuctionsGui auctionsGui = new AuctionsGui(plugin, playerSender, auctions, 1, new Category("-1", material.name(), List.of(material.toString())), null);
            auctionsGui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Command(ROOT + "sell <priceEntry>")
    @Permission("fauction.sell")
    @CommandDescription("{@@fauction.auction_add_help_description}")
    public void onAdd(Player playerSender, @Argument(value = "priceEntry", parserName = PRICE_PARSER) double priceEntry) {

        if (spamManager.spamTest(playerSender, SpamAction.TRANSACTION)) {
            return;
        }

        // Reserved on the main thread : only one sale in flight per player, so the other packets of
        // the same tick cannot start a second sale of the very same item.
        long saleClaim = plugin.getClaimManager().tryClaim(playerSender.getUniqueId());
        if (saleClaim == ClaimManager.NOT_CLAIMED) {
            return;
        }

        ItemStack itemToSell = playerSender.getInventory().getItemInMainHand().clone();

        double price = FormatUtil.applyMoneyFormat(priceEntry, df, globalConfig.isFeatureFlippingMoneyFormat());

        // Everything is checked while the item is still in the inventory, so a refused sale has
        // nothing to give back.
        if (!isSellable(playerSender, itemToSell, price)) {
            plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            return;
        }

        AtomicBoolean itemTaken = new AtomicBoolean(false);

        FAuction.newChain().asyncFirst(() -> plugin.getAuctionCommandManager().getAuctions(playerSender.getUniqueId())).syncLast(auctions -> {

            int limitations;
            if (plugin.getConfigurationManager().getGlobalConfig().isLimitationsUseMetaLuckperms()) {
                limitations = plugin.getLimitationManager().getAuctionLimitationByMeta(playerSender);
            } else {
                limitations = plugin.getLimitationManager().getAuctionLimitationByConfig(playerSender);
            }


            if (limitations != -1 && limitations <= auctions.size()) {
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_AUCTION);
                return;
            }

            // The hand may have changed during the round trip, only the checked item can be sold.
            ItemStack inHand = playerSender.getInventory().getItemInMainHand();
            if (!inHand.isSimilar(itemToSell) || inHand.getAmount() != itemToSell.getAmount()) {
                return;
            }

            if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                if (itemHash.contains((Integer) itemToSell.hashCode())) {
                    return;
                }
                itemHash.add((Integer) itemToSell.hashCode());
            }

            // Taken and resynchronised right away, so the client cannot keep a ghost item and send
            // it back to the server.
            playerSender.getInventory().setItemInMainHand(null);
            playerSender.updateInventory();
            itemTaken.set(true);

            FAuction.newChain().asyncFirst(() -> auctionCommandManager.addAuction(playerSender, itemToSell, price)).syncLast(added -> {

                // The hash only guards this sale while it is in flight ; it must not survive the
                // round trip, or every future sale of a visually identical item would be blocked
                // forever and the list would grow without bound.
                if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                    itemHash.remove((Integer) itemToSell.hashCode());
                }

                if (!Boolean.TRUE.equals(added)) {
                    plugin.getLogger().severe("Auction of " + playerSender.getName() + " could not be saved, item given back");
                    resetItem(playerSender, itemToSell);
                    return;
                }

                String itemName = itemToSell.getItemMeta().getDisplayName() == null || itemToSell.getItemMeta().getDisplayName().isEmpty() ? itemToSell.getType().toString() : itemToSell.getItemMeta().getDisplayName();
                plugin.getLogger().info("Player " + playerSender.getName() + " add item to ah Item : " + itemName + ", At Price : " + price);

                Bukkit.getPluginManager().callEvent(new AuctionAddEvent(playerSender, itemToSell, price));

                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_ADD_SUCCESS, "{item}", FormatUtil.titleItemFormat(itemToSell), "{price}", df.format(price));
            }).execute(() -> plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim));

        }).execute(() -> {
            // The sale is released by the chain that really took the item, if any.
            if (!itemTaken.get()) {
                plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            }
        });
    }

    /**
     * Every check done before the item leaves the inventory.
     */
    private boolean isSellable(Player playerSender, ItemStack itemToSell, double price) {

        if (itemToSell.getType().equals(Material.AIR)) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_AIR);
            return false;
        }

        if (price < 0) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.NEGATIVE_PRICE);
            return false;
        }

        if (globalConfig.getBlacklistItem().contains(itemToSell.getType())) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_BLACKLIST);
            return false;
        }

        if (!haveCorrectMinPrice(itemToSell, playerSender, price)) {
            return false;
        }

        if (!haveCorrectMaxPrice(itemToSell, playerSender, price)) {
            return false;
        }

        return haveCorrectShulkerPrice(playerSender, itemToSell, price);
    }

    public boolean haveCorrectShulkerPrice(Player playerSender, ItemStack itemToSell, double price) {

        if (!Tag.SHULKER_BOXES.getValues().contains(itemToSell.getType())) {
            return true;
        }

        if (!(itemToSell.getItemMeta() instanceof BlockStateMeta im) || !(im.getBlockState() instanceof ShulkerBox shulker)) {
            return true;
        }

        double minPrice = 0;
        double maxPrice = 0;
        boolean minPriceSet = false;
        boolean maxPriceSet = false;

        for (ItemStack itemIn : shulker.getInventory().getContents()) {
            if (itemIn != null && (itemIn.getType() != Material.AIR)) {
                if (globalConfig.getMinPrice().containsKey(itemIn.getType())) {
                    minPrice += itemIn.getAmount() * globalConfig.getMinPrice().get(itemIn.getType());
                    minPriceSet = true;
                } else if (globalConfig.isDefaultMinValueEnable()) {
                    minPrice += itemIn.getAmount() * globalConfig.getDefaultMinValue();
                    minPriceSet = true;
                }

                if (globalConfig.getMaxPrice().containsKey(itemIn.getType())) {
                    maxPrice += itemIn.getAmount() * globalConfig.getMaxPrice().get(itemIn.getType());
                    maxPriceSet = true;
                } else if (globalConfig.isDefaultMaxValueEnable()) {
                    maxPrice += itemIn.getAmount() * globalConfig.getDefaultMaxValue();
                    maxPriceSet = true;
                }
            }
        }

        if (minPriceSet && minPrice > price) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIN_PRICE, "{minPrice}", df.format(ceil(minPrice)));
            return false;
        }

        if (maxPriceSet && maxPrice < price) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_PRICE, "{maxPrice}", df.format(ceil(maxPrice)));
            return false;
        }

        return true;
    }

    public boolean haveCorrectMinPrice(ItemStack itemToSell, Player player, double price) {

        if (globalConfig.getMinPrice().containsKey(itemToSell.getType())) {
            double minPrice = itemToSell.getAmount() * globalConfig.getMinPrice().get(itemToSell.getType());
            if (minPrice > price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MIN_PRICE, "{minPrice}", df.format(ceil(minPrice)));
                return false;
            }
        } else if (globalConfig.isDefaultMinValueEnable()) {
            double minPrice = itemToSell.getAmount() * globalConfig.getDefaultMinValue();
            if (minPrice > price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MIN_PRICE, "{minPrice}", df.format(ceil(minPrice)));
                return false;
            }
        }

        return true;
    }

    /**
     * Gives an item back to a player. Never writes in the main hand, which would destroy whatever
     * the player put there meanwhile.
     */
    public void resetItem(Player playerSender, ItemStack item) {
        if (playerSender.getInventory().firstEmpty() == -1) {
            playerSender.getWorld().dropItem(playerSender.getLocation(), item);
        } else {
            playerSender.getInventory().addItem(item);
        }
    }

    public boolean haveCorrectMaxPrice(ItemStack itemToSell, Player player, double price) {

        if (globalConfig.getMaxPrice().containsKey(itemToSell.getType())) {
            double maxPrice = itemToSell.getAmount() * globalConfig.getMaxPrice().get(itemToSell.getType());
            if (maxPrice < price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MAX_PRICE, "{maxPrice}", df.format(ceil(maxPrice)));
                return false;
            }
        } else if (globalConfig.isDefaultMaxValueEnable()) {
            double maxPrice = itemToSell.getAmount() * globalConfig.getDefaultMaxValue();
            if (maxPrice < price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MAX_PRICE, "{maxPrice}", df.format(ceil(maxPrice)));
                return false;
            }
        }
        return true;
    }

    @Command(ROOT + "expire")
    @Permission("fauction.expire")
    @CommandDescription("{@@fauction.expire_add_help_description}")
    public void onExpire(Player playerSender) {

        FAuction.newChain().asyncFirst(() -> expireCommandManager.getExpires(playerSender.getUniqueId())).syncLast(auctions -> {
            ExpireGui gui = new ExpireGui(plugin, playerSender, auctions, 1, null, null);
            gui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Command(ROOT + "admin reload")
    @Permission("fauction.admin.reload")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onReload(Player playerSender) {

        plugin.reloadConfiguration();
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_RELOAD);
    }

    @Command(ROOT + "admin purge all")
    @Permission("fauction.admin.purge.all")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAll(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllData();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + "admin purge historic")
    @Permission("fauction.admin.purge.hictoric")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAllHistoric(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllHistoric();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + "admin purge expire")
    @Permission("fauction.admin.purge.expire")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAllExpire(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllExpire();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + "admin purge auction")
    @Permission("fauction.admin.purge.auction")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAllAucton(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllAuction();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + "admin transfertToPaper")
    @Permission("fauction.admin.transfertBddToPaper")
    @CommandDescription("{@@fauction.transfert_bdd_help_description}")
    public void onTransferBddPaper(Player playerSender) {

        plugin.getTransfertManager().transfertBDD(true);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.TRANSFERT_BDD);
    }

    @Command(ROOT + "admin transfertToBukkit")
    @Permission("fauction.admin.transfertBddToPaper")
    @CommandDescription("{@@fauction.transfert_bdd_help_description}")
    public void onTransferBddSpigot(Player playerSender) {

        plugin.getTransfertManager().transfertBDD(false);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.TRANSFERT_BDD);
    }

    @Command(ROOT + "admin migrate <migrateVersion>")
    @Permission("fauction.admin.migrate")
    @CommandDescription("{@@fauction.migrate_help_description}")
    public void onMigrate(Player playerSender,
                          @Argument(value = "migrateVersion", parserName = MIGRATE_VERSION_PARSER) MigrateVersion migrateVersion) {

        plugin.migrate(migrateVersion);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIGRATE, "{version}", migrateVersion.getId());
    }

    @Command(ROOT + "help [query]")
    @CommandDescription("{@@fauction.help_description}")
    public void doHelp(CommandSender sender, @Argument("query") @Greedy String query) {
        plugin.getCommandManager().help(sender, query == null ? "" : query);
    }

    /**
     * The price of a sale, checked before the handler runs — so before the ClaimManager reservation
     * and before the item leaves the inventory.
     * <p>
     * Every comparison involving {@code NaN} is false, so {@code price < 0}, {@code minPrice > price}
     * and {@code maxPrice < price} all let it through, and {@code applyMoneyFormat} hands the input
     * back unchanged when it cannot parse it. {@code Infinity} only ever hit a maximum price when one
     * happened to be configured. Neither can reach the business code any more.
     */
    @Parser(name = PRICE_PARSER)
    public double parsePrice(CommandInput input) {
        String token = input.readString();

        double price;
        try {
            price = Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new NotANumberException(token);
        }

        if (!Double.isFinite(price) || price < 0) {
            throw new InvalidPriceException(token);
        }

        return price;
    }

    /**
     * A migration version the plugin actually knows how to run. An unknown version is refused here
     * instead of being announced as a success by the handler.
     */
    @Parser(name = MIGRATE_VERSION_PARSER, suggestions = MIGRATE_VERSION_SUGGESTIONS)
    public MigrateVersion parseMigrateVersion(CommandInput input) {
        String token = input.readString();
        return MigrateVersion.byId(token).orElseThrow(() -> new UnknownMigrateVersionException(token));
    }

    @Suggestions(MIGRATE_VERSION_SUGGESTIONS)
    public List<String> migrateVersionSuggestions(CommandContext<CommandSender> context, CommandInput input) {
        return MigrateVersion.ids();
    }

    /**
     * The input is not a number at all. Carries the input so the message can name it, like the
     * {@code must_be_a_number} of ACF did.
     */
    public static final class NotANumberException extends IllegalArgumentException {

        private final transient String input;

        NotANumberException(String input) {
            super("Not a number : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }

    /**
     * The input is a number, but not a price : negative, {@code NaN} or infinite.
     */
    public static final class InvalidPriceException extends IllegalArgumentException {

        private final transient String input;

        InvalidPriceException(String input) {
            super("Not a valid price : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }

    public static final class UnknownMigrateVersionException extends IllegalArgumentException {

        private final transient String input;

        UnknownMigrateVersionException(String input) {
            super("Unknown migration version : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }
}