package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class KitRing {
    private Text displayName;
    private @Nullable Item displayItem;
    private final HashMap<String, Kit> kits;
    private final ArrayList<String> commands;
    /** a negative cooldown yields a one-time use kit ring */
    private final long cooldownMs;

    public KitRing(
        Text displayName,
        long cooldownMs,
        @Nullable Item displayItem,
        HashMap<String, Kit> kits,
        ArrayList<String> commands
    ) {
        this.displayName = displayName;
        this.cooldownMs = cooldownMs;
        this.displayItem = displayItem;
        this.kits = kits;
        this.commands = commands;
    }

    public Text displayName() {
        return displayName;
    }

    public void setDisplayName(Text displayName) {
        this.displayName = displayName;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public Optional<Item> displayItem() {
        return Optional.ofNullable(displayItem);
    }

    public void setDisplayItem(@Nullable Item displayItem) {
        this.displayItem = displayItem;
    }

    /** return is mutable internal ref */
    public HashMap<String, Kit> kits() {
        return kits;
    }

    /** return is mutable internal ref */
    public ArrayList<String> commands() {
        return commands;
    }

    public boolean addCommand(String command) {
        if (commands.contains(command)) return false;
        this.commands.add(command);
        return true;
    }

    public boolean removeCommand(String command) {
        if (commands().isEmpty() || !commands.contains(command)) return false;
        else commands.remove(command);
        return true;
    }

    public void addKit(String kitName, Kit kit) {
        this.kits.put(kitName, kit);
    }

    public @Nullable Kit removeKit(String kitName) {
        return this.kits.remove(kitName);
    }

    // STORAGE
    private static final class StorageKey {
        public static final String SCHEMA_VERSION = "_schema_version";
        public static final String COOLDOWN = "cooldown";
        public static final String DISPLAY_NAME = "display_name";
        public static final String DISPLAY_ITEM = "display_item";
        public static final String COMMANDS = "commands";
        public static final String KITS = "kits";
    }

    public void writeNbt(NbtCompound root, RegistryWrapper.WrapperLookup registries) {
        root.putInt(KitRing.StorageKey.SCHEMA_VERSION, 1);
        root.putLong(KitRing.StorageKey.COOLDOWN, this.cooldownMs());

        if (this.displayName() != null) {
            root.putString(
                KitRing.StorageKey.DISPLAY_NAME,
                Text.Serialization.toJsonString(this.displayName(), registries)
            );
        }

        if (this.displayItem().isPresent()) {
            root.putString(
                KitRing.StorageKey.DISPLAY_ITEM,
                Registries.ITEM.getKey(this.displayItem().get())
                    .get().getValue().toString()
            );
        }

        if (!commands.isEmpty()) {
            NbtList list = new NbtList();
            for (String command : commands) {
                list.add(NbtString.of(command));
            }
            root.put(KitRing.StorageKey.COMMANDS, list);
        }

        if (!kits.isEmpty()) {
            var kitsNbt = new NbtCompound();
            for (var kitEntry : kits.entrySet()) {
                var kitNbt = new NbtCompound();
                kitEntry.getValue().writeNbt(kitNbt, registries);
                kitsNbt.put(kitEntry.getKey(), kitNbt);
            }
            root.put(KitRing.StorageKey.KITS, kitsNbt);
        }
    }

    private static void handleReadVersion(@NotNull NbtCompound kitNbt) {

        if (!kitNbt.contains(KitRing.StorageKey.SCHEMA_VERSION)) {
            // Upgrade version nil to v1
            // Negative cooldowns replaced with 0, since that was the old behavior
            var cd = kitNbt.getLong(KitRing.StorageKey.COOLDOWN).orElse(0L);
            if (cd < 0) {
                kitNbt.putLong(KitRing.StorageKey.COOLDOWN, 0);
            }

            return;
        }
        var version = kitNbt.getInt(KitRing.StorageKey.SCHEMA_VERSION);
        // other version handling...
    }

    public static KitRing fromNbt(NbtCompound ringNbt, RegistryWrapper.WrapperLookup registries) {
        assert ringNbt != null;
        handleReadVersion(ringNbt);

        long ringCooldown = ringNbt.getLong(KitRing.StorageKey.COOLDOWN).orElse(0L);

        var ringDisplayName = ringNbt
            .getString(StorageKey.DISPLAY_NAME)
            .map(j -> Text.Serialization.fromLenientJson(j, registries))
            .orElse(null);

        var ringDisplayItem = ringNbt.getString(KitRing.StorageKey.DISPLAY_ITEM)
            .map(Identifier::of)
            .map(Registries.ITEM::get)
            .orElse(null);

        ArrayList<String> commands = ringNbt.getList(KitRing.StorageKey.COMMANDS)
            .map(l -> new ArrayList<>(ringNbt.getList(KitRing.StorageKey.COMMANDS).orElseThrow().stream().map(e -> e.asString().orElseThrow()).toList()))
            .orElseGet(ArrayList::new);

        HashMap<String, Kit> kits = ringNbt
            .getCompound(StorageKey.KITS)
            .map(kitsNbt -> {
                var map = new HashMap<String, Kit>();
                kitsNbt.entrySet()
                    .forEach(kitEntry -> {
                        var kit = kitEntry.getValue()
                            .asCompound()
                            .map(nbt -> Kit.fromNbt(nbt, registries))
                            .orElseThrow();

                        kit.setCooldownMs(ringCooldown);

                        map.put(
                            kitEntry.getKey(),
                            kit
                        );
                    });
                return map;
            })
            .orElseGet(HashMap::new);

        return new KitRing(ringDisplayName, ringCooldown, ringDisplayItem, kits, commands);
    }
}
