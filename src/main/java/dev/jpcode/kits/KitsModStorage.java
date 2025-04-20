package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import dev.jpcode.kits.config.KitsConfig;

public class KitsModStorage {
    private final Logger logger;
    private final KitsConfig config;

    public final Map<String, Kit> KIT_MAP = new HashMap<>();
    public final Map<String, KitRing> KIT_RING_MAP = new HashMap<>();
    private Kit starterKit;

    private File kitsDir;
    private Path userDataDir;

    public KitsModStorage(
        Logger logger,
        KitsConfig config
    ) {
        this.logger = logger;
        this.config = config;
    }

    public File getKitsDir() {
        return kitsDir;
    }

    public Path getUserDataDirDir() {
        return userDataDir;
    }

    public Kit getStarterKit() {
        return starterKit;
    }

    public void setStarterKit(String s) {
        if (s == null || s.isBlank()) {
            starterKit = null;
        } else {
            starterKit = KIT_MAP.get(s);
            if (starterKit == null) {
                logger.warn("Provided starter kit name, '{}' could not be found.", s);
            } else {
                logger.info("Starter kit set to '{}'", s);
            }
        }
    }

    public void reloadKits(MinecraftServer server) {
        KIT_MAP.clear();
        KIT_RING_MAP.clear();
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
                        logger.info("Loading kit ring '{}'", kitFile.getName());
                        NbtCompound kitRingNbt = NbtIo.read(kitFile.toPath());
                        String fileName = kitFile.getName();
                        String kitName = fileName.substring(0, fileName.length() - ".ring.nbt".length());
                        KIT_RING_MAP.put(kitName, KitRing.fromNbt(kitRingNbt, server.getOverworld()));
                    } catch (IOException | NullPointerException | NbtCrashException e) {
                        logger.error("Error while loading kit ring '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
                    continue;
                }
                try {
                    logger.info("Loading kit '{}'", kitFile.getName());
                    NbtCompound kitNbt = NbtIo.read(kitFile.toPath());
                    String fileName = kitFile.getName();
                    String kitName = fileName.substring(0, fileName.length() - 4);
                    KIT_MAP.put(kitName, Kit.fromNbt(kitNbt));
                } catch (IOException | NullPointerException | NbtCrashException e) {
                    logger.error("Error while loading kit '{}'", kitFile.getPath());
                    e.printStackTrace();
                }
            }
        }
    }

    private static File ensureKitsDir(MinecraftServer server)
    {
        var buggedKitsDir = server.getRunDirectory().getFileName().resolve("config/kits").toFile();
        var correctKitsDir = server.getRunDirectory().resolve("config/kits").toFile();
        boolean buggedKitsDirExists = buggedKitsDir.exists();
        boolean correctKitsDirExists = correctKitsDir.exists();
        // Handle an old path resolution bug by keeping the bugged one if it exists
        if (buggedKitsDirExists && correctKitsDirExists) {
            KitsMod.LOGGER.warn(
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
                KitsMod.LOGGER.warn("failed to move old Kits dir to new Kits dir. Loading from old.", ex);
                return buggedKitsDir;
            }
        }

        // no special cases, just give em the correct dir!
        return correctKitsDir;
    }
}
