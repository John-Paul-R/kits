package dev.jpcode.kits.util;

import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.Kit;

public final class KitUtil {

    private KitUtil() {}

    public static void giveKit(ServerPlayerEntity player, Kit kit) {
        InventoryUtil.offerAllCopies(kit.inventory(), player.getInventory());
    }
}
