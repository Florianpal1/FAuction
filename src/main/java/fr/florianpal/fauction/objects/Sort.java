package fr.florianpal.fauction.objects;

import fr.florianpal.fauction.enums.SortType;

public class Sort {

    private final String id;

    private final String displayName;

    private final SortType type;

    public Sort(String id, String displayName, String type) {
        this.id = id;
        this.displayName = displayName;
        this.type = SortType.valueOf(type);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SortType getType() {
        return type;
    }
}
