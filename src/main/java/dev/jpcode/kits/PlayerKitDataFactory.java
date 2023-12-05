package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.Level;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PlayerKitDataFactory {

    private PlayerKitDataFactory() {}

    public static PlayerKitData create(ServerPlayerEntity player) {
        File saveFile = getPlayerDataFile(player);
        PlayerKitData pData = new PlayerKitData(player, saveFile);
        if (Files.exists(saveFile.toPath()) && saveFile.length() != 0) {
            try {
                NbtCompound nbtCompound = NbtIo.readCompressed(saveFile.toPath(), NbtTagSizeTracker.ofUnlimitedBytes());
                pData.fromNbt(nbtCompound);

            } catch (IOException e) {
                KitsMod.LOGGER.warn("Failed to load kits player data for {" + player.getName().getString() + "}");
                e.printStackTrace();
            }
        }
        pData.markDirty();
        return pData;
    }

    private static File getPlayerDataFile(ServerPlayerEntity player) {
        Path dataDirectoryPath;
        File playerDataFile = null;
        try {
            try {
                dataDirectoryPath = Files.createDirectories(KitsMod.getUserDataDirDir());
            } catch (NullPointerException e) {
                dataDirectoryPath = Files.createDirectories(Paths.get("./world/modplayerdata/"));
                KitsMod.LOGGER.log(Level.WARN, "Session save path could not be found. Defaulting to ./world/modplayerdata");
            }
            playerDataFile = dataDirectoryPath.resolve(player.getUuidAsString() + ".nbt").toFile();
            playerDataFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return playerDataFile;
    }

}
