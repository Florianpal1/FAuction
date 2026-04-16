package fr.florianpal.fauction.configurations.gui;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.BlockType;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.BarrierMenu;
import fr.florianpal.fauction.objects.BarrierWithCategory;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static fr.florianpal.fauction.enums.BlockType.*;

@Getter
public abstract class AbstractGuiConfig {

    protected InventoryType inventoryType;

    protected String nameGui = "";

    protected int size;

    private List<BarrierWithCategory> auctionGuiBlocks = new ArrayList<>();

    private List<Barrier> closeBlocks = new ArrayList<>();

    private List<Barrier> commandBlocks = new ArrayList<>();

    private List<BarrierMenu> menuBlocks = new ArrayList<>();

    private List<BarrierWithCategory> playerBlocks = new ArrayList<>();

    private List<BarrierWithCategory> historicBlocks = new ArrayList<>();

    private List<Barrier> barrierBlocks = new ArrayList<>();

    private List<BarrierWithCategory> expireBlocks = new ArrayList<>();

    public void load(FAuction plugin, YamlDocument config, String baseBlock) {

        barrierBlocks = new ArrayList<>();
        expireBlocks = new ArrayList<>();
        closeBlocks = new ArrayList<>();
        playerBlocks = new ArrayList<>();
        historicBlocks = new ArrayList<>();
        auctionGuiBlocks = new ArrayList<>();
        menuBlocks = new ArrayList<>();
        commandBlocks = new ArrayList<>();

        size = config.getInt("gui.size");
        nameGui = config.getString("gui.name");
        inventoryType = InventoryType.valueOf(config.getString("gui.type", "CHEST"));

        for (Object indexObject : config.getSection("block").getKeys()) {

            String index = indexObject.toString();
            String currentUtility = config.getString("block." + index + ".utility");

            if (Arrays.stream(BlockType.values()).noneMatch(b -> b.equalsIgnoreCase(currentUtility) || currentUtility.equalsIgnoreCase(baseBlock))) {
                plugin.getLogger().severe("Error : unknown block type " + currentUtility);
                return;
            }

            if (PLAYER.equalsIgnoreCase(currentUtility)) {
                BarrierWithCategory barrier = new BarrierWithCategory(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0),
                        plugin.getConfigurationManager().getCategoriesConfig().getCategories().getOrDefault(config.getString("block." + index + ".category", null), null)
                );
                playerBlocks.add(barrier);
            } else if (EXPIREGUI.equalsIgnoreCase(currentUtility)) {
                BarrierWithCategory barrier = new BarrierWithCategory(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0),
                        plugin.getConfigurationManager().getCategoriesConfig().getCategories().getOrDefault(config.getString("block." + index + ".category", null), null)
                );
                expireBlocks.add(barrier);
            } else if (HISTORICGUI.equalsIgnoreCase(currentUtility)) {
                BarrierWithCategory barrier = new BarrierWithCategory(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0),
                        plugin.getConfigurationManager().getCategoriesConfig().getCategories().getOrDefault(config.getString("block." + index + ".category", null), null)
                );
                historicBlocks.add(barrier);
            } else if (BARRIER.equalsIgnoreCase(currentUtility)) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        null,
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                barrierBlocks.add(barrier);
            } else if (CLOSE.equalsIgnoreCase(currentUtility)) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        null,
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                closeBlocks.add(barrier);
            } else if (MENU.equalsIgnoreCase(currentUtility)) {
                BarrierMenu barrier = new BarrierMenu(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0),
                        config.getString("block." + index + ".id", "main")
                );
                menuBlocks.add(barrier);
            } else if (AUCTIONGUI.equalsIgnoreCase(currentUtility)) {
                BarrierWithCategory barrier = new BarrierWithCategory(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0),
                        plugin.getConfigurationManager().getCategoriesConfig().getCategories().getOrDefault(config.getString("block." + index + ".category", null), null)
                );
                auctionGuiBlocks.add(barrier);
            } else if (COMMAND.equalsIgnoreCase(currentUtility)) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".value"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                commandBlocks.add(barrier);
            }
        }
    }
}
