package dev.jpcode.kits.data;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;

public class NbtKitCooldownStorage implements KitCooldownStorageProvider, NbtStoragePart {
    private Map<String, Long> kitUsedTimes;

    public NbtKitCooldownStorage() {
        kitUsedTimes = new HashMap<>();
    }

    @Override
    public void useKit(String kitName) {
        kitUsedTimes.put(kitName, Util.getEpochTimeMs());
    }

    @Override
    public long getKitUsedTime(String kitName) {
        try {
            return kitUsedTimes.get(kitName);
        } catch (NullPointerException notYetUsed) {
            return 0;
        }
    }

    @Override
    public void resetKitCooldown(String kitName) {
        this.kitUsedTimes.remove(kitName);
    }

    @Override
    public void resetAllKits() {
        this.kitUsedTimes.clear();
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        NbtCompound kitUsedTimesNbt = nbt.getCompound("kitUsedTimes");
        for (String key : kitUsedTimesNbt.getKeys()) {
            this.kitUsedTimes.put(key, kitUsedTimesNbt.getLong(key));
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound kitUsedTimesNbt = new NbtCompound();
        kitUsedTimes.forEach(kitUsedTimesNbt::putLong);

        nbt.put("kitUsedTimes", kitUsedTimesNbt);

        return nbt;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        // ignore, we don't need it here
    }
}
