package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.command.ServerCommandSource;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Kits implements ModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger("kits");
    public static final Map<String, Kit> KIT_MAP = new HashMap<String, Kit>();
    private static File kitsDir;

    public static File getKitsDir() {
        return kitsDir;
    }

    @Override
    public void onInitialize()
    {
        LOGGER.info("Kits is getting ready...");

        KitPerms.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            kitsDir = server.getRunDirectory().toPath().resolve("config/kits").toFile();
            // if the dir was not just created, load all kits from dir.
            if (!kitsDir.mkdir()) {
                File[] kitFiles = kitsDir.listFiles();
                for (File kitFile : kitFiles) {
                    try {
                        LOGGER.info(String.format("Loading kit '%s'", kitFile.getName()));
                        NbtCompound kitNbt = NbtIo.read(kitFile);
                        PlayerInventory kitInventory = new PlayerInventory(null);

                        assert kitNbt != null;
                        kitInventory.readNbt(kitNbt.getList("inventory", NbtElement.COMPOUND_TYPE));
                        String fileName = kitFile.getName();
                        String kitName = fileName.substring(0, fileName.length() - 4);
                        KIT_MAP.put(kitName, new Kit(kitInventory, true, 60));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            KitsCommandRegistry.register(dispatcher, dedicated, KIT_MAP);
        });
    }

    /**
     * Suggests existing kits that the user has permissions for.
     *
     * @param context
     * @param builder
     * @return suggestions for existing kits that the user has permissions for.
     */
    public static CompletableFuture<Suggestions> suggestionProvider(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        ServerCommandSource source = context.getSource();
        return ListSuggestion.getSuggestionsBuilder(builder, KIT_MAP.keySet().stream().filter(kitName ->
            KitPerms.checkKit(source, kitName)
        ).toList());
    }

}
