package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.objects.Category;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoriesConfig {

    @Getter
    private LinkedHashMap<String, Category> categories;

    public void load(YamlDocument config) {

        categories = new LinkedHashMap<>();
        for (Object id : config.getSection("categories").getKeys()) {
            String displayName = config.getString("categories." + id + ".displayName");
            List<String> materials = config.getStringList("categories." + id + ".materials");

            categories.put(id.toString(), new Category(id.toString(), displayName, materials));
        }
    }

    public Category getDefault() {
        return categories.getOrDefault("default", null);
    }

    public Category getNext(Category category) {
        boolean next = false;
        for (Map.Entry<String, Category> entry : categories.entrySet()) {

            if (next) {
                return entry.getValue();
            }

            if (entry.getKey().equals(category.getId())) {
                next = true;
            }
        }

        return getDefault();
    }
}
