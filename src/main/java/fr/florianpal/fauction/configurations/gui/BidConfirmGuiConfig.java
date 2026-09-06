package fr.florianpal.fauction.configurations.gui;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.objects.Confirm;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class BidConfirmGuiConfig extends AbstractGuiWithAuctionsConfig {

    private String titleTrue = "";

    private String titleFalse = "";

    private String bidTitle = "";

    private List<String> bidDescription = new ArrayList<>();

    public void load(FAuction plugin, YamlDocument config) {

        super.load(plugin, config, "bid");

        titleTrue = config.getString("gui.title-true");
        titleFalse = config.getString("gui.title-false");

        bidTitle = config.getString("gui.bid.title");
        replaceTitle = config.getBoolean("gui.bid.replaceTitle");
        bidDescription = config.getStringList("gui.bid.description");
    }

    public List<Confirm> getConfirmBlocks() {
        return confirmBlocks;
    }
}
