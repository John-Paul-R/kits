package dev.jpcode.kits.data;

import java.io.File;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerKitUsageData extends PlayerData implements KitCooldownStorageProvider, StarterKitStorageProvider {
    private final StarterKitStorageProvider starterKitStorageProvider;
    private final KitCooldownStorageProvider kitCooldownStorageProvider;

    public PlayerKitUsageData(ServerPlayerEntity player, File saveFile, StarterKitStorageProvider starterKitStorageProvider, KitCooldownStorageProvider kitCooldownStorageProvider) {
        super(player, saveFile);
        this.starterKitStorageProvider = starterKitStorageProvider;
        this.kitCooldownStorageProvider = kitCooldownStorageProvider;
    }

    @Override
    public boolean hasReceivedStarterKit() {
        return starterKitStorageProvider.hasReceivedStarterKit();
    }

    @Override
    public void setHasReceivedStarterKit(boolean hasReceivedStarterKit) {
        starterKitStorageProvider.setHasReceivedStarterKit(hasReceivedStarterKit);
    }

    @Override
    public void useKit(String kitName) {
        markDirty();
        kitCooldownStorageProvider.useKit(kitName);
    }

    @Override
    public long getKitUsedTime(String kitName) {
        return kitCooldownStorageProvider.getKitUsedTime(kitName);
    }

    @Override
    public void resetKitCooldown(String kitName) {
        markDirty();
        kitCooldownStorageProvider.resetKitCooldown(kitName);
    }

    @Override
    public void resetAllKits() {
        markDirty();
        kitCooldownStorageProvider.resetAllKits();
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        nbt = nbt.getCompound("data");
        if (starterKitStorageProvider instanceof NbtStoragePart nbtStarterKitStorage)
            nbtStarterKitStorage.fromNbt(nbt);
        if (kitCooldownStorageProvider instanceof NbtStoragePart nbtCooldownStorage)
            nbtCooldownStorage.fromNbt(nbt);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (starterKitStorageProvider instanceof NbtStoragePart nbtStarterKitStorage)
            nbtStarterKitStorage.writeNbt(nbt);
        if (kitCooldownStorageProvider instanceof NbtStoragePart nbtCooldownStorage)
            nbtCooldownStorage.writeNbt(nbt);
        return nbt;
    }

    @Override
    public void save() {
        if (starterKitStorageProvider instanceof NbtStoragePart
            || kitCooldownStorageProvider instanceof NbtStoragePart)
            super.save();
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        super.setPlayer(player);
        starterKitStorageProvider.setPlayer(player);
        kitCooldownStorageProvider.setPlayer(player);
    }
}
