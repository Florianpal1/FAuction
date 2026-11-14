package fr.florianpal.fauction.importers.auctionhouse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * The fixture of {@link AuctionHouseImporterTest}, for the end-to-end tests of another package.
 */
public final class AuctionHouseImporterTestAccess {

    public static final UUID SELLER = AuctionHouseImporterTest.SELLER;

    public static final UUID BUYER = AuctionHouseImporterTest.BUYER;

    private AuctionHouseImporterTestAccess() {
    }

    public static void install(Path plugins) throws IOException {
        AuctionHouseImporterTest.install(plugins, "data/notes.json");
    }
}
