package dev.jpcode.kits.command;

import java.io.IOException;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import dev.jpcode.kits.Kit;
import dev.jpcode.kits.KitCommandSyntaxException;
import dev.jpcode.kits.KitsModStorage;

public final class KitCommandsManagerCommand {
    private KitsModStorage storage;

    public KitCommandsManagerCommand(KitsModStorage storage) {
        this.storage = storage;
    }

    public int listCommandsForKit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        CommandSourceStack source = context.getSource();
        Kit kit = getKit(kitName);

        MutableComponent message = Component.literal(String.format("Kit '%s'", kitName));
        if (!kit.commands().isEmpty()) {
            message.append(" (click a command to remove)");
            List<String> commands = kit.commands();
            for (int i = 1; i <= commands.size(); i++) {
                String command = commands.get(i - 1);
                message.append(Component.literal(String.format("\n#%d: %s", i, command))
                    .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand(
                        String.format("/kit commands %s remove %s", kitName, command)
                    ))));
            }
        } else {
            message.append("\nDoes not have any commands");
        }

        source.sendSuccess(() -> message, false);
        return 1;
    }

    public int addCommandToKit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        CommandSourceStack source = context.getSource();
        Kit kit = getKit(kitName);

        String command = StringArgumentType.getString(context, "command")
            .replaceFirst("^/", ""); // remove first slash at the start

        try {
            boolean added = kit.addCommand(command);
            if (!added) throw new KitCommandSyntaxException(Component.literal("Command already exists in this kit."));
            storage.saveKit(kitName, kit);
            source.sendSuccess(() ->
                    Component.literal(String.format("Added command \"%s\" to kit '%s'", command, kitName)),
                true);
        } catch (IOException e) {
            throw new KitCommandSyntaxException(Component.literal("Failed to save kit."));
        }
        return 1;
    }

    public int removeCommandFromKit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        CommandSourceStack source = context.getSource();
        Kit kit = getKit(kitName);

        String command = StringArgumentType.getString(context, "command");

        try {
            boolean existed = kit.removeCommand(command);
            if (!existed) throw new KitCommandSyntaxException(Component.literal("That command is not in this kit."));
            storage.saveKit(kitName, kit);
            source.sendSuccess(() ->
                    Component.literal(String.format("Removed command \"%s\" from kit '%s'. (click to re-add)", command, kitName))
                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand(
                            String.format("/kit commands %s add %s", kitName, command)
                        ))),
                true);
        } catch (IOException e) {
            throw new KitCommandSyntaxException(Component.literal("Failed to save kit."));
        }
        return 1;
    }

    private Kit getKit(String kitName) throws CommandSyntaxException {
        if (!storage.KIT_MAP.containsKey(kitName)) {
            throw new KitCommandSyntaxException(Component.literal(String.format("Kit '%s' does not exist", kitName)));
        }
        return storage.KIT_MAP.get(kitName);
    }
}
