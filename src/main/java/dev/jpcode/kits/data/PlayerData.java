package dev.jpcode.kits.data;

import java.io.File;

import net.minecraft.nbt.NbtCompound;
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

    public File getSaveFile() {
        return this.saveFile;
    }

    public void save() {
        super.save(saveFile);
    }

}
