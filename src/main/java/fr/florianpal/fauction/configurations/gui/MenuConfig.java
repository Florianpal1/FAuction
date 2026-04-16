package fr.florianpal.fauction.configurations.gui;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import fr.florianpal.fauction.FAuction;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class MenuConfig {

    private final Map<String, CustomMenuConfig> menus = new HashMap<>();

    public void load(FAuction plugin) {

        try (Stream<Path> paths = Files.walk(Paths.get(plugin.getDataFolder().getPath() + "/gui/menus"))) {
            List<Path> pathsFiltered = paths.filter(Files::isRegularFile).collect(Collectors.toList());

            for (Path path : pathsFiltered) {

                YamlDocument configuration = YamlDocument.create(path.toFile(),
                        Objects.requireNonNull(getClass().getResourceAsStream("/gui/menus/" + path.getFileName().toString())),
                        GeneralSettings.DEFAULT,
                        LoaderSettings.builder().setAutoUpdate(true).build(),
                        DumperSettings.DEFAULT,
                        UpdaterSettings.builder().setVersioning(new BasicVersioning("version")).setOptionSorting(UpdaterSettings.DEFAULT_OPTION_SORTING).build()
                );

                CustomMenuConfig config = new CustomMenuConfig();
                config.load(plugin, configuration);
                menus.put(configuration.getString("id"), config);
            }
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

}
