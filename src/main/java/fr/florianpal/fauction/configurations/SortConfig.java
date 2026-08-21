package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.objects.Sort;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

public class SortConfig {

    @Getter
    private LinkedHashMap<String, Sort> sort;

    public void load(YamlDocument config) {

        sort = new LinkedHashMap<>();
        for (Object id : config.getSection("sort").getKeys()) {
            String displayName = config.getString("sort." + id + ".displayName");
            String type = config.getString("sort." + id + ".type");

            sort.put(id.toString(), new Sort(id.toString(), displayName, type));
        }
    }

    /**
     * @return the sort configured as "DEFAULT", or the first configured sort if that key was
     * renamed/removed ; only returns null if no sort at all is configured.
     */
    public Sort getDefault() {
        Sort configuredDefault = sort.get("DEFAULT");
        if (configuredDefault != null) {
            return configuredDefault;
        }
        return sort.values().stream().findFirst().orElse(null);
    }

    public Sort getNext(Sort sort) {
        boolean next = false;
        for (Map.Entry<String, Sort> entry : this.sort.entrySet()) {

            if (next) {
                return entry.getValue();
            }

            if (entry.getKey().equals(sort.getId())) {
                next = true;
            }
        }

        return getDefault();
    }
}
