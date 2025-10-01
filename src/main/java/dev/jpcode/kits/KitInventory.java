package dev.jpcode.kits;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.StackWithSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;

public class KitInventory implements Inventory {

    public static final int MAIN_SIZE = 36;
    private static final int HOTBAR_SIZE = 9;
    public static final int OFF_HAND_SLOT = 40;
    public static final int NOT_FOUND = -1;
    public static final int[] ARMOR_SLOTS = new int[]{0, 1, 2, 3};
    public static final int[] HELMET_SLOTS = new int[]{3};

    public final DefaultedList<ItemStack> main;
    public final DefaultedList<ItemStack> armor;
    public final DefaultedList<ItemStack> offHand;
    private final List<DefaultedList<ItemStack>> combinedInventory;
    private int changeCount;

    public KitInventory() {
        this.main = DefaultedList.ofSize(MAIN_SIZE, ItemStack.EMPTY);
        this.armor = DefaultedList.ofSize(ARMOR_SLOTS.length, ItemStack.EMPTY);
        this.offHand = DefaultedList.ofSize(1, ItemStack.EMPTY);
        this.combinedInventory = ImmutableList.of(this.main, this.armor, this.offHand);
    }

    public static int getHotbarSize() {
        return 9;
    }

    public static boolean isValidHotbarIndex(int slot) {
        return slot >= 0 && slot < 9;
    }

    private boolean canStackAddMore(@NotNull ItemStack existingStack, ItemStack stack) {
        return !existingStack.isEmpty()
            && ItemStack.areItemsAndComponentsEqual(existingStack, stack)
            && existingStack.isStackable()
            && existingStack.getCount() < this.getMaxCount(existingStack);
    }

