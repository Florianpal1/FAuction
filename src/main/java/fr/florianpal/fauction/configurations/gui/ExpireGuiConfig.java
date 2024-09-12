package fr.florianpal.fauction.configurations.gui;

import fr.florianpal.fauction.configurations.gui.AbstractGuiWithAuctionsConfig;
import fr.florianpal.fauction.objects.Barrier;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.event.inventory.InventoryType;

import java.util.ArrayList;
import java.util.List;

public class ExpireGuiConfig extends AbstractGuiWithAuctionsConfig {

    private List<Barrier> barrierBlocks = new ArrayList<>();

    private List<Barrier> previousBlocks = new ArrayList<>();

    private List<Barrier> nextBlocks = new ArrayList<>();

    private List<Integer> expireBlocks = new ArrayList<>();

    private List<Barrier> auctionGuiBlocks = new ArrayList<>();

    private String title = "";

    private boolean replaceTitle = true;

    private List<String> description = new ArrayList<>();

    private String nameGui = "";

    protected int size;

    private InventoryType inventoryType;


    public void load(Configuration config) {
        barrierBlocks = new ArrayList<>();
        previousBlocks = new ArrayList<>();
        nextBlocks = new ArrayList<>();
        expireBlocks = new ArrayList<>();
        auctionGuiBlocks = new ArrayList<>();
        description = new ArrayList<>();

        for (String index : config.getConfigurationSection("block").getKeys(false)) {
            if (config.getString("block." + index + ".utility").equalsIgnoreCase("previous")) {

                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        new Barrier(
                                Integer.parseInt(index),
                                Material.getMaterial(config.getString("block." + index + ".replacement.material")),
                                config.getString("block." + index + ".replacement.title"),
                                config.getStringList("block." + index + ".replacement.description"),
                                config.getString("block." + index + ".replacement.texture", ""),
                                config.getInt("block." + index + ".replacement.customModelData", 0)
                        ),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                previousBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("next")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        new Barrier(
                                Integer.parseInt(index),
                                Material.getMaterial(config.getString("block." + index + ".replacement.material")),
                                config.getString("block." + index + ".replacement.title"),
                                config.getStringList("block." + index + ".replacement.description"),
                                config.getString("block." + index + ".replacement.texture", ""),
                                config.getInt("block." + index + ".replacement.customModelData", 0)
                        ),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)

                );
                nextBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("barrier")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                barrierBlocks.add(barrier);
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("expire")) {
                expireBlocks.add(Integer.valueOf(index));
            } else if (config.getString("block." + index + ".utility").equalsIgnoreCase("auctionGui")) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material")),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                auctionGuiBlocks.add(barrier);
         }
        }
        size = config.getInt("gui.size");
        nameGui = config.getString("gui.name");
        title = config.getString("gui.title");
        replaceTitle = config.getBoolean("gui.replaceTitle", true);
        description.addAll(config.getStringList("gui.description"));
        inventoryType = InventoryType.valueOf(config.getString("gui.type", "CHEST"));
    }

    public String getTitle() {
        return title;
    }

    public List<String> getDescription() {
        return description;
    }

    public String getNameGui() {
        return nameGui;
    }

    public List<Barrier> getBarrierBlocks() {
        return barrierBlocks;
    }

    public List<Barrier> getPreviousBlocks() {
        return previousBlocks;
    }

    public List<Barrier> getNextBlocks() {
        return nextBlocks;
    }

    @Override
    public List<Integer> getAuctionBlocks() {
        return expireBlocks;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public InventoryType getType() {
        return inventoryType;
    }

    public List<Integer> getExpireBlocks() {
        return expireBlocks;
    }

    public List<Barrier> getAuctionGuiBlocks() {
        return auctionGuiBlocks;
    }

    public boolean isReplaceTitle() {
        return replaceTitle;
    }
}
