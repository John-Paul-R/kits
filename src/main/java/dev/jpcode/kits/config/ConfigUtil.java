package dev.jpcode.kits.config;

import dev.jpcode.kits.data.StorageLocation;

/**
 * Various parsers, etc.
 */
public final class ConfigUtil {
    private ConfigUtil() {}

    public static StorageLocation parseStorageLocation(String storageLocationString) {
        return StorageLocation.valueOf(storageLocationString);
    }
}
