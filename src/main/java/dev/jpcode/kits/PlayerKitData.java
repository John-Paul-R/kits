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

    /**
     * a lookup of kit "cooldown keys" to used times
     */
    private final Map<String, Long> kitUsedTimes;
    /**
     * a lookup of kit ring name to 'selected kit name' -- particularly for
     * rings that lock the user in to their selection for future claims, once
     * that initial selection is made.
     */
    private final Map<String, String> ringSelections;
    private boolean hasReceivedStarterKit;

    public PlayerKitData(ServerPlayerEntity player, File saveFile) {
        super(player, saveFile);
        kitUsedTimes = new HashMap<>();
        ringSelections = new HashMap<>();
    }

    public void useKit(String kitName, String cooldownKey) {
        kitUsedTimes.put(cooldownKey, Util.getEpochTimeMs());
        markDirty();
        save(DynamicRegistryManager.EMPTY);
    }

    public Optional<Long> getKitUsedTime(String kitCooldownKey) {
        try {
            return Optional.of(kitUsedTimes.get(kitCooldownKey));
        } catch (NullPointerException notYetUsed) {
            return Optional.empty();
        }
    }

    public long getKitCooldownRemainingMs(KitsModStorage.KitRecord kit, long timeMs) {
        return getKitCooldownRemainingMs(kit.cooldownKey(), kit.cooldownMs(), timeMs);
    }

    public long getKitCooldownRemainingMs(Kit kit, long timeMs) {
        return getKitCooldownRemainingMs(kit.getCooldownTrackerKey(), kit.cooldownMs(), timeMs);
    }

    public long getKitCooldownRemainingMs(String kitCooldownKey, long kitCooldownMs, long timeMs) {
        var kitUsedTimeOpt = getKitUsedTime(kitCooldownKey);
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

    // this works for a type of ring where you may choose a "track" and may
    // only select that kit within the ring thereafter
    public boolean mayClaimFromRing(String ringName, String kitName) {
        var ringChoice = this.ringSelections.get(ringName);
        return ringChoice == null || ringChoice.equals(kitName);
    }

    public void resetAllKits() {
        this.kitUsedTimes.clear();
        this.markDirty();
    }

    public boolean canSeeKit(KitsModStorage.KitRecord kit) {
        return KitPerms.checkKit(this.getPlayer().getCommandSource(), kit);
    }

    public boolean mayClaim(KitsModStorage.KitRecord kit) {
        return canSeeKit(kit) && this.getKitCooldownRemainingMs(kit, Util.getEpochTimeMs()) > 0;
    }
}
