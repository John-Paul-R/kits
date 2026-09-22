package dev.jpcode.kits;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.network.chat.Component;

public class KitCommandSyntaxException extends CommandSyntaxException {
    public KitCommandSyntaxException(Component message) {
        super(KitsMod.COMMAND_EXCEPTION_TYPE, message);
    }
}
