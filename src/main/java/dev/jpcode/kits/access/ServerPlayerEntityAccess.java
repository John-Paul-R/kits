package dev.jpcode.kits.access;

import dev.jpcode.kits.data.IPlayerKitData;

public interface ServerPlayerEntityAccess {

    IPlayerKitData kits$getPlayerData();

    void kits$setPlayerData(IPlayerKitData playerData);

}
