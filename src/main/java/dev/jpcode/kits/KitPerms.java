package dev.jpcode.kits;

import me.lucko.fabric.api.permissions.v0.Permissions;

import net.minecraft.server.command.ServerCommandSource;

public final class KitPerms {

    private KitPerms() {}

    static void init() {
    }

    public static boolean checkKit(ServerCommandSource source, String kitName) {
        return Permissions.check(source, "kits.claim." + kitName, 4);
    }

    public static Func<idk, boolean> require(String permissionNode, int defaultRequireLevel) {
        if (!CONFIG.usePermissions) {
            return true;
        }
        return Permissions.require(permissionNode, defaultRequireLevel);
    }
}
