package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import dev.jpcode.kits.datafixer.KitRingDataFixer;

public class KitRing {
    private Text displayName;
    private @Nullable Item displayItem;
    private final Map<String, Kit> kits;
    private final ArrayList<String> commands;
    /** a negative cooldown yields a one-time use kit ring */
    private final long cooldownMs;

    public KitRing(
        Text displayName,
        long cooldownMs,
        @Nullable Item displayItem,
        Map<String, Kit> kits,
        ArrayList<String> commands
    ) {
        this.displayName = displayName;
        this.cooldownMs = cooldownMs;
        this.displayItem = displayItem;
        this.kits = kits;
        this.commands = commands;
    }

    public static KitRing createWithData(
        Optional<Text> displayName,
        long cooldownMs,
        Optional<Item> displayItem,
        Map<String, Kit> kits,
        ArrayList<String> commands
    ) {
        return new KitRing(
            displayName.orElse(null),
            cooldownMs,
            displayItem.orElse(null),
            kits,
            commands
        );
    }

    public Text displayName() {
        return displayName;
    }

    public void setDisplayName(Text displayName) {
        this.displayName = displayName;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public Optional<Item> displayItem() {
        return Optional.ofNullable(displayItem);
    }

    public void setDisplayItem(@Nullable Item displayItem) {
        this.displayItem = displayItem;
    }

    /** return is mutable internal ref */
    public Map<String, Kit> kits() {
        return kits;
    }

    /** return is mutable internal ref */
    public ArrayList<String> commands() {
        return commands;
    }

    public boolean addCommand(String command) {
        if (commands.contains(command)) return false;
        this.commands.add(command);
        return true;
    }

    public boolean removeCommand(String command) {
        if (commands().isEmpty() || !commands.contains(command)) return false;
        else commands.remove(command);
        return true;
    }

    public void addKit(String kitName, Kit kit) {
        this.kits.put(kitName, kit);
    }

    public @Nullable Kit removeKit(String kitName) {
        return this.kits.remove(kitName);
    }

    // STORAGE
    public static final Codec<KitRing> CODEC = dev.jpcode.kits.codec.Codecs.KIT_RING;

    private static final DataFixer _kitRingDataFixer = KitRingDataFixer.createDataFixer().build().fixer();
    private static final String SCHEMA_VERSION_KEY = "_schema_version";
    private static final int SCHEMA_VERSION = 2;

    private static final class StorageKey {
        public static final String SCHEMA_VERSION = "_schema_version";
        public static final String COOLDOWN = "cooldown";
        public static final String DISPLAY_NAME = "display_name";
        public static final String DISPLAY_ITEM = "display_item";
        public static final String COMMANDS = "commands";
        public static final String KITS = "kits";
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        return CODEC.encodeStart(NbtOps.INSTANCE, this)
            .getOrThrow()
            .asCompound()
            .orElseThrow();
    }

    public static class DataFixResult {
        public final NbtCompound nbt;
        public final boolean wasUpgraded;

        public DataFixResult(NbtCompound nbt, boolean wasUpgraded) {
            this.nbt = nbt;
            this.wasUpgraded = wasUpgraded;
        }
    }

    private static DataFixResult fixData(NbtCompound nbt) {
        // Apply datafixer to upgrade from schema 0/1 to schema 2
        int currentVersion = nbt.getInt(SCHEMA_VERSION_KEY, 0);
        boolean wasUpgraded = currentVersion < SCHEMA_VERSION;

        // Handle legacy negative cooldown fix
        if (currentVersion == 0) {
            var cd = nbt.getLong(StorageKey.COOLDOWN).orElse(0L);
            if (cd < 0) {
                nbt.putLong(StorageKey.COOLDOWN, 0);
            }
        }

        nbt = _kitRingDataFixer.update(
            KitRingDataFixer.TYPE,
            new Dynamic<NbtElement>(NbtOps.INSTANCE, nbt),
            currentVersion,
            SCHEMA_VERSION
        ).getValue().asCompound().orElseThrow();

        return new DataFixResult(nbt, wasUpgraded);
    }

    public record LoadResult(KitRing ring, boolean wasUpgraded) {}

    public static LoadResult fromNbtWithResult(
        String cooldownTrackerKey,
        NbtCompound ringNbt,
        RegistryWrapper.WrapperLookup registries
    ) {
        assert ringNbt != null;
        var fixResult = fixData(ringNbt);

        KitRing ring = CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, registries), fixResult.nbt)
            .getOrThrow();

        // Set cooldown tracker keys for all kits
        for (Kit kit : ring.kits.values()) {
            kit.setCooldownTrackerKey(cooldownTrackerKey);
            kit.setCooldownMs(ring.cooldownMs);
        }

        return new LoadResult(ring, fixResult.wasUpgraded);
    }

    public static KitRing fromNbt(
        String cooldownTrackerKey,
        NbtCompound ringNbt,
        RegistryWrapper.WrapperLookup registries
    ) {
        return fromNbtWithResult(cooldownTrackerKey, ringNbt, registries).ring();
    }
}
