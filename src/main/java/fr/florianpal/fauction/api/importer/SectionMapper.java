package fr.florianpal.fauction.api.importer;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Converts one entry of a YAML file into one of the {@code Imported*} records.
 *
 * @param <T> the record produced.
 */
@FunctionalInterface
public interface SectionMapper<T extends ImportedRow> {

    /**
     * @param key     the key of the entry under the root section, a natural {@code sourceId}.
     * @param section the entry.
     * @return the row to import, or {@code null} to ignore it silently.
     * @throws ImportSkipException to skip the row with a reason.
     * @throws Exception           any other failure skips the row too.
     */
    @Nullable T map(String key, ConfigurationSection section, ImportContext context) throws Exception;
}
