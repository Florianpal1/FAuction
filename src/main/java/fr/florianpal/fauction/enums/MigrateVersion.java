package fr.florianpal.fauction.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The versions {@code /ah admin migrate} knows how to migrate to.
 * <p>
 * The single source of truth for both the tab-completion and the migration itself : the command used
 * to take a free {@code String}, and {@code FAuction.migrate} had one {@code case} and no
 * {@code default}, so {@code /ah admin migrate 1.7.9} did nothing at all and still answered "you
 * have migrated to version 1.7.9". A success message for a database migration that never ran.
 */
public enum MigrateVersion {

    V_1_7_8("1.7.8");

    private final String id;

    MigrateVersion(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static Optional<MigrateVersion> byId(String id) {
        return Arrays.stream(values()).filter(version -> version.id.equals(id)).findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(MigrateVersion::getId).collect(Collectors.toList());
    }
}
