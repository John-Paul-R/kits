package dev.jpcode.kits;

import net.minecraft.entity.player.PlayerInventory;

public record Kit(PlayerInventory inventory, long cooldown) {
}
