package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.objects.Historic;
import fr.florianpal.fauction.objects.Sort;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ListUtil {

    public static List<Auction> historicToAuction(List<Historic> historics) {
        return new ArrayList<>(historics);
    }

    public static List<Auction> applySorting(List<Auction> auctions, Sort sort) {
        switch (sort.getType()) {
            case DATE_NEWER_TO_OLDER -> {
                return auctions.stream().sorted(Comparator.comparing(Auction::getDate)).collect(Collectors.toList());
            }
            case DATE_OLDER_TO_NEWER -> {
                return auctions.stream().sorted(Comparator.comparing(Auction::getDate)).collect(Collectors.toList()).reversed();
            }
            case PRICE_HIGHER_TO_LOWER -> {
                return auctions.stream().sorted(Comparator.comparing(Auction::getPrice)).collect(Collectors.toList()).reversed();
            }
            case PRICE_LOWER_TO_HIGHER -> {
                return auctions.stream().sorted(Comparator.comparing(Auction::getPrice)).collect(Collectors.toList());
            }
        }
        return auctions;
    }

    public static List<Auction> getAuctionByCategory(List<Auction> auctions, Category category) {

        if (category.containsAll()) {
            return auctions;
        }

        return auctions.stream().filter(auction -> {
            ItemStack item = auction.getItemStack();
            Material type = item.getType();

            if (category.containsEnchanted() && !item.getEnchantments().isEmpty()) {
                return true;
            }

            if (category.containsWeapons() && isWeapon(type)) {
                return true;
            }

            if (category.containsTools() && isTool(type)) {
                return true;
            }

            if (category.containsArmor() && isArmor(type)) {
                return true;
            }

            if (category.containsBlocks() && isBlock(type)) {
                return true;
            }

            if (category.containsFood() && (isFood(type) || isPotion(type))) {
                return true;
            }

            if (category.containsPotions() && isPotion(type)) {
                return true;
            }

            if (category.containsMisc() && isMisc(type)) {
                return true;
            }

            if (category.containsCustom() && hasCustomData(item)) {
                return true;
            }

            return category.getMaterials().contains(type);
        }).collect(Collectors.toList());
    }

    private static boolean isWeapon(Material type) {
        return type.name().endsWith("_SWORD") || type == Material.BOW || type == Material.CROSSBOW ||
                type == Material.TRIDENT || type.name().endsWith("_AXE");
    }

    private static boolean isTool(Material type) {
        return type.name().endsWith("_PICKAXE") || type.name().endsWith("_AXE") ||
                type.name().endsWith("_SHOVEL") || type.name().endsWith("_HOE") ||
                type == Material.SHEARS || type == Material.FLINT_AND_STEEL || type == Material.FISHING_ROD;
    }

    private static boolean isArmor(Material type) {
        return type.name().endsWith("_HELMET") || type.name().endsWith("_CHESTPLATE") ||
                type.name().endsWith("_LEGGINGS") || type.name().endsWith("_BOOTS") ||
                type == Material.ELYTRA || type == Material.SHIELD;
    }

    private static boolean isBlock(Material type) {
        return type.isBlock();
    }

    private static boolean isFood(Material type) {
        return type.isEdible();
    }

    private static boolean isPotion(Material type) {
        return type == Material.POTION || type == Material.SPLASH_POTION ||
                type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW;
    }

    private static boolean isMisc(Material type) {
        return !isWeapon(type) && !isTool(type) && !isArmor(type) &&
                !isBlock(type) && !isFood(type) && !isPotion(type);
    }

    private static boolean hasCustomData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return !container.isEmpty();
    }
}
