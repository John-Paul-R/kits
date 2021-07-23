package dev.jpcode.kits;

import net.minecraft.entity.player.PlayerInventory;

public final class InventoryUtil {

    private InventoryUtil() {}

    public static void addAllCopies(PlayerInventory source, PlayerInventory target) {
        for (int i = 0; i < source.size(); ++i) {
            target.insertStack(i, source.getStack(i).copy());
        }
    }

    public static void offerAllCopies(PlayerInventory source, PlayerInventory target) {
        for (int i = 0; i < source.size(); ++i) {
            target.offerOrDrop(source.getStack(i).copy());
        }
    }
}
