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
    private Map<String, String> ringSelections;
    private boolean hasReceivedStarterKit;

    public PlayerKitData(ServerPlayerEntity player, File saveFile) {
        super(player, saveFile);
        kitUsedTimes = new HashMap<>();
        ringSelections = new HashMap<>();
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

    public long getKitCooldownRemainingMs(String kitName, Kit kit, long timeMs) {
        var kitUsedTimeOpt = getKitUsedTime(kitName);
        var kitCooldownMs = kit.cooldownMs();
        if (kitUsedTimeOpt.isEmpty()) {
            // kit never used, can't be on any sort of cd, even if a one-time kit
            return 0;
        }
        if (kitCooldownMs == 0) {
            return 0;
        }
        if (kitCooldownMs < 0) {
            // kit has been used, and there is a negative cooldown, meaning it is a one-time kit
            return Long.MAX_VALUE;
        }
        // have a non-negative cd, so not a one-time kit. Do the cd math.
        return Math.max(0, (kitUsedTimeOpt.get() + kitCooldownMs) - timeMs);
    }

    public boolean isKitOnCooldownAtTime(Map.Entry<String, Kit> kit, long timeMs) {
        return getKitCooldownRemainingMs(kit.getKey(), kit.getValue(), timeMs) <= 0;
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
        // `data` is the pre-1.21.5 root key
        NbtCompound dataTag = nbtCompound.getCompound("data").orElse(nbtCompound);

        dataTag.getCompound("kitUsedTimes").ifPresent(kitUsedTimesNbt -> {
            for (String key : kitUsedTimesNbt.getKeys()) {
                this.kitUsedTimes.put(key, kitUsedTimesNbt.getLong(key).orElseThrow());
            }
        });

        this.hasReceivedStarterKit = dataTag.getBoolean("hasReceivedStarterKit").orElse(false);
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

    public void resetRingSelection(String ringName) {
        this.ringSelections.remove(ringName);
        this.markDirty();
    }

    public boolean mayClaimFromRing(String ringName, String kitName) {
        var ringChoice = this.ringSelections.get(ringName);
        return ringChoice != null && ringChoice.equals(kitName);
    }


    public void resetAllKits() {
        this.kitUsedTimes.clear();
        this.markDirty();
    }
}
