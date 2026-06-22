package dev.jpcode.kits;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

public class PlayerKitData extends PlayerData {

    /** A lookup of kit "cooldown keys" to used times. */
    private final Map<String, Long> kitUsedTimes;
    /**
     * a lookup of kit ring name to 'selected kit name' -- particularly for
     * rings that lock the user in to their selection for future claims, once
     * that initial selection is made.
     */
    private final Map<String, String> ringSelections;
    private boolean hasReceivedStarterKit;

    public PlayerKitData(ServerPlayer player, File saveFile) {
        super(player, saveFile);
        kitUsedTimes = new HashMap<>();
        ringSelections = new HashMap<>();
    }

    public void useKit(String kitName, String cooldownKey) {
        kitUsedTimes.put(cooldownKey, Util.getEpochMillis());
        setDirty();
        save(RegistryAccess.EMPTY);
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
    public CompoundTag writeNbt(CompoundTag nbt, HolderLookup.Provider wrapperLookup) {

        CompoundTag kitUsedTimesNbt = new CompoundTag();
        kitUsedTimes.forEach(kitUsedTimesNbt::putLong);

        nbt.put("kitUsedTimes", kitUsedTimesNbt);

        CompoundTag ringSelectionsNbt = new CompoundTag();
        ringSelections.forEach(ringSelectionsNbt::putString);

        nbt.put("ringSelections", ringSelectionsNbt);

        nbt.putBoolean("hasReceivedStarterKit", this.hasReceivedStarterKit);

        return nbt;
    }

    @Override
    public void fromNbt(CompoundTag nbtCompound) {
        // `data` is the pre-1.21.5 root key
        CompoundTag dataTag = nbtCompound.getCompound("data").orElse(nbtCompound);

        dataTag.getCompound("kitUsedTimes").ifPresent(kitUsedTimesNbt -> {
            for (String key : kitUsedTimesNbt.keySet()) {
                this.kitUsedTimes.put(key, kitUsedTimesNbt.getLong(key).orElseThrow());
            }
        });

        dataTag.getCompound("ringSelections").ifPresent(ringSelectionsNbt -> {
            for (String key : ringSelectionsNbt.keySet()) {
                this.ringSelections.put(key, ringSelectionsNbt.getString(key).orElseThrow());
            }
        });

        this.hasReceivedStarterKit = dataTag.getBoolean("hasReceivedStarterKit").orElse(false);
    }

    public boolean hasReceivedStarterKit() {
        return hasReceivedStarterKit;
    }

    public void setHasReceivedStarterKit(boolean hasReceivedStarterKit) {
        this.hasReceivedStarterKit = hasReceivedStarterKit;
        this.setDirty();
        this.save(RegistryAccess.EMPTY);
    }

    public void resetKitCooldown(String kitName) {
        this.kitUsedTimes.remove(kitName);
        this.setDirty();
    }

    public void resetRingChoice(String ringName) {
        this.ringSelections.remove(ringName);
        this.setDirty();
    }

    public String getRingChoice(String ringName) {
        return this.ringSelections.get(ringName);
    }

    // this works for a type of ring where you may choose a "track" and may
    // only select that kit within the ring thereafter
    public boolean mayClaimFromRing(String ringName, String kitName) {
        var ringChoice = getRingChoice(ringName);
        return ringChoice == null || ringChoice.equals(kitName);
    }

    public void recordRingSelection(String ringName, String kitName) {
        this.ringSelections.put(ringName, kitName);
        this.setDirty();
        this.save(RegistryAccess.EMPTY);
    }

    public void resetAllKits() {
        this.kitUsedTimes.clear();
        this.setDirty();
    }

    public boolean canSeeKit(KitsModStorage.KitRecord kit) {
        return KitPerms.checkKit(this.getPlayer().createCommandSourceStack(), kit);
    }

    public boolean mayClaim(KitsModStorage.KitRecord kit) {
        return canSeeKit(kit) && this.getKitCooldownRemainingMs(kit, Util.getEpochMillis()) > 0;
    }
}
