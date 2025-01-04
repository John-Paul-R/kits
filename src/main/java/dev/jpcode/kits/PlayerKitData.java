package dev.jpcode.kits;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;

public class PlayerKitData extends PlayerData {

    private Map<String, Long> kitUsedTimes;
    private boolean hasReceivedStarterKit;

    public PlayerKitData(ServerPlayerEntity player, File saveFile) {
        super(player, saveFile);
        kitUsedTimes = new HashMap<>();
    }

    public void useKit(String kitName) {
        kitUsedTimes.put(kitName, Util.getEpochTimeMs());
        markDirty();
        save(DynamicRegistryManager.EMPTY);
    }

    public Optional<Long> getKitUsedTime(String kitName) {
        try {
            return Optional.of(kitUsedTimes.get(kitName));
        } catch (NullPointerException notYetUsed) {
            return Optional.empty();
        }
    }

    public boolean isKitOnCooldownAtTime(Map.Entry<String, Kit> kit, long timeMs) {
        var kitUsedTimeOpt = getKitUsedTime(kit.getKey());
        var kitCooldownMs = kit.getValue().cooldownMs();
        return
            // kit never used, can't be on any sort of cd, even if a one-time kit
            kitUsedTimeOpt.isEmpty()
                // have a non-negative cd, so not a one-time kit. Do the cd math.
                || (kitCooldownMs >= 0 && (kitUsedTimeOpt.get() + kitCooldownMs) - timeMs <= 0);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {

        NbtCompound kitUsedTimesNbt = new NbtCompound();
        kitUsedTimes.forEach(kitUsedTimesNbt::putLong);

        nbt.put("kitUsedTimes", kitUsedTimesNbt);

        nbt.putBoolean("hasReceivedStarterKit", this.hasReceivedStarterKit);

        return nbt;
    }

    @Override
    public void fromNbt(NbtCompound nbtCompound) {
        NbtCompound dataTag = nbtCompound.getCompound("data");
        NbtCompound kitUsedTimesNbt = dataTag.getCompound("kitUsedTimes");
        for (String key : kitUsedTimesNbt.getKeys()) {
            this.kitUsedTimes.put(key, kitUsedTimesNbt.getLong(key));
        }
        this.hasReceivedStarterKit = dataTag.getBoolean("hasReceivedStarterKit");
    }

    public boolean hasReceivedStarterKit() {
        return hasReceivedStarterKit;
    }

    public void setHasReceivedStarterKit(boolean hasReceivedStarterKit) {
        this.hasReceivedStarterKit = hasReceivedStarterKit;
        this.markDirty();
        this.save(DynamicRegistryManager.EMPTY);
    }

    public void resetKitCooldown(String kitName) {
        this.kitUsedTimes.remove(kitName);
        this.markDirty();
    }

    public void resetAllKits() {
        this.kitUsedTimes.clear();
        this.markDirty();
    }
}
