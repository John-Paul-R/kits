package dev.jpcode.kits;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class Kit {

    private final KitInventory inventory;
    private final long cooldown;
    private @Nullable Item displayItem;

    public Kit(KitInventory inventory, long cooldown) {
        this.inventory = inventory;
        this.cooldown = cooldown;
    }

    public Kit(KitInventory inventory, long cooldown, Item displayItem) {
        this.inventory = inventory;
        this.cooldown = cooldown;
        this.displayItem = displayItem;
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

    public void setDisplayItem(Item item) {
        this.displayItem = item;
    }

    private static class StorageKey {
        public static final String INVENTORY = "inventory";
        public static final String COOLDOWN = "cooldown";
        public static final String DISPLAY_ITEM = "display_item";
    }

    public NbtCompound writeNbt(NbtCompound root) {
        root.put(StorageKey.INVENTORY, this.inventory().writeNbt(new NbtList()));
        root.putLong(StorageKey.COOLDOWN, this.cooldown());
        if (this.displayItem().isPresent()) {
            root.putString(
                StorageKey.DISPLAY_ITEM,
                Registry.ITEM.getKey(this.displayItem().get()).get().getValue().toString());
        }

        return root;
    }

    public static Kit fromNbt(NbtCompound kitNbt) {
        var kitInventory = new KitInventory();

        assert kitNbt != null;
        kitInventory.readNbt(kitNbt.getList(StorageKey.INVENTORY, NbtElement.COMPOUND_TYPE));
        long cooldown = kitNbt.getLong(StorageKey.COOLDOWN);
        var kitDisplayItem = kitNbt.contains(StorageKey.DISPLAY_ITEM)
            ? Registry.ITEM.get(new Identifier(kitNbt.getString(StorageKey.DISPLAY_ITEM)))
            : null;

        return new Kit(kitInventory, cooldown, kitDisplayItem);
    }
}
