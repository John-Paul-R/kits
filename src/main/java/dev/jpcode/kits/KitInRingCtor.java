package dev.jpcode.kits;

@FunctionalInterface
public interface KitInRingCtor {
    Kit createKit(long cooldownMs);
}
