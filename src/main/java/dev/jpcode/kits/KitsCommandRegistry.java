package dev.jpcode.kits;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

import me.lucko.fabric.api.permissions.v0.Permissions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.command.KitClaimCommand;
import dev.jpcode.kits.command.KitCommandsManagerCommand;
import dev.jpcode.kits.command.KitsCommand;

import static dev.jpcode.kits.KitsMod.KIT_MAP;
import static dev.jpcode.kits.KitsMod.KIT_RING_MAP;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class KitsCommandRegistry {

    private KitsCommandRegistry() {
    }

    /**
     * @param cooldownMs if negative, creates a one-time use kit
     */
    static int addKit(CommandContext<ServerCommandSource> context, String kitName, PlayerInventory sourceInventory, long cooldownMs) {
        var kitInventory = new KitInventory();
        kitInventory.copyFrom(sourceInventory);
        return addKit(context, kitName, new Kit(kitInventory, cooldownMs));
    }

    static int addKit(CommandContext<ServerCommandSource> context, String kitName, Kit kit) {
        KIT_MAP.put(kitName, kit);

        try {
            saveKit(kitName, kit, context.getSource().getWorld());
            context.getSource().sendFeedback(() ->
                Text.of(String.format("Kit '%s' created from current inventory.", kitName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public static void saveKit(String kitName, Kit kit, World world) throws IOException {
        var nbt = kit.toNbt(world);

        NbtIo.write(
            nbt,
            KitsMod.getKitsDir().toPath().resolve(String.format("%s.nbt", kitName)).toFile().toPath()
        );
    }

    static int addKitRing(CommandContext<ServerCommandSource> context, String ringName, KitRing ring) {
        KIT_RING_MAP.put(ringName, ring);

        try {
            saveKitRing(ringName, ring, context.getSource().getWorld());
            context.getSource().sendFeedback(() ->
                    Text.of(String.format("Kit Ring '%s' created.", ringName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    static int addKitToRing(CommandContext<ServerCommandSource> context, String ringName, String kitName)
        throws KitCommandSyntaxException
    {
        var ring = KIT_RING_MAP.get(ringName);
        if (ring == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit ring '%s' not found".formatted(ringName)
            ));
        }

        var kit = KIT_MAP.remove(kitName);
        if (kit == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit '%s' not found".formatted(kitName)
            ));
        }

        ring.addKit(kitName, kit);

        try {
            saveKitRing(ringName, ring, context.getSource().getWorld());
            KIT_MAP.remove(kitName);
            Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
            context.getSource().sendFeedback(() ->
                    Text.of(String.format("Kit '%s' added to Ring '%s' created.", kitName, ringName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    static int removeKitFromRing(CommandContext<ServerCommandSource> context, String ringName, String kitName)
        throws KitCommandSyntaxException
    {
        var ring = KIT_RING_MAP.get(ringName);
        if (ring == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit Ring '%s' not found".formatted(ringName)
            ));
        }

        var removed = ring.removeKit(kitName);

        if (removed == null) {
            throw new KitCommandSyntaxException(Text.literal(
                "Kit '%s' not found in Ring '%s'".formatted(kitName, ringName)
            ));
        }

        try {
            saveKitRing(ringName, ring, context.getSource().getWorld());
            context.getSource().sendFeedback(() ->
                    Text.of(String.format("Kit '%s' removed from Ring '%s'.", kitName, ringName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public static void saveKitRing(String kitName, KitRing ring, World world) throws IOException {
        NbtCompound root = new NbtCompound();
        ring.writeNbt(root, world);

        NbtIo.write(
            root,
            KitsMod.getKitsDir().toPath().resolve(String.format("%s.ring.nbt", kitName)).toFile().toPath()
        );
    }

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess commandRegistryAccess,
        CommandManager.RegistrationEnvironment registrationEnvironment
    ) {
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
                .suggests(KitSuggestions::suggestionProvider)
                .then(argument("item", ItemStackArgumentType.itemStack(commandRegistryAccess))
                    .executes(context -> {
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        var item = ItemStackArgumentType.getItemStackArgument(context, "item");

                        var existingKit = KIT_MAP.get(kitName);
                        existingKit.setDisplayItem(item.getItem());
                        try {
                            saveKit(kitName, existingKit, context.getSource().getWorld());
                        } catch (IOException e) {
                            throw new KitCommandSyntaxException(Text.literal("Failed to save kit."));
                        }
                        return 0;
                    })
                )
            ).build()
        );

        kitNode.addChild(literal("claim")
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitSuggestions::suggestionProvider)
                .executes(new KitClaimCommand())
            ).build()
        );

        kitNode.addChild(literal("remove")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitSuggestions::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    KIT_MAP.remove(kitName);

                    try {
                        Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
                    } catch (IOException e) {
                        context.getSource().sendError(Text.of("Could not find kit file on disk."));
                        return -1;
                    }

                    context.getSource().sendFeedback(() -> Text.of(String.format("Removed kit '%s'.", kitName)), true);

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
                    .suggests(KitSuggestions::suggestionProvider)
                    .executes(context -> {
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        var targetPlayers = EntityArgumentType.getPlayers(context, "players");

                        for (var player : targetPlayers) {
                            ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetKitCooldown(kitName);
                        }

                        context.getSource().sendFeedback(() ->
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

                    context.getSource().sendFeedback(() ->
                        Text.literal(String.format("Reset all kit cooldowns for %d players", targetPlayers.size())),
                        true);

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("commands")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(KitSuggestions::suggestionProvider)
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
                        .suggests((CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) -> {
                            String kitName = StringArgumentType.getString(context, "kit_name");
                            return ListSuggestion.getSuggestionsBuilder(builder, KIT_MAP.containsKey(kitName)
                                ? KIT_MAP.get(kitName).commands()
                                : new ArrayList<>());
                        })
                        .executes(KitCommandsManagerCommand::removeCommandFromKit)
                    )
                )
            )
            .build()
        );

        registerKitRing(dispatcher, commandRegistryAccess, registrationEnvironment, kitNode);

        var kitsSguiBuilder = literal("kits")
            .executes(new KitsCommand());
        dispatcher.register(kitsSguiBuilder);
    }

    public static void registerKitRing(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess commandRegistryAccess,
        CommandManager.RegistrationEnvironment registrationEnvironment,
        CommandNode<ServerCommandSource> kitNode
    ) {
        var ring = literal("ring");

        ring.then(literal("add")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .then(argument("cooldown", LongArgumentType.longArg(-1))
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        return addKitRing(
                            context,
                            ringName,
                            new KitRing(
                                Text.of(ringName),
                                LongArgumentType.getLong(context, "cooldown"),
                                null, // displayItem
                                new HashMap<>(),
                                new ArrayList<>()
                            )
                        );
                    })
                    .then(argument("time_unit", StringArgumentType.word())
                        .suggests(TimeUtil::suggestions)
                        .executes(context -> {
                            var ringName = StringArgumentType.getString(context, "ring_name");
                            return addKitRing(
                                context,
                                ringName,
                                new KitRing(
                                    Text.of(ringName),
                                    TimeUtil.parseToMillis(
                                        LongArgumentType.getLong(context, "cooldown"),
                                        StringArgumentType.getString(context, "time_unit")
                                    ),
                                    null, // displayItem
                                    new HashMap<>(),
                                    new ArrayList<>()
                                )
                            );
                        })))
            ).build()
        );

        ring.then(literal("setDisplayItem")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(KitSuggestions::kitRingsSuggestionProvider)
                .then(argument("item", ItemStackArgumentType.itemStack(commandRegistryAccess))
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var item = ItemStackArgumentType.getItemStackArgument(context, "item");

                        var existingRing = KIT_RING_MAP.get(ringName);
                        existingRing.setDisplayItem(item.getItem());
                        try {
                            saveKitRing(ringName, existingRing, context.getSource().getWorld());
                        } catch (IOException e) {
                            throw new KitCommandSyntaxException(Text.literal("Failed to save kit ring."));
                        }
                        return 0;
                    })
                )
            ).build()
        );

        ring.then(literal("removeRingAndContainedKits")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(KitSuggestions::kitRingsSuggestionProvider)
                .executes(context -> {
                    String ringName = StringArgumentType.getString(context, "ring_name");
                    KIT_RING_MAP.remove(ringName);

                    try {
                        Files.delete(KitsMod.getKitsDir().toPath().resolve(ringName + ".ring.nbt"));
                    } catch (IOException e) {
                        context.getSource().sendError(Text.of("Could not find kit file on disk."));
                        return -1;
                    }

                    context.getSource().sendFeedback(() -> Text.of(String.format("Removed kit ring '%s' and all its contained kits.", ringName)), true);

                    return 1;
                })
            ).build()
        );

        ring.then(literal("removeRing")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(KitSuggestions::kitRingsSuggestionProvider)
                .executes(context -> {
                    String ringName = StringArgumentType.getString(context, "ring_name");
                    KIT_MAP.remove(ringName);

                    try {
                        Files.delete(KitsMod.getKitsDir().toPath().resolve(ringName + ".nbt"));
                    } catch (IOException e) {
                        context.getSource().sendError(Text.of("Could not find kit file on disk."));
                        return -1;
                    }

                    context.getSource().sendFeedback(() -> Text.of(String.format("Removed kit '%s'.", ringName)), true);

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("resetPlayerRingSelection")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("players", EntityArgumentType.players())
                .then(argument("ring_name", StringArgumentType.word())
                    .suggests(KitSuggestions::kitRingsSuggestionProvider)
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var targetPlayers = EntityArgumentType.getPlayers(context, "players");

                        for (var player : targetPlayers) {
                            ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetRingSelection(ringName);
                        }

                        context.getSource().sendFeedback(() ->
                                Text.literal(String.format("Reset kit ring selection '%s' for %d players", ringName, targetPlayers.size())),
                            true);

                        return 1;
                    })
                )).build()
        );

        ring.then(literal("commands")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(KitSuggestions::kitRingsSuggestionProvider)
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
                        .suggests((CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) -> {
                            String ringName = StringArgumentType.getString(context, "ring_name");
                            return ListSuggestion.getSuggestionsBuilder(builder, KIT_RING_MAP.containsKey(ringName)
                                ? KIT_RING_MAP.get(ringName).commands()
                                : new ArrayList<>());
                        })
                        .executes(KitCommandsManagerCommand::removeCommandFromKit)
                    )
                )
            )
            .build()
        );

        ring.then(literal("addKit")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(KitSuggestions::kitRingsSuggestionProvider)
                .then(argument("kit_name", StringArgumentType.word())
                    .suggests(KitSuggestions::kitsNotInRingSuggestionProvider)
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        return addKitToRing(
                            context,
                            ringName,
                            kitName
                        );
                    })
                )
            ).build()
        );

        ring.then(literal("removeKit")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(KitSuggestions::kitRingsSuggestionProvider)
                .then(argument("kit_name", StringArgumentType.word())
                    .suggests(KitSuggestions::kitRingKitsSuggestionProvider)
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        return removeKitFromRing(
                            context,
                            ringName,
                            kitName
                        );
                    })
                )
            ).build()
        );

        kitNode.addChild(ring.build());
    }

}
