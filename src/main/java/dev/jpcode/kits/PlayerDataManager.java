package dev.jpcode.kits;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.events.PlayerConnectCallback;
import dev.jpcode.kits.events.PlayerLeaveCallback;

public class PlayerDataManager {

    private final LinkedHashMap<UUID, PlayerKitData> dataMap;
    private static PlayerDataManager instance;

    public PlayerDataManager() {
        instance = this;
        this.dataMap = new LinkedHashMap<>();
    }

    public static PlayerDataManager getInstance() {
        if (instance == null) {
            instance = new PlayerDataManager();
        }
        return instance;
    }

    static {
        PlayerConnectCallback.EVENT_HEAD.register(PlayerDataManager::onPlayerConnect);
        PlayerConnectCallback.EVENT_RETURN.register(PlayerDataManager::onPlayerConnectTail);
        PlayerLeaveCallback.EVENT.register(PlayerDataManager::onPlayerLeave);
    }

    public static void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player) {
        PlayerKitData playerData = instance.addPlayer(player);
        ((ServerPlayerEntityAccess) player).kits$setPlayerData(playerData);
    }

    private static void onPlayerConnectTail(ClientConnection connection, ServerPlayerEntity player) {
        PlayerKitData playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        // Detect if player has gotten starter kit
        if (!playerData.hasReceivedStarterKit()) {
            Kit starterKit = KitsMod.getStarterKit();
            if (starterKit != null) {
                KitUtil.giveKit(player, starterKit);
                if (!starterKit.commands().isEmpty())
                    KitUtil.runCommands(player, starterKit.commands());
                playerData.setHasReceivedStarterKit(true);
                if (KitsMod.CONFIG.starterKitSetCooldown.getValue())
                    playerData.useKit(KitsMod.CONFIG.starterKit.getValue());
            }
        }
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        // Auto-saving should be handled by WorldSaveHandlerMixin. (PlayerData saves when MC server saves players)
        instance.unloadPlayerData(player);
        ((ServerPlayerEntityAccess) player).kits$getPlayerData()
            .save(Objects.requireNonNull(player.getServer()).getRegistryManager());
    }

    public static void handlePlayerDataRespawnSync(ServerPlayerEntity oldPlayerEntity, ServerPlayerEntity newPlayerEntity) {
        var oldPlayerAccess = ((ServerPlayerEntityAccess) oldPlayerEntity);
        var newPlayerAccess = ((ServerPlayerEntityAccess) newPlayerEntity);

        PlayerKitData playerData = oldPlayerAccess.kits$getPlayerData();
        playerData.setPlayer(newPlayerEntity);
        newPlayerAccess.kits$setPlayerData(playerData);
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
