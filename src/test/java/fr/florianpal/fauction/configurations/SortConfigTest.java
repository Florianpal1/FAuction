package fr.florianpal.fauction.configurations;

import fr.florianpal.fauction.enums.SortType;
import fr.florianpal.fauction.objects.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SortConfigTest {

    private SortConfig sortConfig;

    @BeforeEach
    void setUp() {
        sortConfig = new SortConfig();
        sortConfig.load(TestConfigs.of("""
                sort:
                  DEFAULT:
                    displayName: "By date (Newer to Older)"
                    type: DATE_NEWER_TO_OLDER
                  PRICE_LOWER_TO_HIGHER:
                    displayName: "By price (Lower to Higher)"
                    type: PRICE_LOWER_TO_HIGHER
                  PRICE_HIGHER_TO_LOWER:
                    displayName: "By price (Higher to Lower)"
                    type: PRICE_HIGHER_TO_LOWER
                """));
    }

    @Test
    @DisplayName("Every sort of the file is read, in order")
    void readsEverySort() {

        assertEquals(List.of("DEFAULT", "PRICE_LOWER_TO_HIGHER", "PRICE_HIGHER_TO_LOWER"), List.copyOf(sortConfig.getSort().keySet()));

        Sort priceAsc = sortConfig.getSort().get("PRICE_LOWER_TO_HIGHER");
        assertEquals("PRICE_LOWER_TO_HIGHER", priceAsc.getId());
        assertEquals("By price (Lower to Higher)", priceAsc.getDisplayName());
        assertEquals(SortType.PRICE_LOWER_TO_HIGHER, priceAsc.getType());
    }

    @Test
    @DisplayName("The default sort is the DEFAULT entry")
    void defaultSortIsTheDefaultEntry() {
        assertEquals("DEFAULT", sortConfig.getDefault().getId());
    }

    @Test
    @DisplayName("Clicking the sort button walks the file in order")
    void nextFollowsTheFileOrder() {

        Sort first = sortConfig.getDefault();

        Sort second = sortConfig.getNext(first);
        assertEquals("PRICE_LOWER_TO_HIGHER", second.getId());

        Sort third = sortConfig.getNext(second);
        assertEquals("PRICE_HIGHER_TO_LOWER", third.getId());
    }

    @Test
    @DisplayName("The last sort goes back to the default one")
    void lastSortWrapsToTheDefault() {

        Sort last = sortConfig.getSort().get("PRICE_HIGHER_TO_LOWER");

        assertEquals("DEFAULT", sortConfig.getNext(last).getId());
    }

    @Test
    @DisplayName("A sort that is no longer in the file falls back to the default one")
    void unknownSortFallsBackToTheDefault() {

        Sort removed = new Sort("REMOVED", "Removed", "DATE_NEWER_TO_OLDER");

        assertEquals("DEFAULT", sortConfig.getNext(removed).getId());
    }

    @Test
    @DisplayName("Without a DEFAULT entry, the first configured sort is used instead")
    void noDefaultEntryFallsBackToTheFirstSort() {

        SortConfig withoutDefault = new SortConfig();
        withoutDefault.load(TestConfigs.of("""
                sort:
                  PRICE_LOWER_TO_HIGHER:
                    displayName: "By price (Lower to Higher)"
                    type: PRICE_LOWER_TO_HIGHER
                  PRICE_HIGHER_TO_LOWER:
                    displayName: "By price (Higher to Lower)"
                    type: PRICE_HIGHER_TO_LOWER
                """));

        // Renaming/removing "DEFAULT" must not NPE every gui that opens without an explicit sort.
        assertEquals("PRICE_LOWER_TO_HIGHER", withoutDefault.getDefault().getId());
        assertEquals("PRICE_LOWER_TO_HIGHER", withoutDefault.getNext(withoutDefault.getSort().get("PRICE_HIGHER_TO_LOWER")).getId());
    }

    @Test
    @DisplayName("With no sort configured at all, there is no default sort")
    void noSortAtAllMeansNoDefaultSort() {

        SortConfig empty = new SortConfig();
        empty.load(TestConfigs.of("""
                sort: {}
                """));

        assertNull(empty.getDefault());
    }
}