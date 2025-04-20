package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.util.Util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.server.MinecraftServer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import dev.jpcode.kits.config.KitsConfig;

public class KitsMod implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("kits");
    public static final KitsConfig CONFIG = new KitsConfig(
        Path.of("./config/kits.properties"),
        "Kits Config",
        "https://github.com/John-Paul-R/kits/wiki/Basic-Usage"
    );
    private static final KitsModStorage storage = new KitsModStorage(LOGGER, CONFIG);
    public static final SimpleCommandExceptionType COMMAND_EXCEPTION_TYPE = new SimpleCommandExceptionType(new LiteralMessage("Kits exception"));

    public static File getKitsDir() {
        return storage.getKitsDir();
    }

    public static Path getUserDataDirDir() {
        return storage.getUserDataDirDir();
    }

    public static Kit getStarterKit() {
        return storage.getStarterKit();
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Kits is getting ready...");

        KitPerms.init();

        // static ctor to register event handlers
        PlayerDataManager.getInstance();

        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            storage.init(s.getRegistryManager());
        });

        ServerLifecycleEvents.SERVER_STARTED.register(storage::reloadKits);

        var commandRegistry = new KitsCommandRegistry(
            LOGGER,
            CONFIG,
            storage
        );
        CommandRegistrationCallback.EVENT.register(commandRegistry::register);

        LOGGER.info("Kits initialized.");
    }

    public static void reload(MinecraftServer server) {
        storage.reloadKits(server);
        CONFIG.loadOrCreateProperties();
    }

    public static Stream<Map.Entry<String, Kit>> getAllKitsForPlayer(ServerPlayerEntity player) {
        var source = player.getCommandSource();
        return KIT_MAP.entrySet()
            .stream()
            .filter(kitEntry ->
                KitPerms.checkKit(source, kitEntry.getKey())
            );
    }

    public static Stream<Map.Entry<String, Kit>> getClaimableKitsForPlayer(ServerPlayerEntity player) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        long currentTime = Util.getEpochTimeMs();

        return getAllKitsForPlayer(player)
            .filter(entry -> playerData.isKitOnCooldownAtTime(entry, currentTime));
    }

    /**
     * Suggests existing kits that the user has permissions for.
     *
     * @param context server command context w/ player
     * @param builder suggestions builder
     * @return suggestions for existing kits that the user has permissions for.
     */
    public static CompletableFuture<Suggestions> suggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllKitsForPlayer(context.getSource().getPlayer())
                .map(Map.Entry::getKey)
                .toList()
        );
    }

    public static void setStarterKit(String s) {
        storage.setStarterKit(s);
    }

}
