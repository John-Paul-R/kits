package dev.jpcode.kits.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import dev.jpcode.kits.Kit;
import dev.jpcode.kits.KitPerms;
import dev.jpcode.kits.PlayerKitData;
import dev.jpcode.kits.TimeUtil;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.InventoryUtil.offerAllCopies;
import static dev.jpcode.kits.KitUtil.runCommands;
import static dev.jpcode.kits.KitsMod.KIT_MAP;

public class KitClaimCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        return exec(context.getSource().getPlayerOrThrow(), kitName);
    }

    public static int exec(ServerPlayerEntity player, String kitName) {
        PlayerKitData playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        var commandSource = player.getCommandSource();

        Kit kit = KIT_MAP.get(kitName);
        long currentTime = Util.getEpochTimeMs();
        long remainingTime = (playerData.getKitUsedTime(kitName) + kit.cooldown()) - currentTime;

        if (!KitPerms.checkKit(commandSource, kitName)) {
            commandSource.sendError(Text.of(String.format(
                "Insufficient permissions for kit '%s'.",
                kitName)));
            return -1;
        } else if (remainingTime > 0) {
            commandSource.sendError(Text.of(
                String.format(
                    "Kit '%s' is on cooldown. %s remaining.",
                    kitName,
                    TimeUtil.formatTime(remainingTime)
                )));
            return -2;
        }

        PlayerInventory playerInventory = player.getInventory();
        playerData.useKit(kitName);
        offerAllCopies(kit.inventory(), playerInventory);
        if (kit.commands().isPresent()) runCommands(player, kit.commands().get());

        commandSource.sendFeedback(
            Text.of(String.format("Successfully claimed kit '%s'!", kitName)),
            commandSource.getServer().shouldBroadcastConsoleToOps()
        );

        return 1;
    }
}
