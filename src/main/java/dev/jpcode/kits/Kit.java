package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class Kit {

    private final KitInventory inventory;
    private final long cooldown;
    private @Nullable Item displayItem;
    private ArrayList<String> commands;

    public Kit(KitInventory inventory, long cooldown) {
        this.inventory = inventory;
        this.cooldown = cooldown;
        commands = new ArrayList<>();
    }

    public Kit(KitInventory inventory, long cooldown, @Nullable Item displayItem, ArrayList<String> commands) {
        this.inventory = inventory;
        this.cooldown = cooldown;
        this.displayItem = displayItem;
        this.commands = commands;
    }

    public KitInventory inventory() {
        return inventory;
    }

    public long cooldown() {
        return cooldown;
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
        public static final String INVENTORY = "inventory";
        public static final String COOLDOWN = "cooldown";
        public static final String DISPLAY_ITEM = "display_item";
        public static final String COMMANDS = "commands";
    }

    public void writeNbt(NbtCompound root) {
        root.put(StorageKey.INVENTORY, this.inventory().writeNbt(new NbtList()));
        root.putLong(StorageKey.COOLDOWN, this.cooldown());
        if (this.displayItem().isPresent()) {
            root.putString(
                StorageKey.DISPLAY_ITEM,
                Registry.ITEM.getKey(this.displayItem().get()).get().getValue().toString());
        }
        if (!commands.isEmpty()) {
            NbtList list = new NbtList();
            for (String command : commands) {
                list.add(NbtString.of(command));
            }
            root.put(StorageKey.COMMANDS, list);
        }
    }

    public static Kit fromNbt(NbtCompound kitNbt) {
        var kitInventory = new KitInventory();

        assert kitNbt != null;
        kitInventory.readNbt(kitNbt.getList(StorageKey.INVENTORY, NbtElement.COMPOUND_TYPE));
        long cooldown = kitNbt.getLong(StorageKey.COOLDOWN);
        var kitDisplayItem = kitNbt.contains(StorageKey.DISPLAY_ITEM)
            ? Registry.ITEM.get(new Identifier(kitNbt.getString(StorageKey.DISPLAY_ITEM)))
            : null;
        ArrayList<String> commands = kitNbt.contains(StorageKey.COMMANDS)
            ? new ArrayList<>(kitNbt.getList(StorageKey.COMMANDS, NbtElement.STRING_TYPE).stream().map(NbtElement::asString).toList())
            : new ArrayList<>();

        return new Kit(kitInventory, cooldown, kitDisplayItem, commands);
    }
}
