package dev.jpcode.kits;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.KitsMod.KIT_MAP;
import static dev.jpcode.kits.KitsMod.KIT_RING_MAP;

public final class KitSuggestions {
    public static Stream<Map.Entry<String, Kit>> getAllKitsForPlayer(ServerPlayerEntity player) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        return Stream.concat(
            getAllNonRingKitsForPlayer(player),
            getAllKitRingsForPlayer(player)
                .flatMap(ring -> {
                    var ringName = ring.getKey();
                    return ring.getValue()
                        .kits()
                        .entrySet().stream()
                        .filter(kitEntry -> playerData.mayClaimFromRing(ringName, kitEntry.getKey()));
                })
        );
    }

    public static Stream<Map.Entry<String, Kit>> getAllNonRingKitsForPlayer(ServerPlayerEntity player) {
        var source = player.getCommandSource();
        return KIT_MAP.entrySet()
            .stream()
            .filter(kitEntry ->
                KitPerms.checkKit(source, kitEntry.getKey())
            );
    }

    public static Stream<Map.Entry<String, KitRing>> getAllKitRingsForPlayer(ServerPlayerEntity player) {
        var source = player.getCommandSource();
        return KIT_RING_MAP.entrySet()
            .stream()
            .filter(ringEntry ->
                KitPerms.checkKit(source, ringEntry.getKey())
            );
    }

    public static CompletableFuture<Suggestions> kitRingsSuggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllKitRingsForPlayer(context.getSource().getPlayer())
                .map(Map.Entry::getKey)
                .toList()
        );
    }

    public static CompletableFuture<Suggestions> kitRingKitsSuggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        var ringName = StringArgumentType.getString(context, "ring_name");
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllKitRingsForPlayer(context.getSource().getPlayer())
                .filter(kitRingEntry -> kitRingEntry.getKey().equals(ringName))
                .flatMap(kitRingEntry -> kitRingEntry.getValue().kits().keySet().stream())
                .toList()
        );
    }

    public static CompletableFuture<Suggestions> kitsNotInRingSuggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllNonRingKitsForPlayer(context.getSource().getPlayer())
                .map(Map.Entry::getKey)
                .toList()
        );
    }

    public static Stream<Map.Entry<String, Kit>> getClaimableKitsForPlayer(ServerPlayerEntity player) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        long currentTime = Util.getEpochTimeMs();

        return getAllKitsForPlayer(player)
            .filter(entry -> playerData.isKitOnCooldownAtTime(entry, currentTime));
    }

    /**
     * Suggests existing kits that the user has permissions for.
     *
     * @param context server command context w/ player
     * @param builder suggestions builder
     * @return suggestions for existing kits that the user has permissions for.
     */
    public static CompletableFuture<Suggestions> suggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllKitsForPlayer(context.getSource().getPlayer())
                .map(Map.Entry::getKey)
                .toList()
        );
    }

}
