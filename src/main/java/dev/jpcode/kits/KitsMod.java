package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.WorldSavePath;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import dev.jpcode.kits.config.KitsConfig;

public class KitsMod implements ModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger("kits");
    public static final KitsConfig CONFIG = new KitsConfig(
        Path.of("./config/kits.properties"),
        "Kits Config",
        "https://github.com/John-Paul-R/kits/wiki/Basic-Usage"
    );
    public static final Map<String, Kit> KIT_MAP = new HashMap<String, Kit>();
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
    public void onInitialize()
    {
        LOGGER.info("Kits is getting ready...");

        KitPerms.init();

        PlayerDataManager playerDataManager = new PlayerDataManager();

        ServerLifecycleEvents.SERVER_STARTING.register(KitsMod::reloadKits);

        CommandRegistrationCallback.EVENT.register(KitsCommandRegistry::register);

        LOGGER.info("Kits initialized.");
    }

    public static void reloadKits(MinecraftServer server) {
        KIT_MAP.clear();
        kitsDir = server.getRunDirectory().toPath().resolve("config/kits").toFile();
        userDataDir = server.getSavePath(WorldSavePath.ROOT).resolve("kits_user_data");

        // if the dir was not just created, load all kits from dir.
        if (!kitsDir.mkdir()) {
            File[] kitFiles = kitsDir.listFiles();
            for (File kitFile : kitFiles) {
                try {
                    LOGGER.info(String.format("Loading kit '%s'", kitFile.getName()));
                    NbtCompound kitNbt = NbtIo.read(kitFile);
                    PlayerInventory kitInventory = new PlayerInventory(null);

                    assert kitNbt != null;
                    kitInventory.readNbt(kitNbt.getList("inventory", NbtElement.COMPOUND_TYPE));
                    long cooldown = kitNbt.getLong("cooldown");
                    String fileName = kitFile.getName();
                    String kitName = fileName.substring(0, fileName.length() - 4);
                    KIT_MAP.put(kitName, new Kit(kitInventory, cooldown));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }
        CONFIG.loadOrCreateProperties();
    }

    /**
     * Suggests existing kits that the user has permissions for.
     *
     * @param context
     * @param builder
     * @return suggestions for existing kits that the user has permissions for.
     */
    public static CompletableFuture<Suggestions> suggestionProvider(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        ServerCommandSource source = context.getSource();
        return ListSuggestion.getSuggestionsBuilder(builder, KIT_MAP.keySet().stream().filter(kitName ->
            KitPerms.checkKit(source, kitName)
        ).toList());
    }

    public static void setStarterKit(String s) {
        if (s == null) {
            starterKit = null;
        } else {
            starterKit = KIT_MAP.get(s);
            if (starterKit == null) {
                LOGGER.warn(String.format("Provided starter kit name, '%s' could not be found.", s));
            } else {
                LOGGER.info(String.format("Starter kit set to '%s'", s));
            }
        }
    }

}
