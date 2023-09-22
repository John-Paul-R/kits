package dev.jpcode.kits.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.data.PlayerKitUsageData;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ServerPlayerEntityAccess {

    private PlayerKitUsageData kits$playerData;

    @Override
    public PlayerKitUsageData kits$getPlayerData() {
        return this.kits$playerData;
    }

    @Override
    public void kits$setPlayerData(PlayerKitUsageData playerData) {
        this.kits$playerData = playerData;
    }
}
