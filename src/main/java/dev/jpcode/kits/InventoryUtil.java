package dev.jpcode.kits;

import net.minecraft.world.entity.player.Inventory;

public final class InventoryUtil {

    private InventoryUtil() {}

    public static void offerAllCopies(KitInventory source, Inventory target) {
        for (int i = 0; i < source.getContainerSize(); ++i) {
            target.placeItemBackInInventory(source.getItem(i).copy());
        }
    }
}
