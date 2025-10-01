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

import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtCrashException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryOps;
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

        /**
         * used as the permission key for permissions mods and as the kitCooldownKey
         */
        public String permissionName() {
            return ringName == null ? kitName : ringName;
        }

        public long cooldownMs() {
            return ring == null ? kit.cooldownMs() : ring.cooldownMs();
        }

        public String cooldownKey() {
            return permissionName();
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

    public Stream<KitRecord> getKitRecords(Predicate<KitRecord> filter) {
        return ALL_KITS_MAP.values()
            .stream()
            .filter(filter);
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

        // Move the kit file into the ring directory
        Path ringDir = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring");
        Files.createDirectories(ringDir);

        Path sourceKitPath = KitsMod.getKitsDir().toPath().resolve(kitName + ".json");
        Path destKitPath = ringDir.resolve(kitName + ".json");

        try {
            // Try to move the existing kit file
            Files.move(sourceKitPath, destKitPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException e) {
            // If the .json file doesn't exist, save it fresh (also handles .nbt legacy case)
            var kitJsonElement = Kit.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, registries), kit).getOrThrow();
            String kitJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(kitJsonElement);
            Files.writeString(destKitPath, kitJson);
            // Clean up old NBT file if it exists
            try {
                Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
            } catch (NoSuchFileException ign) {}
        }
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

        // Remove kit file from ring directory
        Path ringDir = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring");
        Path kitInRingPath = ringDir.resolve(kitName + ".json");
        try {
            Files.deleteIfExists(kitInRingPath);
        } catch (IOException e) {
            logger.warn("Failed to delete kit file '{}' from ring directory", kitInRingPath, e);
        }

        // Save the removed kit as a standalone kit
        saveKit(kitName, removed);

        // No need to save ring metadata - we only modified the kits map in memory
        // The kit file is already deleted from the ring directory
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
                // Check for ring directories (ringname.ring/)
                if (kitFile.isDirectory() && kitFile.getName().endsWith(".ring")) {
                    try {
                        String ringName = kitFile.getName().substring(0, kitFile.getName().length() - ".ring".length());
                        logger.info("Loading kit ring directory '{}'", kitFile.getName());

                        // Load ring metadata from _ring.json
                        Path metadataPath = kitFile.toPath().resolve("_ring.json");
                        if (!Files.exists(metadataPath)) {
                            logger.error("Ring directory '{}' missing _ring.json metadata file", kitFile.getName());
                            continue;
                        }

                        String metadataJson = Files.readString(metadataPath);
                        var metadataElement = JsonParser.parseString(metadataJson);
                        var kitRing = KitRing.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE, registries), metadataElement).getOrThrow();

                        // Load all kit files from the ring directory
                        File[] ringKitFiles = kitFile.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("_ring.json"));
                        if (ringKitFiles != null) {
                            for (File ringKitFile : ringKitFiles) {
                                String kitName = ringKitFile.getName().substring(0, ringKitFile.getName().length() - ".json".length());
                                String kitJson = Files.readString(ringKitFile.toPath());
                                var kitJsonElement = JsonParser.parseString(kitJson);
                                var kit = Kit.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE, registries), kitJsonElement).getOrThrow();

                                kit.setCooldownTrackerKey(ringName);
                                kit.setCooldownMs(kitRing.cooldownMs());
                                kitRing.addKit(kitName, kit);
                            }
                        }

                        KIT_RING_MAP.put(ringName, kitRing);
                        kitRing.kits().forEach((k, kit) -> {
                            if (ALL_KITS_MAP.containsKey(k)) {
                                logger.warn("Overwriting existing kit '{}' with kit '{}' from kit ring '{}' (this means you have more than one kit with the same name)",
                                    k, k, ringName
                                );
                            }
                            ALL_KITS_MAP.put(k, KitRecord.ringKit(ringName, kitRing, k, kit));
                        });
                    } catch (IOException | IllegalStateException | NullPointerException e) {
                        logger.error("Error while loading kit ring '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
                    continue;
                }

                // Legacy: Load old .ring.json format (migrate to directory structure)
                if (kitFile.getPath().endsWith(".ring.json")) {
                    try {
                        logger.info("Loading legacy kit ring file '{}' (will migrate to directory format)", kitFile.getName());
                        String json = Files.readString(kitFile.toPath());
                        var jsonElement = JsonParser.parseString(json);
                        String fileName = kitFile.getName();
                        String ringName = fileName.substring(0, fileName.length() - ".ring.json".length());
                        var kitRing = dev.jpcode.kits.codec.Codecs.RING_METADATA_WITH_KITS.parse(RegistryOps.of(JsonOps.INSTANCE, registries), jsonElement).getOrThrow();

                        // Set cooldown tracker keys for all kits
                        kitRing.kits().forEach((k, kit) -> {
                            kit.setCooldownTrackerKey(ringName);
                            kit.setCooldownMs(kitRing.cooldownMs());
                        });

                        KIT_RING_MAP.put(ringName, kitRing);
                        kitRing.kits().forEach((k, kit) -> {
                            if (ALL_KITS_MAP.containsKey(k)) {
                                logger.warn("Overwriting existing kit '{}' with kit '{}' from kit ring '{}' (this means you have more than one kit with the same name)",
                                    k, k, ringName
                                );
                            }
                            ALL_KITS_MAP.put(k, KitRecord.ringKit(ringName, kitRing, k, kit));
                        });

                        // Migrate to new directory format on IO thread
                        final String finalRingName = ringName;
                        final KitRing finalRing = kitRing;
                        net.minecraft.util.Util.getIoWorkerExecutor().execute(() -> {
                            try {
                                logger.info("Migrating kit ring '{}' to directory format", finalRingName);
                                saveKitRing(finalRingName, finalRing);
                            } catch (IOException e) {
                                logger.error("Failed to migrate kit ring '{}' to directory format", finalRingName, e);
                            }
                        });
                    } catch (IOException | IllegalStateException | NullPointerException e) {
                        logger.error("Error while loading kit ring '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
                    continue;
                }

                // Legacy NBT support for kit rings
                if (kitFile.getPath().endsWith(".ring.nbt")) {
                    try {
                        logger.info("Loading legacy kit ring NBT file '{}' (will migrate to directory format)", kitFile.getName());
                        NbtCompound kitRingNbt = NbtIo.read(kitFile.toPath());
                        String fileName = kitFile.getName();
                        String ringName = fileName.substring(0, fileName.length() - ".ring.nbt".length());

                        // Parse and migrate to directory format
                        var loadResult = KitRing.fromNbtWithResult(ringName, kitRingNbt, registries);
                        KitRing kitRing = loadResult.ring();

                        // Always migrate NBT format to new directory structure
                        final String finalRingName = ringName;
                        final KitRing finalRing = kitRing;
                        net.minecraft.util.Util.getIoWorkerExecutor().execute(() -> {
                            try {
                                logger.info("Migrating kit ring '{}' to directory format", finalRingName);
                                saveKitRing(finalRingName, finalRing);
                            } catch (IOException e) {
                                logger.error("Failed to migrate kit ring '{}' to directory format", finalRingName, e);
                            }
                        });

                        KIT_RING_MAP.put(ringName, kitRing);
                        kitRing.kits().forEach((k, kit) -> {
                            if (ALL_KITS_MAP.containsKey(k)) {
                                logger.warn("Overwriting existing kit '{}' with kit '{}' from kit ring '{}' (this means you have more than one kit with the same name)",
                                    k, k, ringName
                                );
                            }
                            ALL_KITS_MAP.put(k, KitRecord.ringKit(ringName, kitRing, k, kit));
                        });
                    } catch (IOException | IllegalStateException |NullPointerException | NbtCrashException e) {
                        logger.error("Error while loading kit ring '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
                    continue;
                }

                // Try JSON kit first
                if (kitFile.getPath().endsWith(".json")) {
                    try {
                        logger.info("Loading kit '{}'", kitFile.getName());
                        String json = Files.readString(kitFile.toPath());
                        var jsonElement = JsonParser.parseString(json);
                        String fileName = kitFile.getName();
                        String kitName = fileName.substring(0, fileName.length() - ".json".length());
                        var kit = Kit.CODEC.parse(RegistryOps.of(JsonOps.INSTANCE, registries), jsonElement).getOrThrow();
                        kit.setCooldownTrackerKey(kitName);
                        KIT_MAP.put(kitName, kit);

                        if (ALL_KITS_MAP.containsKey(kitName)) {
                            logger.warn("Overwriting existing kit '{}' with kit '{}' (this means you have more than one kit with the same name)",
                                kitName, kitName
                            );
                        }
                        ALL_KITS_MAP.put(kitName, KitRecord.standaloneKit(kitName, kit));
                    } catch (IOException | IllegalStateException | NullPointerException e) {
                        logger.error("Error while loading kit '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
                    continue;
                }

                // Legacy NBT support for kits
                if (kitFile.getPath().endsWith(".nbt")) {
                    try {
                        logger.info("Loading kit '{}' (legacy NBT format)", kitFile.getName());
                        NbtCompound kitNbt = NbtIo.read(kitFile.toPath());
                        String fileName = kitFile.getName();
                        String kitName = fileName.substring(0, fileName.length() - 4);

                        // Parse and check if upgrade occurred
                        var loadResult = Kit.fromNbtWithResult(kitName, kitNbt, registries);
                        Kit kit = loadResult.kit();

                        // Queue migration to JSON on IO thread if upgraded
                        if (loadResult.wasUpgraded()) {
                            final String finalKitName = kitName;
                            final Kit finalKit = kit;
                            net.minecraft.util.Util.getIoWorkerExecutor().execute(() -> {
                                try {
                                    logger.info("Migrating upgraded kit '{}' to JSON format", finalKitName);
                                    saveKit(finalKitName, finalKit);
                                } catch (IOException e) {
                                    logger.error("Failed to migrate kit '{}' to JSON", finalKitName, e);
                                }
                            });
                        }

                        KIT_MAP.put(kitName, kit);

                        if (ALL_KITS_MAP.containsKey(kitName)) {
                            logger.warn("Overwriting existing kit '{}' with kit '{}' (this means you have more than one kit with the same name)",
                                kitName, kitName
                            );
                        }
                        ALL_KITS_MAP.put(kitName, KitRecord.standaloneKit(kitName, kit));
                    } catch (IOException | IllegalStateException | NullPointerException | NbtCrashException e) {
                        logger.error("Error while loading kit '{}'", kitFile.getPath());
                        e.printStackTrace();
                    }
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
        var jsonElement = Kit.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, registries), kit).getOrThrow();
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement);

        Path kitPath = KitsMod.getKitsDir().toPath().resolve(String.format("%s.json", kitName));
        Files.writeString(kitPath, json);

        // Remove old NBT file if it exists
        Path oldNbtPath = KitsMod.getKitsDir().toPath().resolve(String.format("%s.nbt", kitName));
        try {
            Files.deleteIfExists(oldNbtPath);
        } catch (IOException e) {
            logger.warn("Failed to delete old NBT file for kit '{}'", kitName, e);
        }
    }

    public void saveKitRingMetadata(String ringName, KitRing ring) throws IOException {
        // Create ring directory if it doesn't exist
        Path ringDir = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring");
        Files.createDirectories(ringDir);

        // Save ring metadata to _ring.json (kits field omitted when empty)
        var jsonElement = KitRing.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, registries), ring).getOrThrow();
        String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonElement);
        Path metadataPath = ringDir.resolve("_ring.json");
        Files.writeString(metadataPath, json);
    }

    public void saveKitRing(String ringName, KitRing ring) throws IOException {
        // Save metadata first
        saveKitRingMetadata(ringName, ring);

        Path ringDir = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring");

        // Save each kit as a separate file in the ring directory
        for (Map.Entry<String, Kit> entry : ring.kits().entrySet()) {
            String kitName = entry.getKey();
            Kit kit = entry.getValue();

            var kitJsonElement = Kit.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, registries), kit).getOrThrow();
            String kitJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(kitJsonElement);
            Path kitPath = ringDir.resolve(kitName + ".json");
            Files.writeString(kitPath, kitJson);
        }

        // Clean up old formats if they exist
        Path oldRingJsonPath = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring.json");
        Path oldRingNbtPath = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring.nbt");
        try {
            Files.deleteIfExists(oldRingJsonPath);
            Files.deleteIfExists(oldRingNbtPath);
        } catch (IOException e) {
            logger.warn("Failed to delete old ring file for '{}'", ringName, e);
        }
    }
}
