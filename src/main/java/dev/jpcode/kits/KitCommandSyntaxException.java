package dev.jpcode.kits;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.text.Text;

public class KitCommandSyntaxException extends CommandSyntaxException {
    public KitCommandSyntaxException(Text message) {
        super(KitsMod.COMMAND_EXCEPTION_TYPE, message);
    }
}
