package dev.jpcode.kits;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.server.command.ServerCommandSource;

import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private TimeUtil() {}

    private static final LinkedHashMap<String, Long> timeWeights = new LinkedHashMap<>();

    static {
        timeWeights.put("y",  1000L * 60 * 60 * 24 * 365);
        timeWeights.put("d",  1000L * 60 * 60 * 24);
        timeWeights.put("h",  1000L * 60 * 60 );
        timeWeights.put("m",  1000L * 60 );
        timeWeights.put("s",  1000L);

    }

    public static long parseToMillis(String timeStr) {
        Matcher matcher = Pattern.compile("^[0-9]+([hmsdy])$").matcher(timeStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("'%s' does not match the time format '%s'", timeStr, matcher.pattern().toString()));
        }
        long num = Long.parseLong(matcher.group(1));
        String timeId = matcher.group(2);

        return parseToMillis(num, timeId);
    }
    public static long parseToMillis(long time, String timeUnit) {
        return time * timeWeights.get(timeUnit);
    }

    public static CompletableFuture<Suggestions> suggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return ListSuggestion.getSuggestionsBuilder(builder, timeWeights.keySet());
    }

    public static String formatTime(long millis) {
        int i = 0;
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Long> entry : timeWeights.entrySet()) {
            String unitStr = entry.getKey();
            Long unitMsValue = entry.getValue();

            long val = millis / unitMsValue;
            i++;
            if (val != 0) {
                builder.append(val).append(unitStr);
                if (i != timeWeights.size()) {
                    builder.append(" ");
                }
            }

            millis %= unitMsValue;

        }

        return builder.toString();
    }

}
