package dev.jpcode.kits.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;

import dev.jpcode.kits.PlayerDataManager;
import dev.jpcode.kits.events.PlayerConnectCallback;
import dev.jpcode.kits.events.PlayerLeaveCallback;
import dev.jpcode.kits.events.PlayerRespawnCallback;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    public void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
        PlayerConnectCallback.EVENT_HEAD.invoker().onPlayerConnect(connection, player);
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    public void onPlayerConnectTail(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
        PlayerConnectCallback.EVENT_RETURN.invoker().onPlayerConnect(connection, player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    public void onPlayerLeave(ServerPlayer player, CallbackInfo callbackInfo) {
        PlayerLeaveCallback.EVENT.invoker().onPlayerLeave(player);
    }

    @SuppressWarnings("checkstyle:NoWhitespaceBefore")
    @Inject(
        method = "respawn",
        at = @At(
            value = "INVOKE",
            // This target is near-immediately after the new ServerPlayerEntity is
            // created. This lets us update the EC PlayerData, sooner, might be
            // before the new ServerPlayerEntity is fully initialized.
            target = "Lnet/minecraft/server/level/ServerPlayer;restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V"
        )
    )
    public void onRespawnPlayerEarly(ServerPlayer oldServerPlayerEntity,
                                boolean alive,
                                Entity.RemovalReason removalReason,
                                CallbackInfoReturnable<ServerPlayer> cir,
                                     @Local(name = "player") ServerPlayer serverPlayerEntity) {
        PlayerDataManager.handlePlayerDataRespawnSync(oldServerPlayerEntity, serverPlayerEntity);
    }

    @Inject(method = "respawn", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getLevelData()Lnet/minecraft/world/level/storage/LevelData;"
    ))
    public void onRespawnPlayer(ServerPlayer oldServerPlayerEntity,
                                boolean alive,
                                Entity.RemovalReason removalReason,
                                CallbackInfoReturnable<ServerPlayer> cir,
                                @Local(name = "player") ServerPlayer serverPlayerEntity) {
        PlayerRespawnCallback.EVENT.invoker().onPlayerRespawn(oldServerPlayerEntity, serverPlayerEntity);
    }
}
