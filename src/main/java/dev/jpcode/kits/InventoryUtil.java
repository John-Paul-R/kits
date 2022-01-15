package dev.jpcode.kits;

import net.minecraft.entity.player.PlayerInventory;

public final class InventoryUtil {

    private InventoryUtil() {}

    public static void offerAllCopies(KitInventory source, PlayerInventory target) {
        for (int i = 0; i < source.size(); ++i) {
            target.offerOrDrop(source.getStack(i).copy());
        }
    }
}
