package dev.jpcode.kits;

import java.io.File;
import java.nio.file.Path;

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
            storage.init(s.registryAccess());
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

    public static void setStarterKit(String s) {
        storage.setStarterKit(s);
    }

}
