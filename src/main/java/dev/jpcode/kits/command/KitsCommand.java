package dev.jpcode.kits.command;

import java.util.List;

import eu.pb4.sgui.api.gui.SimpleGuiBuilder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Util;

import dev.jpcode.kits.*;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.KitsMod.CONFIG;

public class KitsCommand implements Command<ServerCommandSource> {

    private final KitSuggestions kitSuggestions;
    private final KitClaimCommand kitClaimCommand;

    public KitsCommand(KitsModStorage storage, KitSuggestions suggestions) {
        this.kitSuggestions = suggestions;
        this.kitClaimCommand = new KitClaimCommand(storage);
    }

    @Override
    public int run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrThrow();
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        var allPlayerKits = kitSuggestions.getAllNonRingKitsForPlayer(player);

        long currentTime = Util.getEpochTimeMs();

        var simpleGuiBuilder = new SimpleGuiBuilder(ScreenHandlerType.GENERIC_9X3, false);
        simpleGuiBuilder.setLockPlayerInventory(true);
        simpleGuiBuilder.setTitle(Text.literal(CONFIG.kitsMenuTitle.getValue()));

        int i = 0;
        for (var kitEntry : allPlayerKits.toList()) {
            simpleGuiBuilder.setSlot(
                i++,
                createKitItemStack(playerData, kitEntry.getKey(), kitEntry.getValue(), currentTime, kitEntry.getKey()),
                (index, type, action, gui) -> {
                    if (type.isLeft) {
                        kitClaimCommand.exec(player, kitEntry.getKey());
                        gui.close();
                    }
                });
        }

        var simpleGui = simpleGuiBuilder.build(player);
        simpleGui.open();

        return Command.SINGLE_SUCCESS;
    }

    private static ItemStack createKitItemStack(
        PlayerKitData playerData,
        String kitName,
        Kit kit,
        long currentTime,
        String cooldownTrackerKey
    ) {
        var kitCooldownRemainingMs = playerData.getKitCooldownRemainingMs(cooldownTrackerKey, kit, currentTime);
        var canUseKit = kitCooldownRemainingMs <= 0;

        var defaultItemStack = (
                canUseKit
                    ? kit.displayItem().orElse(Items.EMERALD_BLOCK)
                    : Items.GRAY_CONCRETE_POWDER
            ).getDefaultStack();

        ItemStack newItemStack = defaultItemStack.copy();
        newItemStack.set(DataComponentTypes.CUSTOM_NAME, Text.of(kitName));
        if (kitCooldownRemainingMs > 0) {
            newItemStack.set(
                DataComponentTypes.LORE,
                new LoreComponent(List.of(
                    Texts.join(
                        List.of(
                            Text.of("Available in"),
                            Text.of(TimeUtil.formatTime(kitCooldownRemainingMs, 2))
                        ),
                        Text.of(" ")
                    )
                ))
            );
        }
        return newItemStack;
    }
}
