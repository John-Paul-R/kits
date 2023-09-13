package dev.jpcode.kits;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Function;

import eu.pb4.sgui.api.gui.SimpleGuiBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.command.KitClaimCommand;
import dev.jpcode.kits.command.KitCommandsManagerCommand;

import static dev.jpcode.kits.KitsMod.KIT_MAP;
import static dev.jpcode.kits.KitsMod.getAllKitsForPlayer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class KitsCommandRegistry {

    private KitsCommandRegistry() {
    }

    static int addKit(CommandContext<ServerCommandSource> context, String kitName, PlayerInventory sourceInventory, long cooldown) {
        var kitInventory = new KitInventory();
        kitInventory.copyFrom(sourceInventory);
        return addKit(context, kitName, new Kit(kitInventory, cooldown));
    }

    static int addKit(CommandContext<ServerCommandSource> context, String kitName, Kit kit) {
        KIT_MAP.put(kitName, kit);

        try {
            saveKit(kitName, kit);
            context.getSource().sendFeedback(
                Text.of(String.format("Kit '%s' created from current inventory.", kitName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public static void saveKit(String kitName, Kit kit) throws IOException {
        NbtCompound root = new NbtCompound();
        kit.writeNbt(root);

        NbtIo.write(
            root,
            KitsMod.getKitsDir().toPath().resolve(String.format("%s.nbt", kitName)).toFile()
        );
    }

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess commandRegistryAccess,
        CommandManager.RegistrationEnvironment registrationEnvironment) {
        CommandNode<ServerCommandSource> kitNode = dispatcher.register(literal("kit"));

        kitNode.addChild(literal("add")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .then(argument("cooldown", LongArgumentType.longArg(-1))
                    .executes(context -> addKit(
                        context,
                        StringArgumentType.getString(context, "kit_name"),
                        context.getSource().getPlayer().getInventory(),
                        LongArgumentType.getLong(context, "cooldown")
                    ))
                    .then(argument("time_unit", StringArgumentType.word())
                        .suggests(TimeUtil::suggestions)
                        .executes(context -> addKit(
                            context,
                            StringArgumentType.getString(context, "kit_name"),
                            context.getSource().getPlayer().getInventory(),
                            TimeUtil.parseToMillis(
                                LongArgumentType.getLong(context, "cooldown"),
                                StringArgumentType.getString(context, "time_unit"))
                        ))))
            ).build()
        );

        kitNode.addChild(literal("setDisplayItem")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitsMod::suggestionProvider)
                .then(argument("item", ItemStackArgumentType.itemStack(commandRegistryAccess))
                    .executes(context -> {
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        var item = ItemStackArgumentType.getItemStackArgument(context, "item");

                        var existingKit = KIT_MAP.get(kitName);
                        existingKit.setDisplayItem(item.getItem());
                        try {
                            saveKit(kitName, existingKit);
                        } catch (IOException e) {
                            throw new CommandException(Text.literal("Failed to save kit."));
                        }
                        return 0;
                    })
                )
            ).build()
        );

        kitNode.addChild(literal("claim")
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitsMod::suggestionProvider)
                .executes(new KitClaimCommand())
            ).build()
        );

        kitNode.addChild(literal("remove")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitsMod::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    KIT_MAP.remove(kitName);

                    try {
                        Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
                    } catch (IOException e) {
                        context.getSource().sendError(Text.of("Could not find kit file on disk."));
                        return -1;
                    }

                    context.getSource().sendFeedback(Text.of(String.format("Removed kit '%s'.", kitName)), true);

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("reload")
            .requires(Permissions.require("kits.manage", 4))
            .executes(context -> {
                KitsMod.reloadKits(context.getSource().getServer());
                return 1;
            }).build()
        );

        kitNode.addChild(literal("resetPlayerKit")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("players", EntityArgumentType.players())
                .then(argument("kit_name", StringArgumentType.word())
                    .suggests(KitsMod::suggestionProvider)
                    .executes(context -> {
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        var targetPlayers = EntityArgumentType.getPlayers(context, "players");

                        for (var player : targetPlayers) {
                            ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetKitCooldown(kitName);
                        }

                        context.getSource().sendFeedback(
                            Text.literal(String.format("Reset kit '%s' cooldown for %d players", kitName, targetPlayers.size())),
                            true);

                        return 1;
                    })
                )).build()
        );

        kitNode.addChild(literal("resetPlayer")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("players", EntityArgumentType.players())
                .executes(context -> {
                    var targetPlayers = EntityArgumentType.getPlayers(context, "players");
                    for (var player : targetPlayers) {
                        ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetAllKits();
                    }

                    context.getSource().sendFeedback(
                        Text.literal(String.format("Reset all kit cooldowns for %d players", targetPlayers.size())),
                        true);

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("commands")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitsMod::suggestionProvider)
                .then(literal("list")
                    .executes(KitCommandsManagerCommand::listCommandsForKit)
                )
                .then(literal("add")
                    .then(argument("command", StringArgumentType.greedyString())
                        .executes(KitCommandsManagerCommand::addCommandToKit)
                    )
                )
                .then(literal("remove")
                    .then(argument("command", StringArgumentType.greedyString())
                        .executes(KitCommandsManagerCommand::removeCommandFromKit)
                    )
                )
            )
            .build()
        );

        var kitsSguiBuilder = literal("kits")
            .executes(ctx -> {
                var player = ctx.getSource().getPlayerOrThrow();
                var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
                var allPlayerKits = getAllKitsForPlayer(player);

                long currentTime = Util.getEpochTimeMs();
                Function<Map.Entry<String, Kit>, Boolean> canUseKit = (entry) ->
                    (playerData.getKitUsedTime(entry.getKey()) + entry.getValue().cooldown()) - currentTime <= 0;

                var simpleGuiBuilder = new SimpleGuiBuilder(ScreenHandlerType.GENERIC_9X3, false);
                simpleGuiBuilder.setLockPlayerInventory(true);
                simpleGuiBuilder.setTitle(Text.literal("Claim Kit"));

                int i = 0;
                for (var kitEntry : allPlayerKits.toList()) {
                    var defaultItemStack = (canUseKit.apply(kitEntry)
                            ? kitEntry.getValue()
                                .displayItem()
                                .orElse(Items.EMERALD_BLOCK)
                            : Items.GRAY_CONCRETE_POWDER)
                        .getDefaultStack();

                    simpleGuiBuilder.setSlot(
                        i++,
                        createKitItemStack(kitEntry.getKey(), defaultItemStack),
                        (index, type, action, gui) -> {
                            if (type.isLeft) {
                                KitClaimCommand.exec(player, kitEntry.getKey());
                                gui.close();
                            }
                        });
                }

                var simpleGui = simpleGuiBuilder.build(player);
                simpleGui.open();

                return 0;
            });
        dispatcher.register(kitsSguiBuilder);
    }

    private static ItemStack createKitItemStack(String kitName, ItemStack itemStack) {
        return itemStack
            .copy()
            .setCustomName(Text.literal(kitName));
    }
}
