package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.ErrorReporter;

import dev.jpcode.kits.datafixer.KitDataFixer;

public class Kit {

    private final KitInventory inventory;
    /** a negative cooldown yields a one-time use kit. */
    private long cooldownMs;
    private @Nullable Item displayItem;
    private final ArrayList<String> commands;
    private String cooldownTrackerKey;

    public Kit(KitInventory inventory, long cooldownMs, String cooldownTrackerKey) {
        this.inventory = inventory;
        this.cooldownMs = cooldownMs;
        this.cooldownTrackerKey = cooldownTrackerKey;
        commands = new ArrayList<>();
    }

    public Kit(
        KitInventory inventory,
        long cooldownMs,
        @Nullable Item displayItem,
        ArrayList<String> commands,
        String cooldownTrackerKey
    ) {
        this.inventory = inventory;
        this.cooldownMs = cooldownMs;
        this.displayItem = displayItem;
        this.commands = commands;
        this.cooldownTrackerKey = cooldownTrackerKey;
    }

    // CODEC constructor
    private Kit(
        List<StackWithSlot> inventoryData,
        long cooldownMs,
        Optional<Item> displayItem,
        List<String> commands
    ) {
        this.inventory = new KitInventory();
        // Will be populated by codec deserialization
        this.cooldownMs = cooldownMs;
        this.displayItem = displayItem.orElse(null);
        this.commands = new ArrayList<>(commands);
        this.cooldownTrackerKey = null; // Must be set by factory
    }

    public KitInventory inventory() {
        return inventory;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    public Optional<Item> displayItem() {
        return Optional.ofNullable(displayItem);
    }

    public void setDisplayItem(@Nullable Item item) {
        this.displayItem = item;
    }

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

    public static Kit createWithData(
        long cooldownMs,
        Optional<Item> displayItem,
        List<StackWithSlot> inventoryData,
        List<String> commands
    ) {
        Kit kit = new Kit(inventoryData, cooldownMs, displayItem, commands);

        // Populate inventory from codec data
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Kit.class);
        try (ErrorReporter.Logging logging = new ErrorReporter.Logging(() -> "Kit.createWithData", logger)) {
            for (StackWithSlot stackWithSlot : inventoryData) {
                if (stackWithSlot.isValidSlot(kit.inventory.main.size())) {
                    kit.inventory.setStack(stackWithSlot.slot(), stackWithSlot.stack());
                }
            }
        }

        return kit;
    }

    public void setCooldownTrackerKey(String cooldownTrackerKey) {
        this.cooldownTrackerKey = cooldownTrackerKey;
    }

    public List<StackWithSlot> getInventoryData() {
        ArrayList<StackWithSlot> list = new ArrayList<>();
        for (int i = 0; i < this.inventory.main.size(); ++i) {
            net.minecraft.item.ItemStack itemStack = this.inventory.main.get(i);
            if (!itemStack.isEmpty()) {
                list.add(new StackWithSlot(i, itemStack));
            }
        }
        return list;
    }

    public static final Codec<Kit> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            // Cooldown
            Codec.LONG
                .optionalFieldOf(StorageKey.COOLDOWN, 0L)
                .forGetter(Kit::cooldownMs),

            // Display item
            Registries.ITEM.getCodec()
                .optionalFieldOf(StorageKey.DISPLAY_ITEM)
                .forGetter(Kit::displayItem),

            // Inventory
            StackWithSlot.CODEC.listOf()
                .optionalFieldOf(StorageKey.INVENTORY, List.of())
                .forGetter(Kit::getInventoryData),

            // Commands
            Codec.STRING.listOf()
                .optionalFieldOf(StorageKey.COMMANDS, List.of())
                .forGetter(Kit::commands)

        ).apply(instance, Kit::createWithData)
    );

    public String getCooldownTrackerKey() {
        return this.cooldownTrackerKey;
    }

    private static final DataFixer _kitDataFixer = KitDataFixer.createDataFixer().build().fixer();
    private static final String SCHEMA_VERSION_KEY = "_schema_version";
    private static final int SCHEMA_VERSION = 3;

    private static final class StorageKey {
        public static final String SCHEMA_VERSION = "_schema_version";
        public static final String INVENTORY = "inventory";
        public static final String COOLDOWN = "cooldown";
        public static final String DISPLAY_ITEM = "display_item";
        public static final String COMMANDS = "commands";
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        return CODEC.encodeStart(NbtOps.INSTANCE, this)
            .getOrThrow()
            .asCompound()
            .orElseThrow();
    }

    public record DataFixResult(NbtCompound nbt, boolean wasUpgraded) { }

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

        nbt = _kitDataFixer.update(
            KitDataFixer.TYPE,
            new Dynamic<NbtElement>(NbtOps.INSTANCE, nbt),
            currentVersion,
            SCHEMA_VERSION
        ).getValue().asCompound().orElseThrow();

        return new DataFixResult(nbt, wasUpgraded);
    }

    public record LoadResult(Kit kit, boolean wasUpgraded) {}

    public static LoadResult fromNbtWithResult(
        String cooldownTrackerKey,
        NbtCompound kitNbt,
        RegistryWrapper.WrapperLookup registries
    ) {
        assert kitNbt != null;
        var fixResult = fixData(kitNbt);

        Kit kit = CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, registries), fixResult.nbt)
            .getOrThrow();

        kit.setCooldownTrackerKey(cooldownTrackerKey);

        return new LoadResult(kit, fixResult.wasUpgraded);
    }

    public static Kit fromNbt(
        String cooldownTrackerKey,
        NbtCompound kitNbt,
        RegistryWrapper.WrapperLookup registries
    ) {
        return fromNbtWithResult(cooldownTrackerKey, kitNbt, registries).kit();
    }
}
