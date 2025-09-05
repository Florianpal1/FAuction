package fr.florianpal.fauction.enums;

public enum BlockType {

    BARRIER("barrier"),

    CONFIRM("confirm"),

    CLOSE("close"),

    MENU("menu"),

    AUCTIONGUI("auctionGui"),

    CATEGORY("category"),

    SORTING("sorting"),

    HISTORICGUI("historicGui"),

    EXPIREGUI("expireGui"),

    PLAYER("player"),

    NEXT("next"),

    COMMAND("command"),

    PREVIOUS("previous");

    private final String text;

    BlockType(String text) {
        this.text = text;
    }

    public boolean equalsIgnoreCase(String current) {

        if (current == null) {
            return false;
        }

        return text.equalsIgnoreCase(current);
    }
}
