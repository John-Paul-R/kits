package dev.jpcode.kits;

import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import me.lucko.fabric.api.permissions.v0.Permissions;

import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;

import net.fabricmc.fabric.api.util.TriState;

public final class KitPerms {

    private KitPerms() {}

    static void init() {
    }

    private static boolean isSuperAdmin(CommandSource source) {
        return source.hasPermissionLevel(4);
    }

    public static boolean checkKit(ServerCommandSource source, String kitName) {
        return Permissions.check(source, "kits.claim." + kitName);
    }

}
