package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import dev.jpcode.kits.config.KitsConfig;

public class KitsModStorage {
    private final Logger logger;
    private final KitsConfig config;
    private RegistryWrapper.WrapperLookup registries;

    public final Map<String, Kit> KIT_MAP = new HashMap<>();
    public final Map<String, KitRing> KIT_RING_MAP = new HashMap<>();

    /**
     * all kits, regardless of whether they're in a ring or not
     */
    private final Map<String, KitRecord> ALL_KITS_MAP = new HashMap<>();
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

    public record KitRecord(@Nullable String ringName, @Nullable KitRing ring, String kitName, Kit kit) {
        public static KitRecord standaloneKit(String kitName, Kit kit) {
            return new KitRecord(null, null, kitName, kit);
        }

        public static KitRecord ringKit(String ringName, KitRing ring, String kitName, Kit kit) {
            return new KitRecord(ringName, ring, kitName, kit);
        }

        public String permissionName() {
            return ringName == null ? kitName : ringName;
        }

        public long cooldownMs() {
            return ring == null ? kit.cooldownMs() : ring.cooldownMs();
        }
    }

    public void init(RegistryWrapper.WrapperLookup registries) {
        this.registries = registries;
    }

    public Stream<KitRecord> getNonRingKitRecords(Predicate<String> filter) {
        return KIT_MAP.keySet()
            .stream()
            .filter(filter)
            .map(ALL_KITS_MAP::get);
    }

    public Stream<KitRecord> getKitRecords(Predicate<String> filter) {
        return ALL_KITS_MAP.values()
            .stream()
            .filter(ent -> filter.test(ent.permissionName()));
    }

    public Optional<KitRecord> getKitRecord(String kitName) {
        return Optional.ofNullable(ALL_KITS_MAP.get(kitName));
    }

    public void putKit(String kitName, Kit kit) throws IOException {
        KIT_MAP.put(kitName, kit);
        ALL_KITS_MAP.put(kitName, KitRecord.standaloneKit(kitName, kit));
        saveKit(kitName, kit);
    }

    public void putKitRing(String ringName, KitRing ring)
        throws IOException
    {
        KIT_RING_MAP.put(ringName, ring);
        ring.kits().forEach((k, kit) -> {
            if (ALL_KITS_MAP.containsKey(k)) {
                logger.warn("Overwriting existing kit '{}' with kit '{}' from kit ring '{}' (this means you have more than one kit with the same name)",
                    k, k, ringName
                );
            }
            ALL_KITS_MAP.put(k, KitRecord.ringKit(ringName, ring, k, kit));
        });

        saveKitRing(ringName, ring);
    }

    public void putKitInRing(String ringName, String kitName)
        throws KitCommandSyntaxException, IOException
    {
        var ring = getKitRingOrThrow(ringName);

        var kit = KIT_MAP.remove(kitName);
        if (kit == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit '%s' not found".formatted(kitName)
            ));
        }

        ring.addKit(kitName, kit);

        saveKitRing(ringName, ring);
        try {
            Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
        } catch (NoSuchFileException ign) {}
    }

    public void removeKitFromRing(String ringName, String kitName)
        throws KitCommandSyntaxException, IOException
    {
        var ring = getKitRingOrThrow(ringName);

        var removed = ring.removeKit(kitName);

        if (removed == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit '%s' not found in Ring '%s'".formatted(kitName, ringName)
            ));
        }

        KIT_MAP.put(kitName, removed);

        saveKit(kitName, removed);
        saveKitRing(ringName, ring);
    }

    @NotNull KitRing getKitRingOrThrow(String ringName)
        throws KitCommandSyntaxException
    {
        var ring = KIT_RING_MAP.get(ringName);
        if (ring == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit Ring '%s' not found".formatted(ringName)
            ));
        }

        return ring;
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
        ALL_KITS_MAP.clear();
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
                        String ringName = fileName.substring(0, fileName.length() - ".ring.nbt".length());
                        var kitRing = KitRing.fromNbt(ringName, kitRingNbt, registries);
                        KIT_RING_MAP.put(ringName, kitRing);
                        kitRing.kits().forEach((k, kit) -> {
                            if (ALL_KITS_MAP.containsKey(k)) {
                                logger.warn("Overwriting existing kit '{}' with kit '{}' from kit ring '{}' (this means you have more than one kit with the same name)",
                                    k, k, ringName
                                );
                            }
                            ALL_KITS_MAP.put(k, KitRecord.ringKit(ringName, kitRing, k, kit));
                        });
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
                    var kit = Kit.fromNbt(kitName, kitNbt, registries);
                    KIT_MAP.put(kitName, kit);

                    if (ALL_KITS_MAP.containsKey(kitName)) {
                        logger.warn("Overwriting existing kit '{}' with kit '{}' (this means you have more than one kit with the same name)",
                            kitName, kitName
                        );
                    }
                    ALL_KITS_MAP.put(kitName, KitRecord.standaloneKit(kitName, kit));
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

    public void saveKit(String kitName, Kit kit) throws IOException {
        NbtCompound root = kit.toNbt(registries);

        NbtIo.write(
            root,
            KitsMod.getKitsDir().toPath().resolve(String.format("%s.nbt", kitName)).toFile().toPath()
        );
    }

    public void saveKitRing(String kitName, KitRing ring) throws IOException {
        NbtCompound root = new NbtCompound();
        ring.writeNbt(root, registries);

        NbtIo.write(
            root,
            KitsMod.getKitsDir().toPath().resolve(String.format("%s.ring.nbt", kitName)).toFile().toPath()
        );
    }
}
