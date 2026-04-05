package dev.jpcode.kits;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

import me.lucko.fabric.api.permissions.v0.Permissions;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;
import dev.jpcode.kits.command.KitClaimCommand;
import dev.jpcode.kits.command.KitCommandsManagerCommand;
import dev.jpcode.kits.command.KitsCommand;
import dev.jpcode.kits.config.KitsConfig;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class KitsCommandRegistry {
    private final Logger logger;
    private final KitsConfig config;
    private final KitsModStorage storage;
    private final KitSuggestions kitSuggestions;

    public KitsCommandRegistry(
        Logger logger,
        KitsConfig config,
        KitsModStorage storage
    ) {
        this.logger = logger;
        this.config = config;
        this.storage = storage;
        this.kitSuggestions = new KitSuggestions(storage);
    }

    /**
     * @param cooldownMs if negative, creates a one-time use kit
     */
    int addKit(CommandContext<CommandSourceStack> context, String kitName, Inventory sourceInventory, long cooldownMs) {
        var kitInventory = new KitInventory();
        kitInventory.copyFrom(sourceInventory);
        return addKit(context, kitName, new Kit(kitInventory, cooldownMs, kitName));
    }

    int addKit(CommandContext<CommandSourceStack> context, String kitName, Kit kit)
    {
        try {
            storage.putKit(kitName, kit);
            context.getSource().sendSuccess(() ->
                Component.nullToEmpty(String.format("Kit '%s' created from current inventory.", kitName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    int addKitRing(CommandContext<CommandSourceStack> context, String ringName, KitRing ring) {
        try {
            storage.putKitRing(ringName, ring);
            context.getSource().sendSuccess(() ->
                    Component.nullToEmpty(String.format("Kit Ring '%s' created.", ringName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    int addKitToRing(CommandContext<CommandSourceStack> context, String ringName, String kitName)
        throws KitCommandSyntaxException
    {
        try {
            storage.putKitInRing(ringName, kitName);
            context.getSource().sendSuccess(() ->
                    Component.nullToEmpty(String.format("Kit '%s' added to Ring '%s' created.", kitName, ringName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 1;
    }

    int removeKitFromRing(CommandContext<CommandSourceStack> context, String ringName, String kitName)
        throws KitCommandSyntaxException
    {
        try {
            storage.removeKitFromRing(ringName, kitName);
            context.getSource().sendSuccess(() ->
                    Component.nullToEmpty(String.format("Kit '%s' removed from Ring '%s'.", kitName, ringName)),
                true
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        CommandBuildContext commandRegistryAccess,
        Commands.CommandSelection registrationEnvironment
    ) {
        CommandNode<CommandSourceStack> kitNode = dispatcher.register(literal("kit"));

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
                .suggests(kitSuggestions::suggestionProvider)
                .then(argument("item", ItemArgument.item(commandRegistryAccess))
                    .executes(context -> {
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        var item = ItemArgument.getItem(context, "item");

                        var existingKit = storage.KIT_MAP.get(kitName);
                        existingKit.setDisplayItem(item.item().value());
                        try {
                            storage.saveKit(kitName, existingKit);
                        } catch (IOException e) {
                            throw new KitCommandSyntaxException(Component.literal("Failed to save kit."));
                        }
                        return 0;
                    })
                )
            ).build()
        );

        kitNode.addChild(literal("claim")
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(kitSuggestions::suggestionProvider)
                .executes(new KitClaimCommand(storage))
            ).build()
        );

        kitNode.addChild(literal("remove")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(kitSuggestions::suggestionProvider)
                .executes(context -> {
                    String kitName = StringArgumentType.getString(context, "kit_name");
                    storage.KIT_MAP.remove(kitName);

                    try {
                        Files.delete(KitsMod.getKitsDir().toPath().resolve(kitName + ".nbt"));
                    } catch (IOException e) {
                        context.getSource().sendFailure(Component.nullToEmpty("Could not find kit file on disk."));
                        return -1;
                    }

                    context.getSource().sendSuccess(() -> Component.nullToEmpty(String.format("Removed kit '%s'.", kitName)), true);

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("reload")
            .requires(Permissions.require("kits.manage", 4))
            .executes(context -> {
                KitsMod.reload(context.getSource().getServer());
                return 1;
            }).build()
        );

        kitNode.addChild(literal("resetPlayerKit")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("players", EntityArgument.players())
                .then(argument("kit_name", StringArgumentType.word())
                    .suggests(kitSuggestions::suggestionProvider)
                    .executes(context -> {
                        var kitName = StringArgumentType.getString(context, "kit_name");
                        var targetPlayers = EntityArgument.getPlayers(context, "players");

                        for (var player : targetPlayers) {
                            ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetKitCooldown(kitName);
                        }

                        context.getSource().sendSuccess(() ->
                            Component.literal(String.format("Reset kit '%s' cooldown for %d players", kitName, targetPlayers.size())),
                            true);

                        return 1;
                    })
                )).build()
        );

        kitNode.addChild(literal("resetPlayer")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("players", EntityArgument.players())
                .executes(context -> {
                    var targetPlayers = EntityArgument.getPlayers(context, "players");
                    for (var player : targetPlayers) {
                        ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetAllKits();
                    }

                    context.getSource().sendSuccess(() ->
                        Component.literal(String.format("Reset all kit cooldowns for %d players", targetPlayers.size())),
                        true);

                    return 1;
                })
            ).build()
        );

        var commandsCommand = new KitCommandsManagerCommand(
            storage
        );
        kitNode.addChild(literal("commands")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("kit_name", StringArgumentType.word())
                .suggests(kitSuggestions::suggestionProvider)
                .then(literal("list")
                    .executes(commandsCommand::listCommandsForKit)
                )
                .then(literal("add")
                    .then(argument("command", StringArgumentType.greedyString())
                        .executes(commandsCommand::addCommandToKit)
                    )
                )
                .then(literal("remove")
                    .then(argument("command", StringArgumentType.greedyString())
                        .suggests((CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> {
                            String kitName = StringArgumentType.getString(context, "kit_name");
                            return ListSuggestion.getSuggestionsBuilder(builder, storage.KIT_MAP.containsKey(kitName)
                                ? storage.KIT_MAP.get(kitName).commands()
                                : new ArrayList<>());
                        })
                        .executes(commandsCommand::removeCommandFromKit)
                    )
                )
            )
            .build()
        );

        registerKitRing(dispatcher, commandRegistryAccess, registrationEnvironment, kitNode);

        var kitsSguiBuilder = literal("kits")
            .executes(new KitsCommand(storage, kitSuggestions));
        dispatcher.register(kitsSguiBuilder);
    }

    public void registerKitRing(
        CommandDispatcher<CommandSourceStack> dispatcher,
        CommandBuildContext commandRegistryAccess,
        Commands.CommandSelection registrationEnvironment,
        CommandNode<CommandSourceStack> kitNode
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
                                Component.nullToEmpty(ringName),
                                LongArgumentType.getLong(context, "cooldown"),
                                null, // displayItem
                                new HashMap<>(),
                                new ArrayList<>(),
                                false // permanentChoice
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
                                    Component.nullToEmpty(ringName),
                                    TimeUtil.parseToMillis(
                                        LongArgumentType.getLong(context, "cooldown"),
                                        StringArgumentType.getString(context, "time_unit")
                                    ),
                                    null, // displayItem
                                    new HashMap<>(),
                                    new ArrayList<>(),
                                    false // permanentChoice
                                )
                            );
                        })))
            ).build()
        );

        ring.then(literal("setDisplayItem")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .then(argument("item", ItemArgument.item(commandRegistryAccess))
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var item = ItemArgument.getItem(context, "item");

                        var existingRing = storage.KIT_RING_MAP.get(ringName);
                        existingRing.setDisplayItem(item.item().value());
                        try {
                            storage.saveKitRingMetadata(ringName, existingRing);
                        } catch (IOException e) {
                            throw new KitCommandSyntaxException(Component.literal("Failed to save kit ring metadata."));
                        }
                        return 0;
                    })
                )
            ).build()
        );

        ring.then(literal("permanentChoice")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .then(argument("enabled", BoolArgumentType.bool())
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var enabled = BoolArgumentType.getBool(context, "enabled");

                        var existingRing = storage.KIT_RING_MAP.get(ringName);
                        if (existingRing == null) {
                            context.getSource().sendFailure(Component.nullToEmpty("Kit ring not found."));
                            return -1;
                        }

                        existingRing.setPermanentChoice(enabled);
                        try {
                            storage.saveKitRingMetadata(ringName, existingRing);
                        } catch (IOException e) {
                            throw new KitCommandSyntaxException(Component.literal("Failed to save kit ring metadata."));
                        }

                        context.getSource().sendSuccess(() ->
                            Component.nullToEmpty(String.format("Permanent choice for ring '%s' set to: %s", ringName, enabled)),
                            true
                        );

                        return 1;
                    })
                )
            ).build()
        );

        ring.then(literal("removeRingAndContainedKits")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .executes(context -> {
                    String ringName = StringArgumentType.getString(context, "ring_name");

                    try {
                        storage.removeKitRingAndKits(ringName);
                    } catch (IOException e) {
                        context.getSource().sendFailure(Component.nullToEmpty("Could not delete kit ring directory on disk."));
                        return -1;
                    }

                    context.getSource().sendSuccess(() -> Component.nullToEmpty(String.format("Removed kit ring '%s' and all its contained kits.", ringName)), true);

                    return 1;
                })
            ).build()
        );

        ring.then(literal("removeRing")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .executes(context -> {
                    String ringName = StringArgumentType.getString(context, "ring_name");

                    try {
                        storage.removeRingExtractKits(ringName);
                    } catch (IllegalArgumentException e) {
                        context.getSource().sendFailure(Component.nullToEmpty("Kit ring not found."));
                        return -1;
                    } catch (IOException e) {
                        context.getSource().sendFailure(Component.nullToEmpty("Error removing ring: " + e.getMessage()));
                        return -1;
                    }

                    context.getSource().sendSuccess(() -> Component.nullToEmpty(String.format("Removed kit ring '%s' and moved all kits to standalone.", ringName)), true);

                    return 1;
                })
            ).build()
        );

        kitNode.addChild(literal("resetPlayerRingSelection")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("players", EntityArgument.players())
                .then(argument("ring_name", StringArgumentType.word())
                    .suggests(kitSuggestions::kitRingsSuggestionProvider)
                    .executes(context -> {
                        var ringName = StringArgumentType.getString(context, "ring_name");
                        var targetPlayers = EntityArgument.getPlayers(context, "players");

                        for (var player : targetPlayers) {
                            ((ServerPlayerEntityAccess) player).kits$getPlayerData().resetRingChoice(ringName);
                        }

                        context.getSource().sendSuccess(() ->
                                Component.literal(String.format("Reset kit ring selection '%s' for %d players", ringName, targetPlayers.size())),
                            true);

                        return 1;
                    })
                )).build()
        );

        var commandsCommand = new KitCommandsManagerCommand(
            storage
        );
        ring.then(literal("commands")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .then(literal("list")
                    .executes(commandsCommand::listCommandsForKit)
                )
                .then(literal("add")
                    .then(argument("command", StringArgumentType.greedyString())
                        .executes(commandsCommand::addCommandToKit)
                    )
                )
                .then(literal("remove")
                    .then(argument("command", StringArgumentType.greedyString())
                        .suggests((CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> {
                            String ringName = StringArgumentType.getString(context, "ring_name");
                            return ListSuggestion.getSuggestionsBuilder(builder, storage.KIT_RING_MAP.containsKey(ringName)
                                ? storage.KIT_RING_MAP.get(ringName).commands()
                                : new ArrayList<>());
                        })
                        .executes(commandsCommand::removeCommandFromKit)
                    )
                )
            )
            .build()
        );

        ring.then(literal("addKit")
            .requires(Permissions.require("kits.manage", 4))
            .then(argument("ring_name", StringArgumentType.word())
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .then(argument("kit_name", StringArgumentType.word())
                    .suggests(kitSuggestions::kitsNotInRingSuggestionProvider)
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
                .suggests(kitSuggestions::kitRingsSuggestionProvider)
                .then(argument("kit_name", StringArgumentType.word())
                    .suggests(kitSuggestions::kitRingKitsSuggestionProvider)
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
