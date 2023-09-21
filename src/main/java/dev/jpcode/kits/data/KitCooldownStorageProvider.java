package dev.jpcode.kits.data;

import net.minecraft.server.network.ServerPlayerEntity;

public interface KitCooldownStorageProvider {
    void useKit(String kitName);

    long getKitUsedTime(String kitName);

    void resetKitCooldown(String kitName);

    void resetAllKits();

    void setPlayer(ServerPlayerEntity player);
}
