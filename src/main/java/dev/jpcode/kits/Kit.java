package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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

    public void writeNbt(NbtCompound root, World world) {
        root.putInt(StorageKey.SCHEMA_VERSION, 1);
        root.put(StorageKey.INVENTORY, this.inventory().writeNbt(new NbtList(), world));
        root.putLong(StorageKey.COOLDOWN, this.cooldownMs());
        if (this.displayItem().isPresent()) {
            root.putString(
                StorageKey.DISPLAY_ITEM,
                Registries.ITEM.getKey(this.displayItem().get()).get().getValue().toString());
        }
        if (!commands.isEmpty()) {
            NbtList list = new NbtList();
            for (String command : commands) {
                list.add(NbtString.of(command));
            }
            root.put(StorageKey.COMMANDS, list);
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

        kitInventory.readNbt(kitNbt.getList(StorageKey.INVENTORY).orElseThrow(), world);
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
