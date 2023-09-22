package dev.jpcode.kits.data;

import net.minecraft.server.network.ServerPlayerEntity;

public interface StarterKitStorageProvider {
    boolean hasReceivedStarterKit();

    void setHasReceivedStarterKit(boolean hasReceivedStarterKit);

    void setPlayer(ServerPlayerEntity player);
}
