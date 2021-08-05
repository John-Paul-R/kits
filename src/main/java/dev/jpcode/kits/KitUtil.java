package dev.jpcode.kits;

import net.minecraft.server.network.ServerPlayerEntity;

public final class KitUtil {

    private KitUtil() {}

    public static void giveKit(ServerPlayerEntity player, Kit kit) {
        InventoryUtil.offerAllCopies(kit.inventory(), player.getInventory());
    }
}
