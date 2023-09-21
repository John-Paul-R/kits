package dev.jpcode.kits.data;

import net.minecraft.nbt.NbtCompound;

public interface NbtStoragePart {
    void fromNbt(NbtCompound nbt);

    NbtCompound writeNbt(NbtCompound nbt);
}
