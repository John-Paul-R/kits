package dev.jpcode.kits.command;

import java.util.Optional;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import dev.jpcode.kits.*;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.InventoryUtil.offerAllCopies;
import static dev.jpcode.kits.KitUtil.runCommands;

public class KitClaimCommand implements Command<CommandSourceStack> {

    private final KitsModStorage storage;

    public KitClaimCommand(KitsModStorage storage) {
        this.storage = storage;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String kitName = StringArgumentType.getString(context, "kit_name");
        return exec(context.getSource().getPlayerOrException(), kitName);
    }

    public int exec(ServerPlayer player, String kitName) {
        PlayerKitData playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        var commandSource = player.createCommandSourceStack();

        var kitRecordOpt = storage.getKitRecord(kitName);
        if (kitRecordOpt.isEmpty()) {
            player.createCommandSourceStack().sendFailure(Component.literal(
                "Kit '%s' not found".formatted(kitName)
            ));
            return 2;
        }
        var kitRecord = kitRecordOpt.get();
        Kit kit = kitRecord.kit();
        long currentTime = Util.getEpochMillis();
        Optional<Long> lastUsed = playerData.getKitUsedTime(kitRecord.permissionName());
        long cooldown = kitRecord.cooldownMs();
        long remainingTime = lastUsed.map(aLong -> (aLong + cooldown) - currentTime).orElse(0L);

        if (!KitPerms.checkKit(commandSource, kitRecord)) {
            commandSource.sendFailure(Component.nullToEmpty(String.format(
                "Insufficient permissions for kit '%s'.",
                kitName)));
            return -1;
        }

        // Check permanent choice for rings
        if (kitRecord.ring() != null && kitRecord.ring().permanentChoice()) {
            if (!playerData.mayClaimFromRing(kitRecord.ringName(), kitName)) {
                commandSource.sendFailure(Component.nullToEmpty(String.format(
                    "You have already chosen '%s' as your kit for ring '%s'.",
                    playerData.getRingChoice(kitRecord.ringName()),
                    kitRecord.ringName())));
                return -1;
            }
        }

        if (cooldown < 0 && lastUsed.isPresent()) {
            commandSource.sendFailure(Component.nullToEmpty(String.format(
                "Kit '%s' can only be claimed once.",
                kitName)));
            return -2;
        } else if (remainingTime > 0) {
            commandSource.sendFailure(Component.nullToEmpty(
                String.format(
                    "Kit '%s' is on cooldown. %s remaining.",
                    kitName,
                    TimeUtil.formatTime(remainingTime)
                )));
            return -2;
        }

        Inventory playerInventory = player.getInventory();
        playerData.useKit(kitName, kitRecord.cooldownKey());

        // Record ring selection if this is a permanent choice ring
        if (kitRecord.ring() != null && kitRecord.ring().permanentChoice()) {
            playerData.recordRingSelection(kitRecord.ringName(), kitName);
        }

        offerAllCopies(kit.inventory(), playerInventory);
        if (!kit.commands().isEmpty()) runCommands(player, kit.commands());

        commandSource.sendSuccess(() ->
            Component.nullToEmpty(String.format("Successfully claimed kit '%s'!", kitName)),
            commandSource.getServer().shouldInformAdmins()
        );

        return 1;
    }
}
