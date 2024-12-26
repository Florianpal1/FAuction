package fr.florianpal.fauction.enums;

public enum BlockType {

    BARRIER("barrier"),

    CONFIRM("confirm"),

    CLOSE("close"),

    AUCTIONGUI("auctionGui"),

    CATEGORY("category"),

    HISTORICGUI("historicGui"),

    EXPIREGUI("expireGui"),

    PLAYER("player"),

    NEXT("next"),

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