package dev.jpcode.kits;

import java.io.IOException;
import java.util.Map;

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

public class KitsCommandRegistry {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated, Map<String, PlayerInventory> kitMap) {
        CommandNode<ServerCommandSource> kitNode = dispatcher.register(CommandManager.literal("kit"));
        kitNode.addChild(CommandManager.literal("add")
            .requires(source -> source.hasPermissionLevel(3))
            .then(CommandManager.argument("kit_name", StringArgumentType.word())
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    PlayerInventory kitInventory = new PlayerInventory(null);
                    addAllCopies(context.getSource().getPlayer().getInventory(), kitInventory);
                    NbtList inventoryNbt = kitInventory.writeNbt(new NbtList());
                    kitMap.put(kitName, kitInventory);
                    NbtCompound root = new NbtCompound();
                    root.put("inventory", inventoryNbt);
                    try {
                        NbtIo.write(
                            root,
                            context.getSource().getMinecraftServer().getRunDirectory().toPath().resolve(String.format("config/kits/%s.nbt", kitName)).toFile()
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

                    PlayerInventory kit = kitMap.get(kitName);
                    PlayerInventory playerInventory = context.getSource().getPlayer().getInventory();
                    offerAllCopies(kit, playerInventory);

                    return 0;
                })
            ).build()
        );
        kitNode.addChild(CommandManager.literal("remove")
            .then(CommandManager.argument("kit_name", StringArgumentType.word())
                .suggests(Kits::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");

                    PlayerInventory kit = kitMap.get(kitName);
                    PlayerInventory playerInventory = context.getSource().getPlayer().getInventory();
                    offerAllCopies(kit, playerInventory);

                    return 0;
                })
            ).build()
        );
    }
}
