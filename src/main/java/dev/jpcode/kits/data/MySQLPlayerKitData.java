package dev.jpcode.kits.data;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;

import dev.jpcode.kits.KitsMod;

public class MySQLPlayerKitData implements IPlayerKitData {
    private ServerPlayerEntity player;

    public MySQLPlayerKitData(ServerPlayerEntity player) {
        this.player = player;
    }

    public void useKit(String kitName) {
        try {
            PreparedStatement pStmt = KitsMod.conn.prepareStatement("SELECT timestamp FROM lastUsed WHERE uuid = ? AND kitName = ?");
            pStmt.setBytes(1, asBytes(player.getUuid()));
            pStmt.setString(2, kitName);

            pStmt.execute();
            ResultSet rs = pStmt.getResultSet();

            if (rs.next()) { // true if there is a row
                pStmt = KitsMod.conn.prepareStatement("UPDATE lastUsed SET timestamp = ? WHERE uuid = ? AND kitName = ?");
                pStmt.setTimestamp(1, new Timestamp(Util.getEpochTimeMs()));
                pStmt.setBytes(2, asBytes(player.getUuid()));
                pStmt.setString(3, kitName);
            } else {
                pStmt = KitsMod.conn.prepareStatement("INSERT INTO lastUsed(uuid, kitName, timestamp) values(?, ?, ?)");
                pStmt.setTimestamp(3, new Timestamp(Util.getEpochTimeMs()));
                pStmt.setBytes(1, asBytes(player.getUuid()));
                pStmt.setString(2, kitName);
            }

            pStmt.execute();

        } catch (SQLException ex) {
            KitsMod.LOGGER.error("SQLException: " + ex.getMessage());
            KitsMod.LOGGER.error("SQLState: " + ex.getSQLState());
            KitsMod.LOGGER.error("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        }
    }

    public long getKitUsedTime(String kitName) {
        try {
            PreparedStatement pStmt = KitsMod.conn.prepareStatement("SELECT timestamp FROM lastUsed WHERE uuid = ? AND kitName = ?");
            pStmt.setBytes(1, asBytes(player.getUuid()));
            pStmt.setString(2, kitName);

            pStmt.execute();
            ResultSet rs = pStmt.getResultSet();

            if (rs.next()) { // true if there is a row
                Timestamp timestamp = rs.getTimestamp("timestamp");
                return timestamp.getTime();
            } else {
                return 0;
            }
        } catch (SQLException ex) {
            KitsMod.LOGGER.error("SQLException: " + ex.getMessage());
            KitsMod.LOGGER.error("SQLState: " + ex.getSQLState());
            KitsMod.LOGGER.error("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        }
    }

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

    public void resetKitCooldown(String kitName) {
        try {
            PreparedStatement pStmt = KitsMod.conn.prepareStatement("DELETE FROM lastused WHERE uuid = ? AND kitName = ?");
            pStmt.setBytes(1, asBytes(player.getUuid()));
            pStmt.setString(2, kitName);

            pStmt.execute();
        } catch (SQLException ex) {
            KitsMod.LOGGER.error("SQLException: " + ex.getMessage());
            KitsMod.LOGGER.error("SQLState: " + ex.getSQLState());
            KitsMod.LOGGER.error("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        }
    }

    public void resetAllKits() {
        try {
            PreparedStatement pStmt = KitsMod.conn.prepareStatement("DELETE FROM lastused WHERE uuid = ?");
            pStmt.setBytes(1, asBytes(player.getUuid()));

            pStmt.execute();
        } catch (SQLException ex) {
            KitsMod.LOGGER.error("SQLException: " + ex.getMessage());
            KitsMod.LOGGER.error("SQLState: " + ex.getSQLState());
            KitsMod.LOGGER.error("VendorError: " + ex.getErrorCode());
            throw new RuntimeException(ex);
        }
    }

    public void setPlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    // stolen from Brice Roncace https://stackoverflow.com/a/29836273/9080495
    private static byte[] asBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
}
