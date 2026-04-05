package dev.jpcode.kits.command;

import java.util.List;

import eu.pb4.sgui.api.SlotHolder;
import eu.pb4.sgui.api.gui.SimpleGui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import dev.jpcode.kits.*;
import dev.jpcode.kits.access.ServerPlayerEntityAccess;

import static dev.jpcode.kits.KitsMod.CONFIG;

public class KitsCommand implements Command<CommandSourceStack> {

    private final KitSuggestions kitSuggestions;
    private final KitClaimCommand kitClaimCommand;

    public KitsCommand(KitsModStorage storage, KitSuggestions suggestions) {
        this.kitSuggestions = suggestions;
        this.kitClaimCommand = new KitClaimCommand(storage);
    }

    @Override
    public int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();

        openKitsScreen(player);

        return Command.SINGLE_SUCCESS;
    }

    private void openKitsScreen(ServerPlayer player) {
        var simpleGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        simpleGui.setLockPlayerInventory(true);
        simpleGui.setTitle(Component.literal(CONFIG.kitsMenuTitle.getValue()));

        paintKitsScreen(player, simpleGui);

        simpleGui.open();
    }

    private void paintKitsScreen(ServerPlayer player, SlotHolder gui) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        long currentTime = Util.getEpochMillis();

        var nonRingKitsForPlayer = kitSuggestions.getAllNonRingKitsForPlayer(player);
        int i = 0;
        for (var kitRecord : nonRingKitsForPlayer.toList()) {
            gui.setSlot(
                i++,
                createKitItemStack(playerData, kitRecord, currentTime),
                (index, type, action, gui2) -> {
                    if (type.isLeft) {
                        kitClaimCommand.exec(player, kitRecord.kitName());
                        gui2.close();
                    }
                });
        }

        var kitRingsForPlayer = kitSuggestions.getAllKitRingsForPlayer(player);
        for (var ringEntry : kitRingsForPlayer.toList()) {
            gui.setSlot(
                i++,
                createKitRingItemStack(playerData, ringEntry.getKey(), ringEntry.getValue(), currentTime),
                (index, type, action, gui2) -> {
                    if (type.isLeft) {
                        gui2.close();
                        openKitRingScreen(player, ringEntry.getKey(), ringEntry.getValue());
                    }
                });
        }
    }

    private void openKitRingScreen(ServerPlayer player, String ringName, KitRing ring) {
        var simpleGui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        simpleGui.setLockPlayerInventory(true);
        simpleGui.setTitle(Component.literal(CONFIG.kitsMenuTitle.getValue()));

        paintKitRingScreen(player, simpleGui, ringName, ring);

        simpleGui.open();
    }

    private void paintKitRingScreen(ServerPlayer player, SlotHolder gui, String ringName, KitRing ring) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        long currentTime = Util.getEpochMillis();

        int i = 0;
        for (var kit : ring.kits().entrySet()) {
            gui.setSlot(
                i++,
                createKitItemStack(playerData, new KitsModStorage.KitRecord(ringName, ring, kit.getKey(), kit.getValue()), currentTime),
                (index, type, action, gui2) -> {
                    if (type.isLeft) {
                        kitClaimCommand.exec(player, kit.getKey());
                        gui2.close();
                    }
                });
        }
    }

    private static ItemStack createKitItemStack(
        PlayerKitData playerData,
        KitsModStorage.KitRecord kit,
        long currentTime
    ) {
        var kitCooldownRemainingMs = playerData.getKitCooldownRemainingMs(kit, currentTime);
        var canUseKit = kitCooldownRemainingMs <= 0;

        var defaultItemStack = (
                canUseKit
                    ? kit.kit().displayItem().orElse(Items.EMERALD_BLOCK)
                    : Items.GRAY_CONCRETE_POWDER
            ).getDefaultInstance();

        ItemStack newItemStack = defaultItemStack.copy();
        newItemStack.set(DataComponents.ITEM_NAME, Component.nullToEmpty(kit.kitName()));
        if (kitCooldownRemainingMs > 0) {
            newItemStack.set(
                DataComponents.LORE,
                new ItemLore(List.of(
                    ComponentUtils.formatList(
                        List.of(
                            Component.nullToEmpty("Available in"),
                            Component.nullToEmpty(TimeUtil.formatTime(kitCooldownRemainingMs, 2))
                        ),
                        Component.nullToEmpty(" ")
                    )
                ))
            );
        }
        return newItemStack;
    }

    private static ItemStack createKitRingItemStack(
        PlayerKitData playerData,
        String ringName,
        KitRing ring,
        long currentTime
    ) {
        var kitCooldownRemainingMs = playerData.getKitCooldownRemainingMs(ringName, ring.cooldownMs(), currentTime);
        var canUseKit = kitCooldownRemainingMs <= 0;

        var defaultItemStack = (
            canUseKit
                ? ring.displayItem().orElse(Items.LIME_CONCRETE)
                : Items.GRAY_CONCRETE_POWDER
        ).getDefaultInstance();

        ItemStack newItemStack = defaultItemStack.copy();
        newItemStack.set(DataComponents.ITEM_NAME, Component.nullToEmpty(ringName));
        if (kitCooldownRemainingMs > 0) {
            newItemStack.set(
                DataComponents.LORE,
                new ItemLore(List.of(
                    ComponentUtils.formatList(
                        List.of(
                            Component.nullToEmpty("Available in"),
                            Component.nullToEmpty(TimeUtil.formatTime(kitCooldownRemainingMs, 2))
                        ),
                        Component.nullToEmpty(" ")
                    )
                ))
            );
        }
        return newItemStack;
    }
}
