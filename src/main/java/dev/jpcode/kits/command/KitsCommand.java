package dev.jpcode.kits.command;

import java.util.List;

import eu.pb4.sgui.api.SlotHolder;
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
import net.minecraft.server.network.ServerPlayerEntity;
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

        openKitsScreen(player);

        return Command.SINGLE_SUCCESS;
    }

    interface IScreen {
        void paint(
            ServerPlayerEntity player,
            SimpleGuiBuilder simpleGuiBuilder,
            SlotHolder gui
        );
        void open();
    }

    private void openKitsScreen(ServerPlayerEntity player) {
        var simpleGuiBuilder = new SimpleGuiBuilder(ScreenHandlerType.GENERIC_9X3, false);
        simpleGuiBuilder.setLockPlayerInventory(true);
        simpleGuiBuilder.setTitle(Text.literal(CONFIG.kitsMenuTitle.getValue()));

        paintKitsScreen(player, simpleGuiBuilder);

        var simpleGui = simpleGuiBuilder.build(player);
        simpleGui.open();
    }

    private void paintKitsScreen(ServerPlayerEntity player, SlotHolder gui) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        long currentTime = Util.getEpochTimeMs();

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

    private void openKitRingScreen(ServerPlayerEntity player, String ringName, KitRing ring) {
        var simpleGuiBuilder = new SimpleGuiBuilder(ScreenHandlerType.GENERIC_9X3, false);
        simpleGuiBuilder.setLockPlayerInventory(true);
        simpleGuiBuilder.setTitle(Text.literal(CONFIG.kitsMenuTitle.getValue()));

        paintKitRingScreen(player, simpleGuiBuilder, ringName, ring);

        var simpleGui = simpleGuiBuilder.build(player);
        simpleGui.open();
    }

    private void paintKitRingScreen(ServerPlayerEntity player, SlotHolder gui, String ringName, KitRing ring) {
        var playerData = ((ServerPlayerEntityAccess) player).kits$getPlayerData();
        long currentTime = Util.getEpochTimeMs();

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
            ).getDefaultStack();

        ItemStack newItemStack = defaultItemStack.copy();
        newItemStack.set(DataComponentTypes.ITEM_NAME, Text.of(kit.kitName()));
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
        ).getDefaultStack();

        ItemStack newItemStack = defaultItemStack.copy();
        newItemStack.set(DataComponentTypes.ITEM_NAME, Text.of(ringName));
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
