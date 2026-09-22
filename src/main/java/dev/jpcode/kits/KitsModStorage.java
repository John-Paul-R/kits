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

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import dev.jpcode.kits.config.KitsConfig;

public class KitsModStorage {
    private final Logger logger;
    private final KitsConfig config;
    private HolderLookup.Provider registries;

    public final Map<String, Kit> KIT_MAP = new HashMap<>();
    public final Map<String, KitRing> KIT_RING_MAP = new HashMap<>();

    /** All kits, regardless of whether they're in a ring or not. */
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

        /** Used as the permission key for permissions mods and as the kitCooldownKey. */
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

    public void init(HolderLookup.Provider registries) {
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
        loadKitRing(ringName, ring);

        saveKitRing(ringName, ring);
    }

    public void removeKitRingAndKits(String ringName) throws IOException {
        var removedRing = KIT_RING_MAP.remove(ringName);
        if (removedRing != null) {
            removedRing.kits().keySet().forEach(ALL_KITS_MAP::remove);
        }

        // Delete the ring directory and all its contents
        deleteRingDirectory(ringName);
    }

    public void convertRingKitsToStandalone(String ringName, KitRing ring) {
        for (var entry : ring.kits().entrySet()) {
            String kitName = entry.getKey();
            Kit kit = entry.getValue();
            KIT_MAP.put(kitName, kit);
            ALL_KITS_MAP.put(kitName, KitRecord.standaloneKit(kitName, kit));
        }
    }

