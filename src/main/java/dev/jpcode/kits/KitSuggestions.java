package dev.jpcode.kits;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import dev.jpcode.kits.access.ServerPlayerEntityAccess;

public final class KitSuggestions {

    private final KitsModStorage storage;

    public KitSuggestions(
        KitsModStorage storage
    ) {
        this.storage = storage;
    }

    public Stream<KitsModStorage.KitRecord> getAllKitsForPlayer(ServerPlayer player) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        return storage
            .getKitRecords(k -> playerData.canSeeKit(k) && playerData.mayClaimFromRing(k.ringName(), k.kitName()));
    }

    public Stream<KitsModStorage.KitRecord> getAllNonRingKitsForPlayer(ServerPlayer player) {
        var source = player.createCommandSourceStack();
        return storage.getNonRingKitRecords(k -> KitPerms.checkKit(source, k));
    }

    public Stream<Map.Entry<String, KitRing>> getAllKitRingsForPlayer(ServerPlayer player) {
        var source = player.createCommandSourceStack();
        return storage.KIT_RING_MAP.entrySet()
            .stream()
            .filter(ringEntry ->
                KitPerms.checkKit(source, ringEntry.getKey())
            );
    }

    public CompletableFuture<Suggestions> kitRingsSuggestionProvider(
        CommandContext<CommandSourceStack> context,
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
        CommandContext<CommandSourceStack> context,
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
        CommandContext<CommandSourceStack> context,
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
        CommandContext<CommandSourceStack> context,
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
