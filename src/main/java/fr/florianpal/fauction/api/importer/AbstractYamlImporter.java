package fr.florianpal.fauction.api.importer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Base class of a module reading a YAML file in which every entry of a root section is a row. The
 * module gives the file, the root sections and a {@link SectionMapper} ; the loop, the cancellation
 * and the errors of a single entry (skipped instead of stopping the import) are handled here.
 * <pre>{@code
 * public class MyImporter extends AbstractYamlImporter {
 *     protected File file(ImportContext ctx) {
 *         return new File(ctx.helpers().sourceDataFolder("MyAH"), "listings.yml");
 *     }
 *     protected List<YamlSource<?>> sources(ImportContext ctx) {
 *         return List.of(new YamlSource<>(ImportDataType.AUCTION, "listings", (key, section, c) ->
 *             new ImportedAuction(key, UUID.fromString(section.getString("seller")), null,
 *                 ImportedItem.of(section.getItemStack("item")), section.getDouble("price"),
 *                 Instant.ofEpochMilli(section.getLong("created")))));
 *     }
 *     // id(), displayName(), supportedTypes(), checkAvailability()...
 * }
 * }</pre>
 */
public abstract class AbstractYamlImporter implements DataImporter {

    /**
     * A root section and the way its entries are converted.
     *
     * @param type     the data type the entries are imported as.
     * @param rootPath the section whose children are the rows ; {@code ""} for the root of the file.
     * @param mapper   converts one entry.
     */
    public record YamlSource<T extends ImportedRow>(ImportDataType type, String rootPath, SectionMapper<T> mapper) {

        public YamlSource {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(rootPath, "rootPath");
            Objects.requireNonNull(mapper, "mapper");
        }
    }

    /**
     * The file to read. An absent file gives no row.
     */
    protected abstract File file(ImportContext context);

    /**
     * The sections to read, in that order. Only the ones of a requested type are read.
     */
    protected abstract List<YamlSource<?>> sources(ImportContext context);

    @Override
    public void read(ImportContext context, ImportSink sink) throws Exception {
        YamlConfiguration yaml = context.helpers().loadYaml(file(context));

        for (YamlSource<?> source : sources(context)) {
            if (sink.isCancelled()) {
                return;
            }
            if (!context.requestedTypes().contains(source.type())) {
                continue;
            }

            ConfigurationSection root = source.rootPath().isEmpty() ? yaml : yaml.getConfigurationSection(source.rootPath());
            if (root == null) {
                continue;
            }

            for (String key : root.getKeys(false)) {
                if (sink.isCancelled()) {
                    return;
                }
                ConfigurationSection entry = root.getConfigurationSection(key);
                if (entry == null) {
                    sink.skip(key, "Not a section");
                    continue;
                }

                try {
                    ImportSinks.emit(sink, source.type(), source.mapper().map(key, entry, context));
                } catch (ImportSkipException skip) {
                    sink.skip(skip.getSourceId(), skip.getMessage());
                } catch (Exception e) {
                    sink.skip(key, ImportSinks.describe(e));
                }
            }
        }
    }
}
