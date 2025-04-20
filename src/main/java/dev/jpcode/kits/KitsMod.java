package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

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
    public static final Map<String, Kit> KIT_MAP = new HashMap<>();
    public static final Map<String, KitRing> KIT_RING_MAP = new HashMap<>();
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
        kitsDir = ensureKitsDir(server);
        userDataDir = server.getSavePath(WorldSavePath.ROOT).resolve("kits_user_data");

        // if the dir was not just created, load all kits from dir.
        if (!kitsDir.mkdirs()) {
            File[] kitFiles = kitsDir.listFiles();
            if (kitFiles == null) {
                throw new IllegalStateException(
                    String.format("Failed to list files in the kits directory ('%s')", kitsDir.getPath()));
            }
            for (File kitFile : kitFiles) {
                if (kitFile.getPath().endsWith(".ring.nbt")) {
                    try {
                        LOGGER.info("Loading kit ring '{}'", kitFile.getName());
                        NbtCompound kitRingNbt = NbtIo.read(kitFile.toPath());
                        String fileName = kitFile.getName();
                        String kitName = fileName.substring(0, fileName.length() - ".ring.nbt".length());
                        KIT_RING_MAP.put(kitName, KitRing.fromNbt(kitRingNbt, server.getOverworld()));
                    } catch (IOException | NullPointerException | NbtCrashException e) {
                        LOGGER.error("Error while loading kit ring '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
                    continue;
                }
                try {
                    LOGGER.info("Loading kit '{}'", kitFile.getName());
                    NbtCompound kitNbt = NbtIo.read(kitFile.toPath());
                    String fileName = kitFile.getName();
                    String kitName = fileName.substring(0, fileName.length() - 4);
                    KIT_MAP.put(kitName, Kit.fromNbt(kitNbt));
                } catch (IOException | NullPointerException | NbtCrashException e) {
                    LOGGER.error("Error while loading kit '{}'", kitFile.getPath());
                    e.printStackTrace();
                }
            }
        }
        CONFIG.loadOrCreateProperties();
    }

    private static File ensureKitsDir(MinecraftServer server)
    {
        var buggedKitsDir = server.getRunDirectory().getFileName().resolve("config/kits").toFile();
        var correctKitsDir = server.getRunDirectory().resolve("config/kits").toFile();
        boolean buggedKitsDirExists = buggedKitsDir.exists();
        boolean correctKitsDirExists = correctKitsDir.exists();
        // Handle an old path resolution bug by keeping the bugged one if it exists
        if (buggedKitsDirExists && correctKitsDirExists) {
            LOGGER.warn(
                "Due to an old bug in Kits, you have an extra kits folder at '{}'."
                    + " Move those kits files to '{}' and remove the old folder to resolve this warning."
                    + " Until the extra is removed, Kits will continue to use it ('{}').",
                buggedKitsDir.toPath().toAbsolutePath(),
                correctKitsDir.toPath().toAbsolutePath(),
                buggedKitsDir.toPath().toAbsolutePath()
            );
            return buggedKitsDir;
        }

        // only the bugged dir exists; move it to the correct location
        if (buggedKitsDirExists) {
            try {
                // ensure 'config' dir exists
                Files.move(
                    buggedKitsDir.toPath(),
                    correctKitsDir.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.COPY_ATTRIBUTES
                );
                return correctKitsDir;
            } catch (IOException ex) {
                LOGGER.warn("failed to move old Kits dir to new Kits dir. Loading from old.", ex);
                return buggedKitsDir;
            }
        }

        // no special cases, just give em the correct dir!
        return correctKitsDir;
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
