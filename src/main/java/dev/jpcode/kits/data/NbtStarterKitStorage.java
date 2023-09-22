package dev.jpcode.kits.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

public class NbtStarterKitStorage implements StarterKitStorageProvider, NbtStoragePart {
    private boolean hasReceivedStarterKit;

    @Override
    public boolean hasReceivedStarterKit() {
        return hasReceivedStarterKit;
    }

    @Override
    public void setHasReceivedStarterKit(boolean hasReceivedStarterKit) {
        this.hasReceivedStarterKit = hasReceivedStarterKit;
    }

    @Override
    public void fromNbt(NbtCompound nbt) {

    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("hasReceivedStarterKit", this.hasReceivedStarterKit);
        return nbt;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        // ignore, we don't need it here
    }
}
