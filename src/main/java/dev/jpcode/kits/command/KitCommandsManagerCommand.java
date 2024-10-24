package dev.jpcode.kits.command;

import java.io.IOException;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import dev.jpcode.kits.Kit;
import dev.jpcode.kits.KitCommandSyntaxException;

import static dev.jpcode.kits.KitsCommandRegistry.saveKit;
import static dev.jpcode.kits.KitsMod.KIT_MAP;

public final class KitCommandsManagerCommand {
    private KitCommandsManagerCommand() {}

    public static int listCommandsForKit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        ServerCommandSource source = context.getSource();
        Kit kit = getKit(kitName);

        MutableText message = Text.literal(String.format("Kit '%s'", kitName));
        if (!kit.commands().isEmpty()) {
            message.append(" (click a command to remove)");
            List<String> commands = kit.commands();
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

    public static int addCommandToKit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        ServerCommandSource source = context.getSource();
        Kit kit = getKit(kitName);

        String command = StringArgumentType.getString(context, "command")
            .replaceFirst("^/", ""); // remove first slash at the start

        try {
            boolean added = kit.addCommand(command);
            if (!added) throw new KitCommandSyntaxException(Text.literal("Command already exists in this kit."));
            saveKit(kitName, kit, source.getWorld());
            source.sendFeedback(() ->
                    Text.literal(String.format("Added command \"%s\" to kit '%s'", command, kitName)),
                true);
        } catch (IOException e) {
            throw new KitCommandSyntaxException(Text.literal("Failed to save kit."));
        }
        return 1;
    }

    public static int removeCommandFromKit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        ServerCommandSource source = context.getSource();
        Kit kit = getKit(kitName);

        String command = StringArgumentType.getString(context, "command");

        try {
            boolean existed = kit.removeCommand(command);
            if (!existed) throw new KitCommandSyntaxException(Text.literal("That command is not in this kit."));
            saveKit(kitName, kit, source.getWorld());
            source.sendFeedback(() ->
                    Text.literal(String.format("Removed command \"%s\" from kit '%s'. (click to re-add)", command, kitName))
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            String.format("/kit commands %s add %s", kitName, command)))),
                true);
        } catch (IOException e) {
            throw new KitCommandSyntaxException(Text.literal("Failed to save kit."));
        }
        return 1;
    }

    private static Kit getKit(String kitName) throws CommandSyntaxException {
        if (!KIT_MAP.containsKey(kitName)) {
            throw new KitCommandSyntaxException(Text.literal(String.format("Kit '%s' does not exist", kitName)));
        }
        return KIT_MAP.get(kitName);
    }
}
