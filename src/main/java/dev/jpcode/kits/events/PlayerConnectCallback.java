package dev.jpcode.kits.events;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface PlayerConnectCallback {
    Event<PlayerConnectCallback> EVENT_HEAD = EventFactory.createArrayBacked(PlayerConnectCallback.class,
        (listeners) -> (player) -> {
            for (PlayerConnectCallback event : listeners) {
                event.onPlayerConnect(player);
            }
        });

    void onPlayerConnect(ServerPlayer player);
}
