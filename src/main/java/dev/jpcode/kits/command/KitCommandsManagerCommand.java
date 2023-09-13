package dev.jpcode.kits.command;

import java.io.IOException;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.command.CommandException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import dev.jpcode.kits.Kit;

import static dev.jpcode.kits.KitsCommandRegistry.saveKit;
import static dev.jpcode.kits.KitsMod.KIT_MAP;

public final class KitCommandsManagerCommand {
    private KitCommandsManagerCommand() {}

    public static int listCommandsForKit(CommandContext<ServerCommandSource> context) {
        String kitName = StringArgumentType.getString(context, "kit_name");
        ServerCommandSource source = context.getSource();
        Kit kit = getKit(kitName);
        if (kit == null) return 0;

        MutableText message = Text.literal(String.format("Kit '%s'", kitName));
        if (kit.commands().isPresent()) {
            message.append(" (click a command to remove)");
            List<String> commands = kit.commands().get();
            for (int i = 1; i <= commands.size(); i++) {
                String command = commands.get(i - 1);
                message.append(Text.literal(String.format("\n#%d: %s", i, command))
                    .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        String.format("/kit commands %s remove %s", kitName, command)))));
            }
        } else {
            message.append("\nDoes not have any commands");
        }

        source.sendFeedback(() -> message, false);
        return 1;
    }

    public static int addCommandToKit(CommandContext<ServerCommandSource> context) {
        String kitName = StringArgumentType.getString(context, "kit_name");
        ServerCommandSource source = context.getSource();
        Kit kit = getKit(kitName);
        if (kit == null) return 0;

        String command = StringArgumentType.getString(context, "command")
            .replaceFirst("^/+", ""); // remove any slashes at the start

        try {
            boolean added = kit.addCommand(command);
            if (!added) throw new CommandException(Text.literal("Command already exists in this kit."));
            saveKit(kitName, kit);
            source.sendFeedback(() ->
                    Text.literal(String.format("Added command \"%s\" to kit '%s'", command, kitName)),
                true);
        } catch (IOException e) {
            throw new CommandException(Text.literal("Failed to save kit."));
        }
        return 1;
    }

    public static int removeCommandFromKit(CommandContext<ServerCommandSource> context) {
        String kitName = StringArgumentType.getString(context, "kit_name");
        ServerCommandSource source = context.getSource();
        Kit kit = getKit(kitName);
        if (kit == null) return 0;

        String command = StringArgumentType.getString(context, "command");

        try {
            boolean existed = kit.removeCommand(command);
            if (!existed) throw new CommandException(Text.literal("No command with that index."));
            saveKit(kitName, kit);
            source.sendFeedback(() ->
                    Text.literal(String.format("Removed command \"%s\" from kit '%s'. (click to re-add)", command, kitName))
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            String.format("/kit commands %s add %s", kitName, command)))),
                true);
        } catch (IOException e) {
            throw new CommandException(Text.literal("Failed to save kit."));
        }
        return 1;
    }

    private static Kit getKit(String kitName) {
        if (!KIT_MAP.containsKey(kitName)) {
            throw new CommandException(Text.literal(String.format("kit '%s' does not exist", kitName)));
        }
        return KIT_MAP.get(kitName);
    }
}
