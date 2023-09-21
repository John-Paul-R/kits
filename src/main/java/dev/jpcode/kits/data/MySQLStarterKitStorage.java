package dev.jpcode.kits.data;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import net.minecraft.server.network.ServerPlayerEntity;

import dev.jpcode.kits.KitsMod;

import static dev.jpcode.kits.data.MySQLKitCooldownStorage.asBytes;

public class MySQLStarterKitStorage implements StarterKitStorageProvider {
    private ServerPlayerEntity player;

    public MySQLStarterKitStorage(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public boolean hasReceivedStarterKit() {
        try {
            PreparedStatement pStmt = KitsMod.conn.prepareStatement("SELECT claimed FROM starterkits WHERE uuid = ?");
            pStmt.setBytes(1, asBytes(player.getUuid()));

            pStmt.execute();
            ResultSet rs = pStmt.getResultSet();

            if (rs.next()) { // true if there is a row
                return rs.getBoolean("claimed");
            } else {
                return false;
            }
        } catch (SQLException ex) {
            KitsMod.LOGGER.error("SQLException: " + ex.getMessage());
            KitsMod.LOGGER.error("SQLState: " + ex.getSQLState());
            KitsMod.LOGGER.error("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setHasReceivedStarterKit(boolean hasReceivedStarterKit) {
        try {
            PreparedStatement pStmt = KitsMod.conn.prepareStatement("SELECT claimed FROM starterkits WHERE uuid = ?");
            pStmt.setBytes(1, asBytes(player.getUuid()));

            pStmt.execute();
            ResultSet rs = pStmt.getResultSet();

            if (rs.next()) { // true if there is a row
                pStmt = KitsMod.conn.prepareStatement("UPDATE starterkits SET claimed = ? WHERE uuid = ?");
                pStmt.setBytes(2, asBytes(player.getUuid()));
                pStmt.setBoolean(1, hasReceivedStarterKit);
            } else {
                pStmt = KitsMod.conn.prepareStatement("INSERT INTO starterkits(uuid, claimed) values(?, ?)");
                pStmt.setBytes(1, asBytes(player.getUuid()));
                pStmt.setBoolean(2, hasReceivedStarterKit);
            }

            pStmt.execute();

        } catch (SQLException ex) {
            KitsMod.LOGGER.error("SQLException: " + ex.getMessage());
            KitsMod.LOGGER.error("SQLState: " + ex.getSQLState());
            KitsMod.LOGGER.error("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        this.player = player;
    }
}
