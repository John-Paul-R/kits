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

import dev.jpcode.kits.access.ServerPlayerEntityAccess;

public final class KitSuggestions {

    private final KitsModStorage storage;

    public KitSuggestions(
        KitsModStorage storage
    ) {
        this.storage = storage;
    }

    public Stream<KitsModStorage.KitRecord> getAllKitsForPlayer(ServerPlayerEntity player) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        return storage
            .getKitRecords(k -> playerData.canSeeKit(k) && playerData.mayClaimFromRing(k.ringName(), k.kitName()))
            ;
//            Stream.concat(
//            getAllNonRingKitsForPlayer(player),
//            getAllKitRingsForPlayer(player)
//                .flatMap(ring -> {
//                    var ringName = ring.getKey();
//                    return ring.getValue()
//                        .kits()
//                        .entrySet().stream()
//                        .filter(kitEntry -> playerData.mayClaimFromRing(ringName, kitEntry.getKey()));
//                })
//        );
    }

    public Stream<KitsModStorage.KitRecord> getAllNonRingKitsForPlayer(ServerPlayerEntity player) {
        var source = player.getCommandSource();
        return storage.getNonRingKitRecords(k -> KitPerms.checkKit(source, k));
    }

    public Stream<Map.Entry<String, KitRing>> getAllKitRingsForPlayer(ServerPlayerEntity player) {
        var source = player.getCommandSource();
        return storage.KIT_RING_MAP.entrySet()
            .stream()
            .filter(ringEntry ->
                KitPerms.checkKit(source, ringEntry.getKey())
            );
    }

    public CompletableFuture<Suggestions> kitRingsSuggestionProvider(
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

    public CompletableFuture<Suggestions> kitRingKitsSuggestionProvider(
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

    public CompletableFuture<Suggestions> kitsNotInRingSuggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllNonRingKitsForPlayer(context.getSource().getPlayer())
                .map(KitsModStorage.KitRecord::kitName)
                .toList()
        );
    }

    /**
     * Suggests existing kits that the user has permissions for.
     *
     * @param context server command context w/ player
     * @param builder suggestions builder
     * @return suggestions for existing kits that the user has permissions for.
     */
    public CompletableFuture<Suggestions> suggestionProvider(
        CommandContext<ServerCommandSource> context,
        SuggestionsBuilder builder
    ) {
        return ListSuggestion.getSuggestionsBuilder(
            builder,
            getAllKitsForPlayer(context.getSource().getPlayer())
                .map(KitsModStorage.KitRecord::kitName)
                .toList()
        );
    }

}
