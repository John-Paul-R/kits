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

    public static final Codec<KitRing> KIT_RING = RecordCodecBuilder.create(instance ->
        instance.group(
            // Display name
            TextCodecs.CODEC
                .optionalFieldOf("display_name")
                .forGetter(ring -> Optional.ofNullable(ring.displayName())),

            // Cooldown
            Codec.LONG
                .fieldOf("cooldown")
                .forGetter(KitRing::cooldownMs),

            // Display item
            Registries.ITEM.getCodec()
                .optionalFieldOf("display_item")
                .forGetter(KitRing::displayItem),

            // Kits map
            Codec.unboundedMap(Codec.STRING, Kit.CODEC)
                .optionalFieldOf("kits", new HashMap<>())
                .forGetter(KitRing::kits),

            // Commands list
            Codec.STRING.listOf()
                .xmap(ArrayList::new, list -> list)
                .optionalFieldOf("commands", new ArrayList<>())
                .forGetter(KitRing::commands)

        ).apply(instance, KitRing::createWithData)
    );
}
