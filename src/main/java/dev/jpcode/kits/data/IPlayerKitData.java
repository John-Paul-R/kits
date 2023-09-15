package dev.jpcode.kits.data;

import net.minecraft.server.network.ServerPlayerEntity;

public interface IPlayerKitData {
    void useKit(String kitName);

    long getKitUsedTime(String kitName);

    boolean hasReceivedStarterKit();

    void setHasReceivedStarterKit(boolean hasReceivedStarterKit);

    void resetKitCooldown(String kitName);

    void resetAllKits();

    void setPlayer(ServerPlayerEntity player);
}
