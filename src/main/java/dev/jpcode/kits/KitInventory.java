package dev.jpcode.kits;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class KitInventory implements Container {

    public static final int MAIN_SIZE = 36;
    private static final int HOTBAR_SIZE = 9;
    public static final int OFF_HAND_SLOT = 40;
    public static final int NOT_FOUND = -1;
    public static final int[] ARMOR_SLOTS = new int[]{0, 1, 2, 3};
    public static final int[] HELMET_SLOTS = new int[]{3};

    public final NonNullList<ItemStack> main;
    public final NonNullList<ItemStack> armor;
    public final NonNullList<ItemStack> offHand;
    private final List<NonNullList<ItemStack>> combinedInventory;
    private int changeCount;

    public KitInventory() {
        this.main = NonNullList.withSize(MAIN_SIZE, ItemStack.EMPTY);
        this.armor = NonNullList.withSize(ARMOR_SLOTS.length, ItemStack.EMPTY);
        this.offHand = NonNullList.withSize(1, ItemStack.EMPTY);
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
            && ItemStack.isSameItemSameComponents(existingStack, stack)
            && existingStack.isStackable()
            && existingStack.getCount() < this.getMaxStackSize(existingStack);
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
            if (!this.main.get(i).isEmpty() && ItemStack.isSameItemSameComponents(stack, this.main.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public static boolean usableWhenFillingSlot(ItemStack stack) {
        return !stack.isDamaged() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME);
    }

    public int indexOf(ItemStack stack) {
        for (int i = 0; i < this.main.size(); ++i) {
            ItemStack itemStack = this.main.get(i);
            if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, itemStack) && usableWhenFillingSlot(itemStack)) {
                return i;
            }
        }

        return -1;
    }

    public void setItem(int slot, ItemStack stack) {
        NonNullList<ItemStack> defaultedList = null;

        NonNullList<ItemStack> defaultedList2;
        for (java.util.Iterator<NonNullList<ItemStack>> combinedInventoryIterator = this.combinedInventory.iterator(); combinedInventoryIterator.hasNext(); slot -= defaultedList2.size()) {
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

    public void writeData(ValueOutput.TypedOutputList<ItemStackWithSlot> list) {
        for (int i = 0; i < this.main.size(); ++i) {
            ItemStack itemStack = this.main.get(i);
            if (!itemStack.isEmpty()) {
                list.add(new ItemStackWithSlot(i, itemStack));
            }
        }
    }

    private record ItemSlot(int slot, @Nullable ItemStack stack) { }

    public void readData(ValueInput.TypedInputList<ItemStackWithSlot> list) {
        this.main.clear();

        for (ItemStackWithSlot stackWithSlot : list) {
            if (stackWithSlot.isValidInContainer(this.main.size())) {
                this.setItem(stackWithSlot.slot(), stackWithSlot.stack());
            }
        }
    }

    public int getContainerSize() {
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

    public ItemStack getItem(int slot) {
        List<ItemStack> list = null;

        NonNullList<ItemStack> defaultedList;
        for (java.util.Iterator<NonNullList<ItemStack>> combinedInventoryIterator = this.combinedInventory.iterator(); combinedInventoryIterator.hasNext(); slot -= defaultedList.size()) {
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

    public void setChanged() {
        ++this.changeCount;
    }

    public int getChangeCount() {
        return this.changeCount;
    }

    public boolean contains(ItemStack stack) {
        for (NonNullList<ItemStack> itemStacks : this.combinedInventory) {
            for (ItemStack itemStack : itemStacks) {
                if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tag) {
        for (NonNullList<ItemStack> itemStacks : this.combinedInventory) {
            for (ItemStack itemStack : itemStacks) {
                if (!itemStack.isEmpty() && itemStack.is(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void copyFrom(Inventory other) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, other.getItem(i).copy());
        }
    }

    public void copyFrom(KitInventory other) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, other.getItem(i).copy());
        }
    }

    public void clearContent() {
        this.combinedInventory.forEach(List::clear);
    }

    public ItemStack removeItemNoUpdate(int slot) {
        NonNullList<ItemStack> defaultedList = null;

        NonNullList<ItemStack> defaultedList2;
        for (java.util.Iterator<NonNullList<ItemStack>> var3 = this.combinedInventory.iterator(); var3.hasNext(); slot -= defaultedList2.size()) {
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

    public ItemStack removeItem(int slot, int amount) {
        List<ItemStack> list = null;

        NonNullList<ItemStack> defaultedList;
        for (java.util.Iterator<NonNullList<ItemStack>> var4 = this.combinedInventory.iterator(); var4.hasNext(); slot -= defaultedList.size()) {
            defaultedList = var4.next();
            if (slot < defaultedList.size()) {
                list = defaultedList;
                break;
            }
        }

        return list != null && !list.get(slot).isEmpty() ? ContainerHelper.removeItem(list, slot, amount) : ItemStack.EMPTY;
    }

    public boolean stillValid(Player player) {
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
        if (this.canStackAddMore(this.getItem(OFF_HAND_SLOT), stack)) {
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
        ItemStack itemStack = this.getItem(slot);
        if (itemStack.isEmpty()) {
            itemStack = stack.copyWithCount(0);
            this.setItem(slot, itemStack);
        }

        int j = this.getMaxStackSize(itemStack) - itemStack.getCount();
        int k = Math.min(i, j);
        if (k == 0) {
            return i;
        } else {
            i -= k;
            itemStack.grow(k);
            itemStack.setPopTime(5);
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
                    this.main.set(slot, stack.copyAndClear());
                    this.main.get(slot).setPopTime(5);
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
            CrashReport crashReport = CrashReport.forThrowable(ex, "Adding item to inventory");
            CrashReportCategory crashReportSection = crashReport.addCategory("Item being added");
            crashReportSection.setDetail("Item ID", Item.getId(stack.getItem()));
            crashReportSection.setDetail("Item data", stack.getDamageValue());
            crashReportSection.setDetail("Item name", () -> stack.getHoverName().getString());
            throw new ReportedException(crashReport);
        }
    }

    public boolean insertStack(ItemStack stack) {
        return this.insertStack(-1, stack);
    }

    public void offerOrDropToPlayer(Inventory playerInventory) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            playerInventory.placeItemBackInInventory(this.getItem(i).copy(), Prediction.SERVER_ONLY);
        }
    }

}
