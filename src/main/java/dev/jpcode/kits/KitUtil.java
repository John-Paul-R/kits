package dev.jpcode.kits;

import java.util.ArrayList;
import java.util.Objects;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;

public final class KitUtil {

    private KitUtil() {}

    public static void giveKit(ServerPlayerEntity player, Kit kit) {
        InventoryUtil.offerAllCopies(kit.inventory(), player.getInventory());
    }

    public static void runCommands(ServerPlayerEntity player, ArrayList<String> commands) {
        MinecraftServer server = player.getServer();
        CommandManager commandManager = Objects.requireNonNull(server).getCommandManager();
        for (String command : commands) {
            command = command.replace("@p", player.getName().getString());
            commandManager.executeWithPrefix(server.getCommandSource(), command);
        }
    }
}
