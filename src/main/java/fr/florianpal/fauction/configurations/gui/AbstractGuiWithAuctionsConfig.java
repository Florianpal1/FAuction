package fr.florianpal.fauction.configurations.gui;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.BlockType;
import fr.florianpal.fauction.objects.Barrier;
import fr.florianpal.fauction.objects.Confirm;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static fr.florianpal.fauction.enums.BlockType.*;

public abstract class AbstractGuiWithAuctionsConfig extends AbstractGuiConfig {

    protected LinkedHashMap<Integer, Integer> baseBlocks = new LinkedHashMap<>();

    protected List<Barrier> previousBlocks = new ArrayList<>();

    protected List<Barrier> nextBlocks = new ArrayList<>();

    protected List<Barrier> categoriesBlocks = new ArrayList<>();

    protected List<Barrier> sortingBlocks = new ArrayList<>();

    protected List<Confirm> confirmBlocks = new ArrayList<>();

    protected String title = "";

    protected boolean replaceTitle = true;

    protected List<String> description = new ArrayList<>();

    @Override
    public void load(FAuction plugin, YamlDocument config, String baseBlock) {

        super.load(plugin, config, baseBlock);

        previousBlocks = new ArrayList<>();
        nextBlocks = new ArrayList<>();
        baseBlocks = new LinkedHashMap<>();
        description = new ArrayList<>();
        categoriesBlocks = new ArrayList<>();
        sortingBlocks = new ArrayList<>();
        confirmBlocks = new ArrayList<>();
        int baseCounter = 0;

        for (Object indexObject : config.getSection("block").getKeys()) {

            String index = indexObject.toString();
            String currentUtility = config.getString("block." + index + ".utility");

            if (Arrays.stream(BlockType.values()).noneMatch(b -> b.equalsIgnoreCase(currentUtility) || currentUtility.equalsIgnoreCase(baseBlock))) {
                plugin.getLogger().severe("Error : unknown block type " + currentUtility);
                return;
            }

            if (PREVIOUS.equalsIgnoreCase(currentUtility)) {

                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        null,
                        new Barrier(
                                Integer.parseInt(index),
                                Material.getMaterial(config.getString("block." + index + ".replacement.material", Material.BARRIER.toString())),
                                config.getString("block." + index + ".replacement.title"),
                                config.getStringList("block." + index + ".replacement.description"),
                                null,
                                config.getString("block." + index + ".replacement.texture", ""),
                                config.getInt("block." + index + ".replacement.customModelData", 0)
                        ),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                previousBlocks.add(barrier);
            } else if (NEXT.equalsIgnoreCase(currentUtility)) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        null,
                        new Barrier(
                                Integer.parseInt(index),
                                Material.getMaterial(config.getString("block." + index + ".replacement.material", Material.BARRIER.toString())),
                                config.getString("block." + index + ".replacement.title"),
                                config.getStringList("block." + index + ".replacement.description"),
                                null,
                                config.getString("block." + index + ".replacement.texture", ""),
                                config.getInt("block." + index + ".replacement.customModelData", 0)
                        ),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                nextBlocks.add(barrier);
            } else if (CATEGORY.equalsIgnoreCase(currentUtility)) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        null,
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                categoriesBlocks.add(barrier);
            } else if (SORTING.equalsIgnoreCase(currentUtility)) {
                Barrier barrier = new Barrier(
                        Integer.parseInt(index),
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getString("block." + index + ".title"),
                        config.getStringList("block." + index + ".description"),
                        null,
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)
                );
                sortingBlocks.add(barrier);
            } else if (baseBlock.equalsIgnoreCase(currentUtility)) {

                baseBlocks.put(baseCounter, Integer.valueOf(index));
                baseCounter = baseCounter + 1;
            } else if (CONFIRM.equalsIgnoreCase(currentUtility)) {
                confirmBlocks.add(new Confirm(Integer.parseInt(index), null,
                        Material.getMaterial(config.getString("block." + index + ".material", Material.BARRIER.toString())),
                        config.getBoolean("block." + index + ".value"),
                        config.getString("block." + index + ".texture", ""),
                        config.getInt("block." + index + ".customModelData", 0)));
            }
        }

        title = config.getString("gui.title");
        replaceTitle = config.getBoolean("gui.replaceTitle", true);
        description.addAll(config.getStringList("gui.description"));
    }

    public LinkedHashMap<Integer, Integer> getBaseBlocks() {
        return baseBlocks;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getDescription() {
        return description;
    }

    public List<Barrier> getPreviousBlocks() {
        return previousBlocks;
    }

    public List<Barrier> getNextBlocks() {
        return nextBlocks;
    }

    public boolean isReplaceTitle() {
        return replaceTitle;
    }

    public List<Barrier> getCategoriesBlocks() {
        return categoriesBlocks;
    }

    public List<Barrier> getSortingBlocks() {
        return sortingBlocks;
    }
}