    public int getEmptySlot() {
        for (int i = 0; i < this.main.size(); ++i) {
            if (this.main.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public int getSlotWithStack(ItemStack stack) {
        for (int i = 0; i < this.main.size(); ++i) {
            if (!this.main.get(i).isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, this.main.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public static boolean usableWhenFillingSlot(ItemStack stack) {
        return !stack.isDamaged() && !stack.hasEnchantments() && !stack.contains(DataComponentTypes.CUSTOM_NAME);
    }

    public int indexOf(ItemStack stack) {
        for (int i = 0; i < this.main.size(); ++i) {
            ItemStack itemStack = this.main.get(i);
            if (!itemStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, itemStack) && usableWhenFillingSlot(itemStack)) {
                return i;
            }
        }

        return -1;
    }

    public void setStack(int slot, ItemStack stack) {
        DefaultedList<ItemStack> defaultedList = null;

        DefaultedList<ItemStack> defaultedList2;
        for (java.util.Iterator<DefaultedList<ItemStack>> combinedInventoryIterator = this.combinedInventory.iterator(); combinedInventoryIterator.hasNext(); slot -= defaultedList2.size()) {
            defaultedList2 = combinedInventoryIterator.next();
            if (slot < defaultedList2.size()) {
                defaultedList = defaultedList2;
                break;
            }
        }

        if (defaultedList != null) {
            defaultedList.set(slot, stack);
        }

    }

    public void writeData(WriteView.ListAppender<StackWithSlot> list) {
        for(int i = 0; i < this.main.size(); ++i) {
            ItemStack itemStack = this.main.get(i);
            if (!itemStack.isEmpty()) {
                list.add(new StackWithSlot(i, itemStack));
            }
        }
    }

    private record ItemSlot(int slot, @Nullable ItemStack stack) { }

    public void readData(ReadView.TypedListReadView<StackWithSlot> list) {
        this.main.clear();

        for(StackWithSlot stackWithSlot : list) {
            if (stackWithSlot.isValidSlot(this.main.size())) {
                this.setStack(stackWithSlot.slot(), stackWithSlot.stack());
            }
        }
    }

    public int size() {
        return this.main.size() + this.armor.size() + this.offHand.size();
    }

    public boolean isEmpty() {
        for (ItemStack itemStack : this.main) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        for (ItemStack itemStack : this.armor) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        for (ItemStack itemStack : this.offHand) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public ItemStack getStack(int slot) {
        List<ItemStack> list = null;

        DefaultedList<ItemStack> defaultedList;
        for (java.util.Iterator<DefaultedList<ItemStack>> combinedInventoryIterator = this.combinedInventory.iterator(); combinedInventoryIterator.hasNext(); slot -= defaultedList.size()) {
            defaultedList = combinedInventoryIterator.next();
            if (slot < defaultedList.size()) {
                list = defaultedList;
                break;
            }
        }

        return list == null ? ItemStack.EMPTY : list.get(slot);
    }

    public ItemStack getArmorStack(int slot) {
        return this.armor.get(slot);
    }

    public void markDirty() {
        ++this.changeCount;
    }

    public int getChangeCount() {
        return this.changeCount;
    }

    public boolean contains(ItemStack stack) {
        for (DefaultedList<ItemStack> itemStacks : this.combinedInventory) {
            for (ItemStack itemStack : itemStacks) {
                if (!itemStack.isEmpty() && ItemStack.areItemsAndComponentsEqual(itemStack, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        for (DefaultedList<ItemStack> itemStacks : this.combinedInventory) {
            for (ItemStack itemStack : itemStacks) {
                if (!itemStack.isEmpty() && itemStack.isIn(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void copyFrom(PlayerInventory other) {
        for (int i = 0; i < this.size(); ++i) {
            this.setStack(i, other.getStack(i).copy());
        }
    }

    public void copyFrom(KitInventory other) {
        for (int i = 0; i < this.size(); ++i) {
            this.setStack(i, other.getStack(i).copy());
        }
    }

    public void clear() {
        this.combinedInventory.forEach(List::clear);
    }

    public ItemStack removeStack(int slot) {
        DefaultedList<ItemStack> defaultedList = null;

        DefaultedList<ItemStack> defaultedList2;
        for (java.util.Iterator<DefaultedList<ItemStack>> var3 = this.combinedInventory.iterator(); var3.hasNext(); slot -= defaultedList2.size()) {
            defaultedList2 = var3.next();
            if (slot < defaultedList2.size()) {
                defaultedList = defaultedList2;
                break;
            }
        }

        if (defaultedList != null && !defaultedList.get(slot).isEmpty()) {
            ItemStack itemStack = defaultedList.get(slot);
            defaultedList.set(slot, ItemStack.EMPTY);
            return itemStack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    public ItemStack removeStack(int slot, int amount) {
        List<ItemStack> list = null;

        DefaultedList<ItemStack> defaultedList;
        for (java.util.Iterator<DefaultedList<ItemStack>> var4 = this.combinedInventory.iterator(); var4.hasNext(); slot -= defaultedList.size()) {
            defaultedList = var4.next();
            if (slot < defaultedList.size()) {
                list = defaultedList;
                break;
            }
        }

        return list != null && !list.get(slot).isEmpty() ? Inventories.splitStack(list, slot, amount) : ItemStack.EMPTY;
    }

    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    public int getOccupiedSlotWithRoomForStack(ItemStack stack) {
        // Try all main slots
        for (int i = 0; i < this.main.size(); ++i) {
            if (this.canStackAddMore(this.main.get(i), stack)) {
                return i;
            }
        }

        // Try offhand
        if (this.canStackAddMore(this.getStack(OFF_HAND_SLOT), stack)) {
            return OFF_HAND_SLOT;
        }

        return -1;
    }

    private int addStack(ItemStack stack) {
        int slotIdx = this.getOccupiedSlotWithRoomForStack(stack);
        if (slotIdx == -1) {
            slotIdx = this.getEmptySlot();
        }

        if (slotIdx == -1) {
            return stack.getCount();
        }

        return this.addStack(slotIdx, stack);
    }

    private int addStack(int slot, ItemStack stack) {
        int i = stack.getCount();
        ItemStack itemStack = this.getStack(slot);
        if (itemStack.isEmpty()) {
            itemStack = stack.copyWithCount(0);
            this.setStack(slot, itemStack);
        }

        int j = this.getMaxCount(itemStack) - itemStack.getCount();
        int k = Math.min(i, j);
        if (k == 0) {
            return i;
        } else {
            i -= k;
            itemStack.increment(k);
            itemStack.setBobbingAnimationTime(5);
            return i;
        }
    }

    public boolean insertStack(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.isDamaged()) {
                if (slot == -1) {
                    slot = this.getEmptySlot();
                }

                if (slot >= 0) {
                    this.main.set(slot, stack.copyAndEmpty());
                    this.main.get(slot).setBobbingAnimationTime(5);
                    return true;
                } else {
                    return false;
                }
            } else {
                int i;
                do {
                    i = stack.getCount();
                    if (slot == -1) {
                        stack.setCount(this.addStack(stack));
                    } else {
                        stack.setCount(this.addStack(slot, stack));
                    }
                } while (!stack.isEmpty() && stack.getCount() < i);

                return stack.getCount() < i;
            }
        } catch (Throwable ex) {
            CrashReport crashReport = CrashReport.create(ex, "Adding item to inventory");
            CrashReportSection crashReportSection = crashReport.addElement("Item being added");
            crashReportSection.add("Item ID", Item.getRawId(stack.getItem()));
            crashReportSection.add("Item data", stack.getDamage());
            crashReportSection.add("Item name", () -> stack.getName().getString());
            throw new CrashException(crashReport);
        }
    }

    public boolean insertStack(ItemStack stack) {
        return this.insertStack(-1, stack);
    }

    public void offerOrDropToPlayer(PlayerInventory playerInventory) {
        for (int i = 0; i < this.size(); ++i) {
            playerInventory.offerOrDrop(this.getStack(i).copy());
        }
    }

}
