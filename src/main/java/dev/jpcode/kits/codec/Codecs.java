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
     * Codec for a map of kits.
     * Ensures mutable HashMap on decode.
     */
    public static final Codec<HashMap<String, Kit>> KITS_MAP =
        Codec.unboundedMap(Codec.STRING, Kit.CODEC)
            .xmap(HashMap::new, map -> map); // Ensure mutable HashMap

    /**
     * Codec for ring metadata (stored in _ring.json).
     * Does NOT include kits field - kits are stored as separate files in the ring directory.
     * Use RING_METADATA_WITH_KITS for loading legacy .ring.json files.
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

            // Commands list
            Codec.STRING.listOf()
                .xmap(ArrayList::new, list -> list)
                .optionalFieldOf(KitRing.StorageKey.COMMANDS, new ArrayList<>())
                .forGetter(KitRing::commands),

            // Permanent choice
            Codec.BOOL
                .optionalFieldOf(KitRing.StorageKey.PERMANENT_CHOICE, false)
                .forGetter(KitRing::permanentChoice)

        ).apply(instance, (displayName, cooldown, displayItem, commands, permanentChoice) ->
            KitRing.createWithData(displayName, cooldown, displayItem, Optional.empty(), commands, permanentChoice)
        )
    );

    /**
     * Codec for loading legacy .ring.json files that include embedded kits.
     * Used only for migration from old format.
     */
    public static final Codec<KitRing> RING_METADATA_WITH_KITS = RecordCodecBuilder.create(instance ->
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

            // Kits map (for loading old format)
            KITS_MAP
                .optionalFieldOf(KitRing.StorageKey.KITS)
                .forGetter(ring -> Optional.of(ring.kits())),

            // Commands list
            Codec.STRING.listOf()
                .xmap(ArrayList::new, list -> list)
                .optionalFieldOf(KitRing.StorageKey.COMMANDS, new ArrayList<>())
                .forGetter(KitRing::commands),

            // Permanent choice
            Codec.BOOL
                .optionalFieldOf(KitRing.StorageKey.PERMANENT_CHOICE, false)
                .forGetter(KitRing::permanentChoice)

        ).apply(instance, KitRing::createWithData)
    );
}
