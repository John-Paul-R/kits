package dev.jpcode.kits.datafixer;

import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.Maps;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.serialization.Dynamic;

public class KitRingDataFixer {
    public static final DSL.TypeReference TYPE = () -> "KitRing";

    private KitRingDataFixer() {}

    public static class KitRingSchema extends Schema {
        public KitRingSchema(int versionKey, Schema parent) {
            super(versionKey, parent);
        }

        @Override
        public void registerTypes(
            Schema schema,
            Map<String, Supplier<TypeTemplate>> entityTypes,
            Map<String, Supplier<TypeTemplate>> blockEntityTypes
        ) {
            schema.registerType(true, TYPE, DSL::remainder);
        }

        @Override
        public Map<String, Supplier<TypeTemplate>> registerEntities(Schema schema) {
            return Maps.newHashMap();
        }

        @Override
        public Map<String, Supplier<TypeTemplate>> registerBlockEntities(Schema schema) {
            return Maps.newHashMap();
        }
    }

    public static class FixItemStackFieldsFix extends DataFix {
        public FixItemStackFieldsFix(Schema outputSchema) {
            super(outputSchema, false);
        }

        @Override
        public TypeRewriteRule makeRule() {
            Type<?> inputType = getInputSchema().getType(TYPE);
            return fixTypeEverywhereTyped("KitRing ItemStack field migration", inputType,
                typed -> typed.update(DSL.remainderFinder(), KitRingDataFixer::fixKitRingData));
        }
    }

    public static DataFixerBuilder createDataFixer() {
        DataFixerBuilder builder = new DataFixerBuilder(2);

        // Schema 0-1: Original format with PascalCase ItemStack fields
        builder.addSchema(0, KitRingSchema::new);

        // Schema 2: New format with lowercase ItemStack fields
        Schema v2Schema = builder.addSchema(2, KitRingSchema::new);
        builder.addFixer(new FixItemStackFieldsFix(v2Schema));

        return builder;
    }

    private static <T> Dynamic<T> fixKitRingData(Dynamic<T> dynamic) {
        // Fix all kits in the kits map - only ItemStack fields need fixing
        return dynamic.update("kits", kitsCompound ->
            kitsCompound.updateMapValues(pair ->
                pair.mapSecond(KitRingDataFixer::fixKitData)
            )
        );
    }

    private static <T> Dynamic<T> fixKitData(Dynamic<T> dynamic) {
        // Fix inventory items - ItemStack fields changed from PascalCase to lowercase
        // StackWithSlot has flattened structure: {slot: N, Item: "...", Count: N} -> {slot: N, id: "...", count: N}
        return dynamic.update("inventory", inventoryList -> {
            var streamResult = inventoryList.asStreamOpt().result();
            if (streamResult.isPresent()) {
                return inventoryList.createList(streamResult.get().map(KitRingDataFixer::fixInventoryItem));
            }
            return inventoryList;
        });
    }

    private static <T> Dynamic<T> fixInventoryItem(Dynamic<T> dynamic) {
        // ItemStack field renames for codec compatibility (Item -> id, Count -> count)
        // Fields are flattened at the same level as 'slot', not nested
        return renameField(renameField(dynamic, "Item", "id"), "Count", "count");
    }

    private static <T> Dynamic<T> renameField(Dynamic<T> dynamic, String oldName, String newName) {
        return dynamic.get(oldName).result()
            .map(value -> dynamic.remove(oldName).set(newName, value))
            .orElse(dynamic);
    }
}
