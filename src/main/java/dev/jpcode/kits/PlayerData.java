package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

public abstract class PlayerData extends PersistentState {

    private ServerPlayerEntity player;
    private final File saveFile;

    PlayerData(ServerPlayerEntity player, File saveFile) {
        this.player = player;
        this.saveFile = saveFile;
    }

    public void setPlayer(ServerPlayerEntity serverPlayerEntity) {
        this.player = serverPlayerEntity;
    }

    public ServerPlayerEntity getPlayer() {
        return this.player;
    }

    public abstract void fromNbt(NbtCompound nbtCompound3);

    public void save(RegistryWrapper.WrapperLookup wrapperLookup) {
        NbtCompound data = this.toNbt(wrapperLookup);

        try {
            NbtIo.writeCompressed(data, this.saveFile.toPath());
        } catch (IOException e) {
            KitsMod.LOGGER.error("Could not save data {}", this, e);
        }
    }

}
