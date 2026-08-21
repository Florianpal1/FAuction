package fr.florianpal.fauction.configurations;

import fr.florianpal.fauction.objects.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoriesConfigTest {

    private CategoriesConfig categoriesConfig;

    @BeforeEach
    void setUp() {
        categoriesConfig = new CategoriesConfig();
        categoriesConfig.load(TestConfigs.of("""
                categories:
                  default:
                    displayName: "&aAll"
                    materials:
                      - ALL
                  blocks:
                    displayName: "&aBlocks"
                    materials:
                      - BLOCKS
                  custom:
                    displayName: "&aCustom"
                    materials:
                      - DIAMOND
                      - EMERALD
                """));
    }

    @Test
    @DisplayName("Every category of the file is read, in order")
    void readsEveryCategory() {

        assertEquals(List.of("default", "blocks", "custom"), List.copyOf(categoriesConfig.getCategories().keySet()));

        Category custom = categoriesConfig.getCategories().get("custom");
        assertEquals("&aCustom", custom.getDisplayName());
        assertEquals(List.of("DIAMOND", "EMERALD"), custom.getMaterialsString());
    }

    @Test
    @DisplayName("Clicking the category button walks the file in order, then wraps")
    void nextFollowsTheFileOrderThenWraps() {

        Category first = categoriesConfig.getDefault();
        assertEquals("default", first.getId());

        Category second = categoriesConfig.getNext(first);
        assertEquals("blocks", second.getId());

        Category third = categoriesConfig.getNext(second);
        assertEquals("custom", third.getId());

        assertEquals("default", categoriesConfig.getNext(third).getId());
    }

    @Test
    @DisplayName("A category that is no longer in the file falls back to the default one")
    void unknownCategoryFallsBackToTheDefault() {

        Category removed = new Category("removed", "&aRemoved", List.of("DIAMOND"));

        assertEquals("default", categoriesConfig.getNext(removed).getId());
    }

    @Test
    @DisplayName("Without a default entry, the first configured category is used instead")
    void noDefaultEntryFallsBackToTheFirstCategory() {

        CategoriesConfig withoutDefault = new CategoriesConfig();
        withoutDefault.load(TestConfigs.of("""
                categories:
                  blocks:
                    displayName: "&aBlocks"
                    materials:
                      - BLOCKS
                  custom:
                    displayName: "&aCustom"
                    materials:
                      - DIAMOND
                """));

        // Renaming/removing "default" must not NPE every gui that opens without an explicit category.
        assertEquals("blocks", withoutDefault.getDefault().getId());
    }

    @Test
    @DisplayName("With no category configured at all, there is no default category")
    void noCategoryAtAllMeansNoDefaultCategory() {

        CategoriesConfig empty = new CategoriesConfig();
        empty.load(TestConfigs.of("""
                categories: {}
                """));

        assertNull(empty.getDefault());
    }

    @Test
    @DisplayName("The keywords of a category are recognised")
    void keywordsAreRecognised() {

        Category all = categoriesConfig.getCategories().get("default");
        assertTrue(all.containsAll());
        assertFalse(all.containsBlocks());

        Category blocks = categoriesConfig.getCategories().get("blocks");
        assertTrue(blocks.containsBlocks());
        assertFalse(blocks.containsAll());

        Category custom = categoriesConfig.getCategories().get("custom");
        assertFalse(custom.containsAll());
        assertFalse(custom.containsBlocks());
        assertFalse(custom.containsEnchanted());
        assertFalse(custom.containsWeapons());
        assertFalse(custom.containsTools());
        assertFalse(custom.containsArmor());
        assertFalse(custom.containsFood());
        assertFalse(custom.containsPotions());
        assertFalse(custom.containsMisc());
        assertFalse(custom.containsCustom());
    }
}