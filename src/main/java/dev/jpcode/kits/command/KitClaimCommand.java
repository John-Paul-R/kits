package dev.jpcode.kits.command;

import java.util.Optional;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import dev.jpcode.kits.*;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.InventoryUtil.offerAllCopies;
import static dev.jpcode.kits.KitUtil.runCommands;

public class KitClaimCommand implements Command<ServerCommandSource> {

    private final KitsModStorage storage;

    public KitClaimCommand(KitsModStorage storage) {
        this.storage = storage;
    }

    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        return exec(context.getSource().getPlayerOrThrow(), kitName);
    }

    public int exec(ServerPlayerEntity player, String kitName) {
        PlayerKitData playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        var commandSource = player.getCommandSource();

        var kitRecordOpt = storage.getKitRecord(kitName);
        if (kitRecordOpt.isEmpty()) {
            player.getCommandSource().sendError(Text.literal(
                "Kit '%s' not found".formatted(kitName)
            ));
            return 2;
        }
        var kitRecord = kitRecordOpt.get();
        Kit kit = kitRecord.kit();
        long currentTime = Util.getEpochTimeMs();
        Optional<Long> lastUsed = playerData.getKitUsedTime(kitRecord.permissionName());
        long cooldown = kitRecord.cooldownMs();
        long remainingTime = lastUsed.map(aLong -> (aLong + cooldown) - currentTime).orElse(0L);

        if (!KitPerms.checkKit(commandSource, kitRecord)) {
            commandSource.sendError(Text.of(String.format(
                "Insufficient permissions for kit '%s'.",
                kitName)));
            return -1;
        } else if (cooldown < 0 && lastUsed.isPresent()) {
            commandSource.sendError(Text.of(String.format(
                "Kit '%s' can only be claimed once.",
                kitName)));
            return -2;
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
        playerData.useKit(kitName, kitRecord.cooldownKey());
        offerAllCopies(kit.inventory(), playerInventory);
        if (!kit.commands().isEmpty()) runCommands(player, kit.commands());

        commandSource.sendFeedback(() ->
            Text.of(String.format("Successfully claimed kit '%s'!", kitName)),
            commandSource.getServer().shouldBroadcastConsoleToOps()
        );

        return 1;
    }
}
