package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.World;

public class Kit {

    private final KitInventory inventory;
    /** a negative cooldown yields a one-time use kit. */
    private final long cooldownMs;
    private @Nullable Item displayItem;
    private final ArrayList<String> commands;

    public Kit(KitInventory inventory, long cooldownMs) {
        this.inventory = inventory;
        this.cooldownMs = cooldownMs;
        commands = new ArrayList<>();
    }

    public Kit(KitInventory inventory, long cooldownMs, @Nullable Item displayItem, ArrayList<String> commands) {
        this.inventory = inventory;
        this.cooldownMs = cooldownMs;
        this.displayItem = displayItem;
        this.commands = commands;
    }

    public KitInventory inventory() {
        return inventory;
    }

    public long cooldownMs() {
        return cooldownMs;
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

    private static final class StorageKey {
        public static final String SCHEMA_VERSION = "_schema_version";
        public static final String INVENTORY = "inventory";
        public static final String COOLDOWN = "cooldown";
        public static final String DISPLAY_ITEM = "display_item";
        public static final String COMMANDS = "commands";
    }

    public NbtCompound toNbt(World world) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Kit.class);
        try (ErrorReporter.Logging logging = new ErrorReporter.Logging(() -> "Kit.writeNbt", logger)) {
            var root = NbtWriteView.create(logging, world.getRegistryManager());

            root.putInt(StorageKey.SCHEMA_VERSION, 1);

            var inventoryListAppender = root.getListAppender(StorageKey.INVENTORY, StackWithSlot.CODEC);
            this.inventory().writeData(inventoryListAppender);

            root.putLong(StorageKey.COOLDOWN, this.cooldownMs());

            if (this.displayItem().isPresent()) {
                root.putString(
                    StorageKey.DISPLAY_ITEM,
                    Registries.ITEM.getKey(this.displayItem().get()).get().getValue().toString());
            }

            if (!commands.isEmpty()) {
                var list = root.getListAppender(StorageKey.COMMANDS, Codecs.NON_EMPTY_STRING);
                for (String command : commands) {
                    list.add(command);
                }
            }

            return root.getNbt();
        }
    }

    private static void handleReadVersion(@NotNull NbtCompound kitNbt) {

        if (!kitNbt.contains(StorageKey.SCHEMA_VERSION)) {
            // Upgrade version nil to v1
            // Negative cooldowns replaced with 0, since that was the old behavior
            var cd = kitNbt.getLong(StorageKey.COOLDOWN).orElse(0L);
            if (cd < 0) {
                kitNbt.putLong(StorageKey.COOLDOWN, 0);
            }

            return;
        }
        var version = kitNbt.getInt(StorageKey.SCHEMA_VERSION);
        // other version handling...
    }

    public static Kit fromNbt(NbtCompound kitNbt, World world) {
        var kitInventory = new KitInventory();

        assert kitNbt != null;
        handleReadVersion(kitNbt);

        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Kit.class);
        try (ErrorReporter.Logging logging = new ErrorReporter.Logging(() -> "Kit.writeNbt", logger)) {
            var nbt = NbtReadView.create(logging, world.getRegistryManager(), kitNbt);

            var inventoryView = nbt.getTypedListView(StorageKey.INVENTORY, StackWithSlot.CODEC);
            kitInventory.readData(inventoryView);

        }
        long cooldown = kitNbt.getLong(StorageKey.COOLDOWN).orElse(0L);
        var kitDisplayItem = kitNbt.getString(StorageKey.DISPLAY_ITEM)
            .map(Identifier::of)
            .map(Registries.ITEM::get)
            .orElse(null);

        ArrayList<String> commands = kitNbt.getList(StorageKey.COMMANDS)
            .map(l -> new ArrayList<>(kitNbt.getList(StorageKey.COMMANDS).orElseThrow().stream().map(e -> e.asString().orElseThrow()).toList()))
            .orElseGet(ArrayList::new);

        return new Kit(kitInventory, cooldown, kitDisplayItem, commands);
    }
}
