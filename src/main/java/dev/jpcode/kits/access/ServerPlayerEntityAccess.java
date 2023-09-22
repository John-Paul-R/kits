package dev.jpcode.kits.access;

import dev.jpcode.kits.data.PlayerKitUsageData;

public interface ServerPlayerEntityAccess {

    PlayerKitUsageData kits$getPlayerData();

    void kits$setPlayerData(PlayerKitUsageData playerData);

}
