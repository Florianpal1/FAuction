package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.enums.MigrateVersion;
import fr.florianpal.fauction.languages.Lang;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.incendo.cloud.context.CommandInput;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AuctionCommandTest extends FAuctionTestBase {

    private AuctionCommand command;

    @BeforeEach
    void setUpCommand() {

        when(globalConfig.getDecimalFormat()).thenReturn("0.00");

        // haveCorrectShulkerPrice sends a message on refusal : an empty Lang resolves every key to
        // "no message configured", so the price rules can be exercised without a language file.
        when(plugin.getLang()).thenReturn(new Lang());

        command = new AuctionCommand(plugin);
    }

    @Test
    @DisplayName("A shulker box priced at the exact sum of its content's minimum prices is accepted")
    void shulkerPriceAtExactMinimumSumIsAccepted() {

        when(globalConfig.getMinPrice()).thenReturn(Map.of(Material.DIAMOND, 10.0));
        when(globalConfig.getMaxPrice()).thenReturn(Map.of());

        ItemStack shulker = shulkerWith(namedItem(Material.DIAMOND, 2, "Diamonds"));
        Player player = server.addPlayer();

        assertTrue(command.haveCorrectShulkerPrice(player, shulker, 20.0));
        assertFalse(command.haveCorrectShulkerPrice(player, shulker, 19.0));
    }

    @Test
    @DisplayName("A shulker box priced at the exact sum of its content's maximum prices is accepted")
    void shulkerPriceAtExactMaximumSumIsAccepted() {

        when(globalConfig.getMinPrice()).thenReturn(Map.of());
        when(globalConfig.getMaxPrice()).thenReturn(Map.of(Material.DIAMOND, 10.0));

        ItemStack shulker = shulkerWith(namedItem(Material.DIAMOND, 2, "Diamonds"));
        Player player = server.addPlayer();

        assertTrue(command.haveCorrectShulkerPrice(player, shulker, 20.0));
        assertFalse(command.haveCorrectShulkerPrice(player, shulker, 21.0));
    }

    private ItemStack shulkerWith(ItemStack... contents) {
        ItemStack shulkerItem = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
        ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
        shulkerBox.getInventory().setContents(contents);
        meta.setBlockState(shulkerBox);
        shulkerItem.setItemMeta(meta);
        return shulkerItem;
    }

    @Test
    @DisplayName("A price that is not a finite positive number never reaches the handler")
    void priceParserRefusesWhatTheChecksLetThrough() {

        assertEquals(12.5, command.parsePrice(CommandInput.of("12.5")));
        assertEquals(0.0, command.parsePrice(CommandInput.of("0")));

        // Every comparison involving NaN is false, so the price checks of the handler let it through.
        assertThrows(AuctionCommand.InvalidPriceException.class, () -> command.parsePrice(CommandInput.of("NaN")));
        assertThrows(AuctionCommand.InvalidPriceException.class, () -> command.parsePrice(CommandInput.of("Infinity")));
        assertThrows(AuctionCommand.InvalidPriceException.class, () -> command.parsePrice(CommandInput.of("-1")));

        assertThrows(AuctionCommand.NotANumberException.class, () -> command.parsePrice(CommandInput.of("abc")));
    }

    @Test
    @DisplayName("An unknown migration version is refused instead of being announced as a success")
    void migrateVersionParserRefusesUnknownVersions() {

        assertEquals(MigrateVersion.V_1_7_8, command.parseMigrateVersion(CommandInput.of("1.7.8")));

        assertThrows(AuctionCommand.UnknownMigrateVersionException.class,
                () -> command.parseMigrateVersion(CommandInput.of("1.7.9")));
    }

    @Test
    @DisplayName("The versions suggested are the versions the plugin can migrate to")
    void migrateVersionSuggestionsComeFromTheSameSource() {
        assertEquals(MigrateVersion.ids(), command.migrateVersionSuggestions());
    }
}
