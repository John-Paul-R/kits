package dev.jpcode.kits;

import me.lucko.fabric.api.permissions.v0.Permissions;

import net.minecraft.commands.CommandSourceStack;

public final class KitPerms {

    private KitPerms() {}

    static void init() {
    }

    public static boolean checkKit(CommandSourceStack source, String kitPermissionKey) {
        return Permissions.check(source, "kits.claim." + kitPermissionKey, 4);
    }

    public static boolean checkKit(CommandSourceStack source, KitsModStorage.KitRecord kitRecord) {
        return Permissions.check(source, "kits.claim." + kitRecord.permissionName(), 4);
    }
}
