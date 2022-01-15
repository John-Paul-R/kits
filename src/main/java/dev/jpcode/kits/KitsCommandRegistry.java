package dev.jpcode.kits;

import java.io.IOException;
import java.nio.file.Files;

import me.lucko.fabric.api.permissions.v0.Permissions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.InventoryUtil.offerAllCopies;
import static dev.jpcode.kits.KitsMod.KIT_MAP;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class KitsCommandRegistry {

    private KitsCommandRegistry() {}

    static int addKit(CommandContext<ServerCommandSource> context, String kitName, PlayerInventory sourceInventory, long cooldown) {
        var kitInventory = new KitInventory();
        kitInventory.copyFrom(sourceInventory);
        return addKit(context, kitName, new Kit(kitInventory, cooldown));
    }

    static int addKit(CommandContext<ServerCommandSource> context, String kitName, Kit kit) {
        KIT_MAP.put(kitName, kit);

        NbtCompound root = new NbtCompound();
        root.put("inventory", kit.inventory().writeNbt(new NbtList()));
        root.putLong("cooldown", kit.cooldown());
        try {
            NbtIo.write(
                root,
                KitsMod.getKitsDir().toPath().resolve(String.format("%s.nbt", kitName)).toFile()
            );
            context.getSource().sendFeedback(
                Text.of(String.format("Kit '%s' created from current inventory.", kitName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated) {
        CommandNode<ServerCommandSource> kitNode = dispatcher.register(literal("kit"));

        kitNode.addChild(literal("add")
            .requires(Permissions.require("kits.manage"))
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

        kitNode.addChild(literal("claim")
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitsMod::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");

                    PlayerKitData playerData = ((ServerPlayerEntityAccess) context.getSource().getPlayer()).kits$getPlayerData();
                    Kit kit = KIT_MAP.get(kitName);
                    long currentTime = Util.getEpochTimeMs();
                    long remainingTime = (playerData.getKitUsedTime(kitName) + kit.cooldown()) - currentTime;

                    if (!KitPerms.checkKit(context.getSource(), kitName)) {
                        context.getSource().sendError(Text.of(String.format(
                            "Insufficient permissions for kit '%s'.",
                            kitName)));
                        return -1;
                    } else if (remainingTime > 0) {
                        context.getSource().sendError(Text.of(
                            String.format(
                                "Kit '%s' is on cooldown. %s remaining.",
                                kitName,
                                TimeUtil.formatTime(remainingTime)
                        )));
                        return -2;
                    }

                    PlayerInventory playerInventory = context.getSource().getPlayer().getInventory();
                    playerData.useKit(kitName);
                    offerAllCopies(kit.inventory(), playerInventory);

                    context.getSource().sendFeedback(
                        Text.of(String.format("Successfully claimed kit '%s'!", kitName)),
                        context.getSource().getServer().shouldBroadcastConsoleToOps()
                    );

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("remove")
            .requires(Permissions.require("kits.manage"))
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
            .requires(Permissions.require("kits.manage"))
            .executes(context -> {
                KitsMod.reloadKits(context.getSource().getServer());
                return 1;
            }).build()
        );

        kitNode.addChild(literal("resetPlayerKit")
            .requires(Permissions.require("kits.manage"))
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
                            new LiteralText(String.format("Reset kit '%s' cooldown for %d players", kitName, targetPlayers.size())),
                            true);

                        return 1;
                    })
                )).build()
        );

        kitNode.addChild(literal("resetPlayer")
            .requires(Permissions.require("kits.manage"))
            .then(argument("players", EntityArgumentType.players())
                .executes(context -> {
                    var targetPlayers = EntityArgumentType.getPlayers(context, "players");
                    for (var player : targetPlayers) {
                        ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetAllKits();
                    }

                    context.getSource().sendFeedback(
                        new LiteralText(String.format("Reset all kit cooldowns for %d players", targetPlayers.size())),
                        true);

                    return 1;
                })
            ).build()
        );

    }
}
