package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.objects.Sort;

import java.util.LinkedHashMap;
import java.util.Map;

public class SortConfig {

    private LinkedHashMap<String, Sort> sort;

    public void load(YamlDocument config) {

        sort = new LinkedHashMap<>();
        for (Object id : config.getSection("sort").getKeys()) {
            String displayName = config.getString("sort." + id + ".displayName");
            String type = config.getString("sort." + id + ".type");

            sort.put(id.toString(), new Sort(id.toString(), displayName, type));
        }
    }

    public Map<String, Sort> getSort() {
        return sort;
    }

    public Sort getDefault() {
        return sort.getOrDefault("DEFAULT", null);
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
