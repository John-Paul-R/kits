package dev.jpcode.kits;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import me.lucko.fabric.api.permissions.v0.Permissions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static dev.jpcode.kits.InventoryUtil.addAllCopies;
import static dev.jpcode.kits.InventoryUtil.offerAllCopies;

public final class KitsCommandRegistry {

    private KitsCommandRegistry() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated, Map<String, Kit> kitMap) {
        CommandNode<ServerCommandSource> kitNode = dispatcher.register(CommandManager.literal("kit"));
        kitNode.addChild(CommandManager.literal("add")
            .requires(Permissions.require("kits.manage"))
            .then(CommandManager.argument("kit_name", StringArgumentType.word())
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    PlayerInventory kitInventory = new PlayerInventory(null);
                    addAllCopies(context.getSource().getPlayer().getInventory(), kitInventory);
                    NbtList inventoryNbt = kitInventory.writeNbt(new NbtList());
                    kitMap.put(kitName, new Kit(kitInventory, true, 60));
                    NbtCompound root = new NbtCompound();
                    root.put("inventory", inventoryNbt);
                    try {
                        NbtIo.write(
                            root,
                            Kits.getKitsDir().toPath().resolve(String.format("%s.nbt", kitName)).toFile()
                        );
                        context.getSource().sendFeedback(
                            Text.of(String.format("Kit '%s' created from current inventory.", kitName)),
                            true
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return 0;
                })
            ).build()
        );

        kitNode.addChild(CommandManager.literal("claim")
            .then(CommandManager.argument("kit_name", StringArgumentType.word())
                .suggests(Kits::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    if (!KitPerms.checkKit(context.getSource(), kitName)) {
                        context.getSource().sendError(Text.of("Insufficient permissions for specified kit."));
                        return -1;
                    }

                    Kit kit = kitMap.get(kitName);
                    PlayerInventory playerInventory = context.getSource().getPlayer().getInventory();
                    offerAllCopies(kit.inventory(), playerInventory);

                    return 0;
                })
            ).build()
        );

        kitNode.addChild(CommandManager.literal("remove")
            .requires(Permissions.require("kits.manage"))
            .then(CommandManager.argument("kit_name", StringArgumentType.word())
                .suggests(Kits::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    kitMap.remove(kitName);

                    try {
                        Files.delete(Kits.getKitsDir().toPath().resolve(kitName + ".nbt"));
                    } catch (IOException e) {
                        context.getSource().sendError(Text.of("Could not find kit file on disk."));
                        return -1;
                    }

                    context.getSource().sendFeedback(Text.of(String.format("Removed kit '%s'.", kitName)), true);

                    return 0;
                })
            ).build()
        );
    }
}
