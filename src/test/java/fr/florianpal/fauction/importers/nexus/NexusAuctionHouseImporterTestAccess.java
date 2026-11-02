package fr.florianpal.fauction.importers.nexus;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The fixture of {@link NexusAuctionHouseImporterTest}, for the end-to-end tests of another package.
 */
public final class NexusAuctionHouseImporterTestAccess {

    private NexusAuctionHouseImporterTestAccess() {
    }

    public static void install(Path plugins) throws IOException {
        NexusAuctionHouseImporterTest.install(plugins, true, 0);
    }
}
