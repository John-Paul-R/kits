package dev.jpcode.kits;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.util.WorldSavePath;

import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PlayerKitDataFactory {

    public static PlayerKitData create(ServerPlayerEntity player) {
        File saveFile = getPlayerDataFile(player);
        PlayerKitData pData = new PlayerKitData(player, saveFile);
        if (Files.exists(saveFile.toPath()) && saveFile.length() != 0) {
            try {
                NbtCompound NbtCompound3 = NbtIo.readCompressed(new FileInputStream(saveFile));
                pData.fromNbt(NbtCompound3);

            } catch (IOException e) {
                KitsMod.LOGGER.warn("Failed to load kits player data for {"+player.getName().getString()+"}");
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
                dataDirectoryPath = Files.createDirectories(KitsMod.getUserDataDirDir().resolve(player.getUuidAsString() + ".nbt"));
            } catch (NullPointerException e){
                dataDirectoryPath = Files.createDirectories(Paths.get("./world/modplayerdata/"));
                KitsMod.LOGGER.log(Level.WARN, "Session save path could not be found. Defaulting to ./world/modplayerdata");
            }
            playerDataFile = dataDirectoryPath.resolve(player.getUuidAsString()+".nbt").toFile();
            playerDataFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return playerDataFile;
    }

}
