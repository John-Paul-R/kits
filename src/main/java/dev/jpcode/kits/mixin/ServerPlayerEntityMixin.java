package dev.jpcode.kits.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.PlayerKitData;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

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
