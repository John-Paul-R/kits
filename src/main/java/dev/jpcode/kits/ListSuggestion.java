package dev.jpcode.kits;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

public final class ListSuggestion {

    private ListSuggestion() {}

    public static CompletableFuture<Suggestions> getSuggestionsBuilder(SuggestionsBuilder builder, Collection<String> suggestions) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        if (suggestions.isEmpty()) { // If the suggestions is empty then return no suggestions
            return Suggestions.empty(); // No suggestions
        }

        for (String str : suggestions) { // Iterate through the supplied suggestions
            if (str.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(str); // Add every single entry to suggestions suggestions.
            }
        }
        return builder.buildFuture(); // Create the CompletableFuture containing all the suggestions
    }
}
