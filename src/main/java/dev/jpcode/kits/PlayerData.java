package dev.jpcode.kits;

import java.io.File;
import java.io.IOException;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public abstract class PlayerData extends SavedData {

    private ServerPlayer player;
    private final File saveFile;

    PlayerData(ServerPlayer player, File saveFile) {
        this.player = player;
        this.saveFile = saveFile;
    }

    public void setPlayer(ServerPlayer serverPlayerEntity) {
        this.player = serverPlayerEntity;
    }

    public ServerPlayer getPlayer() {
        return this.player;
    }

    public abstract void fromNbt(CompoundTag nbtCompound3);

    public abstract CompoundTag writeNbt(CompoundTag nbt, HolderLookup.Provider wrapperLookup);

    protected CompoundTag toNbt(HolderLookup.Provider wrapperLookup) {
        var tag = new CompoundTag();
        this.writeNbt(tag, wrapperLookup);
        return tag;
    }

    public void save(HolderLookup.Provider wrapperLookup) {
        CompoundTag data = this.toNbt(wrapperLookup);

        try {
            NbtIo.writeCompressed(data, this.saveFile.toPath());
        } catch (IOException e) {
            KitsMod.LOGGER.error("Could not save data {}", this, e);
        }
    }

}
