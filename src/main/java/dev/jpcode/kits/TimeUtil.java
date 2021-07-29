package dev.jpcode.kits;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.server.command.ServerCommandSource;

public final class TimeUtil {

    private TimeUtil() {}

    private static final LinkedHashMap<String, Long> TIME_WEIGHTS = new LinkedHashMap<>();

    static {
        TIME_WEIGHTS.put("y", 1000L * 60 * 60 * 24 * 365);
        TIME_WEIGHTS.put("d", 1000L * 60 * 60 * 24);
        TIME_WEIGHTS.put("h", 1000L * 60 * 60);
        TIME_WEIGHTS.put("m", 1000L * 60);
        TIME_WEIGHTS.put("s", 1000L);

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
        return time * TIME_WEIGHTS.get(timeUnit);
    }

    public static CompletableFuture<Suggestions> suggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return ListSuggestion.getSuggestionsBuilder(builder, TIME_WEIGHTS.keySet());
    }

    public static String formatTime(long millis) {
        int i = 0;
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Long> entry : TIME_WEIGHTS.entrySet()) {
            String unitStr = entry.getKey();
            Long unitMsValue = entry.getValue();

            long val = millis / unitMsValue;
            i++;
            if (val != 0) {
                builder.append(val).append(unitStr);
                if (i != TIME_WEIGHTS.size()) {
                    builder.append(" ");
                }
            }

            millis %= unitMsValue;

        }

        return builder.toString();
    }

}
