package dev.jpcode.kits;

import java.util.LinkedHashMap;
import java.util.UUID;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.events.PlayerConnectCallback;
import dev.jpcode.kits.events.PlayerLeaveCallback;
import dev.jpcode.kits.events.PlayerRespawnCallback;

public class PlayerDataManager {

    private final LinkedHashMap<UUID, PlayerKitData> dataMap;
    private static PlayerDataManager instance;

    public PlayerDataManager() {
        instance = this;
        this.dataMap = new LinkedHashMap<>();
    }

    public static PlayerDataManager getInstance() {
        return instance;
    }

    static {
        PlayerConnectCallback.EVENT.register(PlayerDataManager::onPlayerConnect);
        PlayerLeaveCallback.EVENT.register(PlayerDataManager::onPlayerLeave);
        PlayerRespawnCallback.EVENT.register(PlayerDataManager::onPlayerRespawn);
    }

    public static void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player) {
        PlayerKitData playerData = instance.addPlayer(player);
        ((ServerPlayerEntityAccess) player).kits$setPlayerData(playerData);
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        // Auto-saving should be handled by WorldSaveHandlerMixin. (PlayerData saves when MC server saves players)
        instance.unloadPlayerData(player);
    }

    private static void onPlayerRespawn(ServerPlayerEntity oldPlayerEntity, ServerPlayerEntity newPlayerEntity) {
        PlayerKitData pData = ((ServerPlayerEntityAccess) oldPlayerEntity).kits$getPlayerData();
        pData.setPlayer(newPlayerEntity);
        ((ServerPlayerEntityAccess) newPlayerEntity).kits$setPlayerData(pData);
    }

    public PlayerKitData addPlayer(ServerPlayerEntity player) {
        PlayerKitData playerData = PlayerKitDataFactory.create(player);
        dataMap.put(player.getUuid(), playerData);
        return playerData;
    }

    public PlayerData getPlayerData(ServerPlayerEntity player) {
        PlayerData playerData = dataMap.get(player.getUuid());

        if (playerData == null) {
            throw new NullPointerException(String.format("dataMap returned null for player with uuid %s", player.getUuid().toString()));
        }
        return playerData;
    }

    private void unloadPlayerData(ServerPlayerEntity player) {
        this.dataMap.remove(player.getUuid());
    }

}
