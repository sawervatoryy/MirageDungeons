package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Один появившийся в мире данж. */
public final class Dungeon {

    public enum State { PASTING, ACTIVE, COUNTDOWN, OPEN, LOOTED }

    public static final class ChestInfo {
        public final int x;
        public final int y;
        public final int z;
        public boolean locked;
        public boolean mimic;
        public boolean opened;

        public ChestInfo(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public String id;
    public int level;
    public String world;
    public int originX;
    public int originY;
    public int originZ;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public int guardX;
    public int guardY;
    public int guardZ;
    public State state = State.PASTING;
    public long openAt;
    public long createdAt;
    public long openedAt;
    public long lootedAt;
    public int keysToDrop;
    public UUID boss;
    public final List<ChestInfo> chests = new ArrayList<>();

    public boolean contains(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(world)) {
            return false;
        }
        return contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean isGuard(Block b) {
        return b.getWorld().getName().equals(world)
                && b.getX() == guardX && b.getY() == guardY && b.getZ() == guardZ;
    }

    public ChestInfo chestAt(Block b) {
        if (!b.getWorld().getName().equals(world)) {
            return null;
        }
        for (ChestInfo c : chests) {
            if (c.x == b.getX() && c.y == b.getY() && c.z == b.getZ()) {
                return c;
            }
        }
        return null;
    }

    public boolean isOpenPhase() {
        return state == State.OPEN || state == State.LOOTED;
    }

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }
}
