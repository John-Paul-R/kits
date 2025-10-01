package dev.jpcode.kits.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.registry.Registries;
import net.minecraft.text.TextCodecs;

import dev.jpcode.kits.Kit;
import dev.jpcode.kits.KitRing;

public final class Codecs {
    private Codecs() {}

    /**
     * Codec for ring metadata (stored in _ring.json).
     * Kits field is optional - omitted when empty (for new directory format),
     * included when present (for loading old .ring.json format during migration).
     */
    public static final Codec<KitRing> RING_METADATA = RecordCodecBuilder.create(instance ->
        instance.group(
            // Display name
            TextCodecs.CODEC
                .optionalFieldOf(KitRing.StorageKey.DISPLAY_NAME)
                .forGetter(ring -> Optional.ofNullable(ring.displayName())),

            // Cooldown
            Codec.LONG
                .fieldOf(KitRing.StorageKey.COOLDOWN)
                .forGetter(KitRing::cooldownMs),

            // Display item
            Registries.ITEM.getCodec()
                .optionalFieldOf(KitRing.StorageKey.DISPLAY_ITEM)
                .forGetter(KitRing::displayItem),

            // Kits map (optional, for lo    private void paintKitsScreen(ServerPlayerEntity player, SimpleGuiBuilder simpleGuiBuilder, PlayerKitData playerData, long currentTime) {                .map(Map.Entry::getKey)CUSTOM_NAMEkitRecordading old format)
            Codec.unboundedMap(Codec.STRING, Kit.CODEC)
                .xmap(HashMap::new, map -> map) // Ensure mutable HashMap
                .optionalFieldOf(KitRing.StorageKey.KITS)
                .forGetter(ring -> ring.kits().isEmpty()
                    ? Optional.empty()
                    : Optional.of(ring.kits())
                ),

            // Commands list
            Codec.STRING.listOf()
                .xmap(ArrayList::new, list -> list)
                .optionalFieldOf(KitRing.StorageKey.COMMANDS, new ArrayList<>())
                .forGetter(KitRing::commands)

        ).apply(instance, KitRing::createWithData)
    );
}
