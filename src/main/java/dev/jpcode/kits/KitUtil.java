package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.Objects;

import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class KitUtil {

    private KitUtil() {}

    public static void giveKit(ServerPlayer player, Kit kit) {
        InventoryUtil.offerAllCopies(kit.inventory(), player.getInventory());
    }

    public static void runCommands(ServerPlayer player, ArrayList<String> commands) {
        MinecraftServer server = player.level().getServer();
        Commands commandManager = Objects.requireNonNull(server).getCommands();
        for (String command : commands) {
            command = command.replace("@p", player.getName().getString());
            commandManager.performPrefixedCommand(server.createCommandSourceStack(), command);
        }
    }
}
