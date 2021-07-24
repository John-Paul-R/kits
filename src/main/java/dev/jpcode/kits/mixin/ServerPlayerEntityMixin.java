package dev.jpcode.kits.mixin;

import dev.jpcode.kits.PlayerKitData;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import net.minecraft.server.network.ServerPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ServerPlayerEntityAccess {

    private PlayerKitData kits$playerData;

    @Override
    public PlayerKitData kits$getPlayerData() {
        return this.kits$playerData;
    }

    @Override
    public void kits$setPlayerData(PlayerKitData playerData) {
        this.kits$playerData = playerData;
    }
}
