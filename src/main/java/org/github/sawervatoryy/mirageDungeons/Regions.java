package org.github.sawervatoryy.mirageDungeons;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/** Приваты данжей: регионы WorldGuard с флагами из config.yml (worldguard.flags-before / flags-after). */
public final class Regions {

    private final MirageDungeons plugin;

    public Regions(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    public String id(Dungeon d) {
        return plugin.getConfig().getString("worldguard.region-prefix", "mirage_") + d.id;
    }

    private RegionManager manager(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            return null;
        }
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(w));
    }

    /** Создаёт регион (если его нет) и выставляет флаги: after=false — до взрыва, true — после. */
    public void sync(Dungeon d, LevelConfig lvl, boolean after) {
        if (lvl == null || !lvl.wgMode) {
            return;
        }
        try {
            RegionManager rm = manager(d.world);
            if (rm == null) {
                plugin.getLogger().warning("WorldGuard: нет менеджера регионов для мира " + d.world);
                return;
            }
            String id = id(d);
            ProtectedRegion region = rm.getRegion(id);
            boolean isNew = region == null;
            if (isNew) {
                region = new ProtectedCuboidRegion(id,
                        BlockVector3.at(d.minX, d.minY, d.minZ),
                        BlockVector3.at(d.maxX, d.maxY, d.maxZ));
            }
            region.setPriority(lvl.wgPriority);
            region.setFlags(new HashMap<Flag<?>, Object>());
            List<String> flags = after ? lvl.flagsAfter : lvl.flagsBefore;
            for (String line : flags) {
                applyFlag(region, line);
            }
            if (isNew) {
                rm.addRegion(region);
            }
            plugin.getLogger().info("WorldGuard: регион " + id + " " + (isNew ? "создан" : "обновлён")
                    + " (" + (after ? "после взрыва" : "до взрыва") + ", флагов: " + flags.size() + ")");
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "WorldGuard: не удалось настроить регион данжа " + d.id, e);
        }
    }

    public void delete(Dungeon d) {
        try {
            RegionManager rm = manager(d.world);
            if (rm != null) {
                rm.removeRegion(id(d));
            }
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "WorldGuard: не удалось удалить регион данжа " + d.id, e);
        }
    }

    public boolean exists(Dungeon d) {
        try {
            RegionManager rm = manager(d.world);
            return rm != null && rm.hasRegion(id(d));
        } catch (Throwable e) {
            return false;
        }
    }

    /** Формат строки: «имя_флага значение», например «tnt deny» или «greeting &bПривет». */
    private void applyFlag(ProtectedRegion region, String line) {
        String t = line.trim();
        if (t.isEmpty()) {
            return;
        }
        int sp = t.indexOf(' ');
        String name = (sp < 0 ? t : t.substring(0, sp)).toLowerCase();
        String value = sp < 0 ? "" : t.substring(sp + 1).trim();
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name);
        if (flag == null) {
            plugin.getLogger().warning("WorldGuard: неизвестный флаг «" + name + "»");
            return;
        }
        try {
            setFlag(region, flag, value);
        } catch (InvalidFlagFormat e) {
            plugin.getLogger().warning("WorldGuard: неверное значение флага «" + line + "»: " + e.getMessage());
        }
    }

    private <V> void setFlag(ProtectedRegion region, Flag<V> flag, String value) throws InvalidFlagFormat {
        V parsed = flag.parseInput(FlagContext.create().setInput(value).build());
        region.setFlag(flag, parsed);
    }
}
