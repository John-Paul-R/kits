package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.config.KitsConfig;

public class KitsMod implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("kits");
    public static final KitsConfig CONFIG = new KitsConfig(
        Path.of("./config/kits.properties"),
        "Kits Config",
        "https://github.com/John-Paul-R/kits/wiki/Basic-Usage"
    );
    public static final Map<String, Kit> KIT_MAP = new HashMap<>();
    public static final SimpleCommandExceptionType COMMAND_EXCEPTION_TYPE = new SimpleCommandExceptionType(new LiteralMessage("Kits exception"));
    private static File kitsDir;
    private static Path userDataDir;

    private static Kit starterKit;

    public static File getKitsDir() {
        return kitsDir;
    }

    public static Path getUserDataDirDir() {
        return userDataDir;
    }

    public static Kit getStarterKit() {
        return starterKit;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Kits is getting ready...");

        KitPerms.init();

        // static ctor to register event handlers
        PlayerDataManager.getInstance();

        ServerLifecycleEvents.SERVER_STARTED.register(KitsMod::reloadKits);

        CommandRegistrationCallback.EVENT.register(KitsCommandRegistry::register);

        LOGGER.info("Kits initialized.");
    }

    public static void reloadKits(MinecraftServer server) {
        KIT_MAP.clear();
        kitsDir = server.getRunDirectory().getFileName().resolve("config/kits").toFile();
        userDataDir = server.getSavePath(WorldSavePath.ROOT).resolve("kits_user_data");

        // if the dir was not just created, load all kits from dir.
        if (!kitsDir.mkdirs()) {
            File[] kitFiles = kitsDir.listFiles();
            if (kitFiles == null) {
                throw new IllegalStateException(
                    String.format("Failed to list files in the kits directory ('%s')", kitsDir.getPath()));
            }
            for (File kitFile : kitFiles) {
                try {
                    LOGGER.info("Loading kit '{}'", kitFile.getName());
                    NbtCompound kitNbt = NbtIo.read(kitFile.toPath());
                    String fileName = kitFile.getName();
                    String kitName = fileName.substring(0, fileName.length() - 4);
                    KIT_MAP.put(kitName, Kit.fromNbt(kitNbt, server.getOverworld()));
                } catch (IOException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }
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
            .filter(entry -> {
                long remainingTime = (playerData.getKitUsedTime(entry.getKey()) + entry.getValue().cooldown()) - currentTime;
                return remainingTime <= 0;
            });
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
        if (s == null || s.isBlank()) {
            starterKit = null;
        } else {
            starterKit = KIT_MAP.get(s);
            if (starterKit == null) {
                LOGGER.warn("Provided starter kit name, '{}' could not be found.", s);
            } else {
                LOGGER.info("Starter kit set to '{}'", s);
            }
        }
    }

}
