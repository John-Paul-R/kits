package dev.jpcode.kits.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import dev.jpcode.kits.PlayerDataManager;
import dev.jpcode.kits.events.PlayerConnectCallback;
import dev.jpcode.kits.events.PlayerLeaveCallback;
import dev.jpcode.kits.events.PlayerRespawnCallback;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    public void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, CallbackInfo callbackInfo) {
        PlayerConnectCallback.EVENT_HEAD.invoker().onPlayerConnect(connection, player);
    }

    @Inject(method = "onPlayerConnect", at = @At("RETURN"))
    public void onPlayerConnectTail(ClientConnection connection, ServerPlayerEntity player, CallbackInfo callbackInfo) {
        PlayerConnectCallback.EVENT_RETURN.invoker().onPlayerConnect(connection, player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    public void onPlayerLeave(ServerPlayerEntity player, CallbackInfo callbackInfo) {
        PlayerLeaveCallback.EVENT.invoker().onPlayerLeave(player);
    }

    @SuppressWarnings("checkstyle:NoWhitespaceBefore")
    @Inject(
        method = "respawnPlayer",
        at = @At(
            value = "INVOKE",
            // This target is near-immediately after the new ServerPlayerEntity is
            // created. This lets us update the EC PlayerData, sooner, might be
            // before the new ServerPlayerEntity is fully initialized.
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;copyFrom(Lnet/minecraft/server/network/ServerPlayerEntity;Z)V"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD)
    public void onRespawnPlayer(
        ServerPlayerEntity oldServerPlayerEntity
        , boolean alive
        , CallbackInfoReturnable<ServerPlayerEntity> cir
        , BlockPos blockPos
        , float f
        , boolean bl
        , ServerWorld serverWorld
        , Optional optional
        , ServerWorld serverWorld2
        , ServerPlayerEntity serverPlayerEntity
    ) {
        PlayerDataManager.handlePlayerDataRespawnSync(oldServerPlayerEntity, serverPlayerEntity);
    }

    @Inject(method = "respawnPlayer", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;getLevelProperties()Lnet/minecraft/world/WorldProperties;"
        ), locals = LocalCapture.CAPTURE_FAILHARD)
    public void onRespawnPlayer(ServerPlayerEntity oldServerPlayerEntity, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir,
                                BlockPos blockPos,
                                float f,
                                boolean bl,
                                ServerWorld serverWorld,
                                Optional optional2,
                                ServerWorld serverWorld2,
                                ServerPlayerEntity serverPlayerEntity,
                                boolean bl2
    ) {
        PlayerRespawnCallback.EVENT.invoker().onPlayerRespawn(oldServerPlayerEntity, serverPlayerEntity);
    }
}
