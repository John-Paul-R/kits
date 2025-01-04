package dev.jpcode.kits.command;

import eu.pb4.sgui.api.gui.SimpleGuiBuilder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import dev.jpcode.kits.Kit;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.KitsMod.CONFIG;
import static dev.jpcode.kits.KitsMod.getAllKitsForPlayer;

public class KitsCommand implements Command<ServerCommandSource> {
    @Override
    public int run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrThrow();
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        var allPlayerKits = getAllKitsForPlayer(player);

        long currentTime = Util.getEpochTimeMs();

        var simpleGuiBuilder = new SimpleGuiBuilder(ScreenHandlerType.GENERIC_9X3, false);
        simpleGuiBuilder.setLockPlayerInventory(true);
        simpleGuiBuilder.setTitle(Text.literal(CONFIG.kitsMenuTitle.getValue()));

        int i = 0;
        for (var kitEntry : allPlayerKits.toList()) {
            var canUseKit = playerData.isKitOnCooldownAtTime(kitEntry, currentTime);

            var defaultItemStack = (
                canUseKit
                    ? kitEntry.getValue()
                        .displayItem()
                        .orElse(Items.EMERALD_BLOCK)
                    : Items.GRAY_CONCRETE_POWDER
                )
                .getDefaultStack();

            simpleGuiBuilder.setSlot(
                i++,
                createKitItemStack(kitEntry.getKey(), kitEntry.getValue(), defaultItemStack),
                (index, type, action, gui) -> {
                    if (type.isLeft) {
                        KitClaimCommand.exec(player, kitEntry.getKey());
                        gui.close();
                    }
                });
        }

        var simpleGui = simpleGuiBuilder.build(player);
        simpleGui.open();

        return Command.SINGLE_SUCCESS;
    }

    private static ItemStack createKitItemStack(String kitName, Kit kit, ItemStack itemStack) {
        ItemStack newItemStack = itemStack.copy();
        newItemStack.set(DataComponentTypes.CUSTOM_NAME, Text.of(kitName));
        return newItemStack;
    }
}