    public void deleteRingDirectory(String ringName) throws IOException {
        // Delete the ring directory and all its contents
        Path ringDir = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring");
        if (Files.exists(ringDir)) {
            try (var stream = Files.walk(ringDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete path '{}' in ring directory", path, e);
                        }
                    });
            }
        }

        // Clean up legacy formats
        Files.deleteIfExists(KitsMod.getKitsDir().toPath().resolve(ringName + ".ring.nbt"));
        Files.deleteIfExists(KitsMod.getKitsDir().toPath().resolve(ringName + ".ring.json"));
    }

    public void removeRingExtractKits(String ringName) throws IOException {
        var removedRing = KIT_RING_MAP.remove(ringName);
        if (removedRing == null) {
            throw new IllegalArgumentException("Kit ring not found: " + ringName);
        }

        // Extract all kits from the ring and save them as standalone kits
        Path ringDir = KitsMod.getKitsDir().toPath().resolve(ringName + ".ring");

        for (var entry : removedRing.kits().entrySet()) {
            String kitName = entry.getKey();
            Kit kit = entry.getValue();

            KIT_MAP.put(kitName, kit);
            ALL_KITS_MAP.put(kitName, new KitRecord(null, null, kitName, kit));

            // Move kit file from ring directory to kits directory
            Path kitInRingPath = ringDir.resolve(kitName + ".json");
            Path kitStandalonePath = KitsMod.getKitsDir().toPath().resolve(kitName + ".json");

            if (Files.exists(kitInRingPath)) {
                Files.move(kitInRingPath, kitStandalonePath, StandardCopyOption.ATOMIC_MOVE);
            } else {
                // If file doesn't exist, save it fresh
                saveKit(kitName, kit);
            }
        }

        // Update storage maps
        convertRingKitsToStandalone(ringName, removedRing);

        // Delete the (now empty) ring directory
        deleteRingDirectory(ringName);
    }

    public void removeKit(String kitName) throws IOException {
        KIT_MAP.remove(kitName);
        ALL_KITS_MAP.remove(kitName);

        // Try deleting both JSON and legacy NBT formats
        Path jsonPath = KitsMod.getKitsDir().toPath().resolve(kitName + ".json");
        Path nbtPath = KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt");

        boolean jsonDeleted = Files.deleteIfExists(jsonPath);
        boolean nbtDeleted = Files.deleteIfExists(nbtPath);

        if (!jsonDeleted && !nbtDeleted) {
            throw new NoSuchFileException(kitName + " (checked both .json and .nbt)");
        }
    }

    public void putKitInRing(String ringName, String kitName)
        throws KitCommandSyntaxException, IOException
    {
        var ring = getKitRingOrThrow(ringName);

        var kit = KIT_MAP.remove(kitName);
        if (kit == null) {
            throw new KitCommandSyntaxException(Component.literal(
                "Kit '%s' not found".formatted(kitName)
            ));
        }

        ring.addKit(kitName, kit);

        ALL_KITS_MAP.put(kitName, new KitRecord(ringName, ring, kitName, kit));

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
            var kitJsonElement = Kit.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), kit).getOrThrow();
            String kitJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(kitJsonElement);
            Files.writeString(destKitPath, kitJson);
            // Clean up old NBT file if it exists
            try {
                Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
            } catch (NoSuchFileException ign) {
                // no handling
            }
        }
    }

    public void removeKitFromRing(String ringName, String kitName)
        throws KitCommandSyntaxException, IOException
    {
        var ring = getKitRingOrThrow(ringName);

        var removed = ring.removeKit(kitName);

        if (removed == null) {
            throw new KitCommandSyntaxException(Component.literal(
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
            throw new KitCommandSyntaxException(Component.literal(
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
        userDataDir = server.getWorldPath(LevelResource.ROOT).resolve("kits_user_data");

        // if the dir was not just created, load all kits from dir.
        if (kitsDir.mkdirs()) {
            return;
        }

        File[] kitsFiles = kitsDir.listFiles();
        if (kitsFiles == null) {
            throw new IllegalStateException(
                String.format("Failed to list files in the kits directory ('%s')", kitsDir.getPath()));
        }

        for (File file : kitsFiles) {
            // Check for ring directories (ringname.ring/)
            if (file.isDirectory() && file.getName().endsWith(".ring")) {
                try {
                    String ringName = file.getName().substring(0, file.getName().length() - ".ring".length());
                    logger.info("Loading kit ring directory '{}'", file.getName());

                    // Load ring metadata from _ring.json
                    Path metadataPath = file.toPath().resolve("_ring.json");
                    if (!Files.exists(metadataPath)) {
                        logger.error("Ring directory '{}' missing _ring.json metadata file", file.getName());
                        continue;
                    }

                    String metadataJson = Files.readString(metadataPath);
                    var metadataElement = JsonParser.parseString(metadataJson);
                    var kitRing = KitRing.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), metadataElement).getOrThrow();

                    // Load all kit files from the ring directory
                    File[] ringKitFiles = file.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("_ring.json"));
                    if (ringKitFiles != null) {
                        for (File ringKitFile : ringKitFiles) {
                            String kitName = ringKitFile.getName().substring(0, ringKitFile.getName().length() - ".json".length());
                            String kitJson = Files.readString(ringKitFile.toPath());
                            var kitJsonElement = JsonParser.parseString(kitJson);
                            var kit = Kit.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), kitJsonElement).getOrThrow();

                            kit.setCooldownTrackerKey(ringName);
                            kitRing.addKit(kitName, kit);
                        }
                    }

                    loadKitRing(ringName, kitRing);
                } catch (IOException | IllegalStateException | NullPointerException e) {
                    logger.error("Error while loading kit ring '{}'", file.getPath());
                    e.printStackTrace();
                }
                continue;
            }

            // Legacy: Load old .ring.json format (migrate to directory structure)
            if (file.getPath().endsWith(".ring.json")) {
                try {
                    logger.info("Loading legacy kit ring file '{}' (will migrate to directory format)", file.getName());
                    String json = Files.readString(file.toPath());
                    var jsonElement = JsonParser.parseString(json);
                    final String fileName = file.getName();
                    final String ringName = fileName.substring(0, fileName.length() - ".ring.json".length());
                    final var kitRing = dev.jpcode.kits.codec.Codecs.RING_METADATA_WITH_KITS.parse(RegistryOps.create(JsonOps.INSTANCE, registries), jsonElement).getOrThrow();

                    // Set cooldown tracker keys for all kits
                    kitRing.kits().forEach((k, kit) -> {
                        kit.setCooldownTrackerKey(ringName);
                    });

                    loadKitRing(ringName, kitRing);

                    // Migrate to new directory format on IO thread
                    migrateRingToDirectoryFormat(ringName, kitRing);
                } catch (IOException | IllegalStateException | NullPointerException e) {
                    logger.error("Error while loading kit ring '{}'", file.getPath());
                    e.printStackTrace();
                }
                continue;
            }

            // Legacy NBT support for kit rings
            if (file.getPath().endsWith(".ring.nbt")) {
                try {
                    logger.info("Loading legacy kit ring NBT file '{}' (will migrate to directory format)", file.getName());
                    final CompoundTag kitRingNbt = NbtIo.read(file.toPath());
                    final String fileName = file.getName();
                    final String ringName = fileName.substring(0, fileName.length() - ".ring.nbt".length());

                    final var kitRing = KitRing.fromNbt(ringName, kitRingNbt, registries);

                    loadKitRing(ringName, kitRing);

                    migrateRingToDirectoryFormat(ringName, kitRing);

                } catch (IOException | IllegalStateException | NullPointerException | ReportedNbtException e) {
                    logger.error("Error while loading kit ring '{}'", file.getPath());
                    e.printStackTrace();
                }
                continue;
            }

            // Try JSON kit first
            if (file.getPath().endsWith(".json")) {
                try {
                    logger.info("Loading kit '{}'", file.getName());
                    String json = Files.readString(file.toPath());
                    var jsonElement = JsonParser.parseString(json);
                    String fileName = file.getName();
                    String kitName = fileName.substring(0, fileName.length() - ".json".length());
                    var kit = Kit.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), jsonElement).getOrThrow();
                    kit.setCooldownTrackerKey(kitName);
                    KIT_MAP.put(kitName, kit);

                    if (ALL_KITS_MAP.containsKey(kitName)) {
                        logger.warn("Overwriting existing kit '{}' with kit '{}' (this means you have more than one kit with the same name)",
                            kitName, kitName
                        );
                    }
                    ALL_KITS_MAP.put(kitName, KitRecord.standaloneKit(kitName, kit));
                } catch (IOException | IllegalStateException | NullPointerException e) {
                    logger.error("Error while loading kit '{}'", file.getPath());
                    e.printStackTrace();
                }
                continue;
            }

            // Legacy NBT support for kits
            if (file.getPath().endsWith(".nbt")) {
                try {
                    logger.info("Loading kit '{}' (legacy NBT format)", file.getName());
                    CompoundTag kitNbt = NbtIo.read(file.toPath());
                    String fileName = file.getName();
                    String kitName = fileName.substring(0, fileName.length() - 4);

                    // Parse and check if upgrade occurred
                    var loadResult = Kit.fromNbtWithResult(kitName, kitNbt, registries);
                    Kit kit = loadResult.kit();

                    // Queue migration to JSON on IO thread if upgraded
                    if (loadResult.wasUpgraded()) {
                        final String finalKitName = kitName;
                        final Kit finalKit = kit;
                        net.minecraft.util.Util.ioPool().execute(() -> {
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
                } catch (IOException | IllegalStateException | NullPointerException | ReportedNbtException e) {
                    logger.error("Error while loading kit '{}'", file.getPath());
                    e.printStackTrace();
                }
            }
        }
    }

    private void migrateRingToDirectoryFormat(String ringName, KitRing kitRing) {
        net.minecraft.util.Util.ioPool().execute(() -> {
            try {
                logger.info("Migrating kit ring '{}' to directory format", ringName);
                saveKitRing(ringName, kitRing);
            } catch (IOException e) {
                logger.error("Failed to migrate kit ring '{}' to directory format", ringName, e);
            }
        });
    }

    private void loadKitRing(String ringName, KitRing kitRing) {
        KIT_RING_MAP.put(ringName, kitRing);
        kitRing.kits().forEach((k, kit) -> {
            if (ALL_KITS_MAP.containsKey(k)) {
                logger.warn("Overwriting existing kit '{}' with kit '{}' from kit ring '{}' (this means you have more than one kit with the same name)",
                    k, k, ringName
                );
            }
            ALL_KITS_MAP.put(k, KitRecord.ringKit(ringName, kitRing, k, kit));
        });
    }

    private static File ensureKitsDir(MinecraftServer server)
    {
        var buggedKitsDir = server.getServerDirectory().getFileName().resolve("config/kits").toFile();
        var correctKitsDir = server.getServerDirectory().resolve("config/kits").toFile();
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
        var jsonElement = Kit.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), kit).getOrThrow();
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
        var jsonElement = KitRing.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), ring).getOrThrow();
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

            var kitJsonElement = Kit.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), kit).getOrThrow();
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
