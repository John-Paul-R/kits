package dev.jpcode.kits.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.data.IPlayerKitData;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin implements ServerPlayerEntityAccess {

    private IPlayerKitData kits$playerData;

    @Override
    public IPlayerKitData kits$getPlayerData() {
        return this.kits$playerData;
    }

    @Override
    public void kits$setPlayerData(IPlayerKitData playerData) {
        this.kits$playerData = playerData;
    }
}
