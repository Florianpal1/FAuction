package fr.florianpal.fauction.commands;

import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.BukkitLocales;
import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.managers.commandmanagers.CommandManager;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuctionCommandTest extends FAuctionTestBase {

    private AuctionCommand command;

    @BeforeEach
    void setUpCommand() {

        when(globalConfig.getDecimalFormat()).thenReturn("0.00");

        // haveCorrectShulkerPrice sends a message on refusal ; give MessageUtil an ACF stack that
        // resolves to "no message configured" instead of null-ing out on the mocked plugin.
        CommandManager commandManager = mock(CommandManager.class);
        BukkitCommandIssuer issuer = mock(BukkitCommandIssuer.class);
        BukkitLocales locales = mock(BukkitLocales.class);
        when(plugin.getCommandManager()).thenReturn(commandManager);
        when(commandManager.getCommandIssuer(any())).thenReturn(issuer);
        when(commandManager.getLocales()).thenReturn(locales);
        when(locales.getOptionalMessage(any(), any())).thenReturn(null);

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

    @Test
    @DisplayName("A bid start price ignores the default min/max price bounds when the toggle is off")
    void bidIgnoresDefaultBoundsWhenToggleIsOff() {

        when(globalConfig.isBidApplyDefaultPriceLimits()).thenReturn(false);
        when(globalConfig.getBlacklistItem()).thenReturn(java.util.List.of());

        Player player = server.addPlayer();
        ItemStack gem = namedItem(Material.DIAMOND, 1, "Gem");

        // Would be refused by haveCorrectMinPrice if the bounds applied ; the toggle being off, only
        // the negative-price and blacklist checks still run.
        when(globalConfig.isDefaultMinValueEnable()).thenReturn(true);
        when(globalConfig.getDefaultMinValue()).thenReturn(1000.0);
        when(globalConfig.getMinPrice()).thenReturn(Map.of());

        assertTrue(command.isBidStartable(player, gem, 1.0));
    }

    @Test
    @DisplayName("A bid start price is bound by the default min/max prices when the toggle is on")
    void bidRespectsDefaultBoundsWhenToggleIsOn() {

        when(globalConfig.isBidApplyDefaultPriceLimits()).thenReturn(true);
        when(globalConfig.getBlacklistItem()).thenReturn(java.util.List.of());
        when(globalConfig.getMinPrice()).thenReturn(Map.of());
        when(globalConfig.getMaxPrice()).thenReturn(Map.of());
        when(globalConfig.isDefaultMinValueEnable()).thenReturn(true);
        when(globalConfig.getDefaultMinValue()).thenReturn(1000.0);
        when(globalConfig.isDefaultMaxValueEnable()).thenReturn(false);

        Player player = server.addPlayer();
        ItemStack gem = namedItem(Material.DIAMOND, 1, "Gem");

        assertFalse(command.isBidStartable(player, gem, 1.0));
    }

    @Test
    @DisplayName("A negative bid start price is always refused")
    void negativeBidStartPriceIsRefused() {

        when(globalConfig.isBidApplyDefaultPriceLimits()).thenReturn(false);
        when(globalConfig.getBlacklistItem()).thenReturn(java.util.List.of());

        Player player = server.addPlayer();
        ItemStack gem = namedItem(Material.DIAMOND, 1, "Gem");

        assertFalse(command.isBidStartable(player, gem, -1.0));
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
}
