package org.github.sawervatoryy.mirageDungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Вся логика: спавн, вставка схематик, откат, открытие, стражи, таймеры, расписание. */
public final class DungeonManager {

    private record MobJob(LevelConfig.MobSpec spec, int keys) { }

    private final MirageDungeons plugin;
    private final Map<String, Dungeon> dungeons = new LinkedHashMap<>();
    private final Map<Integer, LevelConfig> levels = new TreeMap<>();
    private final Map<String, Schem> schemCache = new HashMap<>();
    private final Map<String, Long> schemStamp = new HashMap<>();
    private final Map<String, BlockData> blockCache = new HashMap<>();
    private final Map<String, BossBar> countdownBars = new HashMap<>();
    private final Map<String, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, String> playerRegion = new HashMap<>();
    private final Map<UUID, Long> lastTrap = new HashMap<>();
    private final Random rnd = new Random();
    private File dataFile;
    private YamlConfiguration data;
    private BukkitTask task;

    public DungeonManager(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ загрузка и сохранение

    public void reloadLevels() {
        levels.clear();
        ConfigurationSection ls = plugin.getConfig().getConfigurationSection("levels");
        if (ls == null) {
            return;
        }
        for (String k : ls.getKeys(false)) {
            try {
                int id = Integer.parseInt(k);
                ConfigurationSection s = ls.getConfigurationSection(k);
                if (s != null) {
                    levels.put(id, LevelConfig.load(id, plugin.getConfig(), s));
                }
            } catch (NumberFormatException ignored) {
                // не числовой ключ
            }
        }
    }

    public void load() {
        reloadLevels();
        new File(plugin.getDataFolder(), "snapshots").mkdirs();
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = data.getConfigurationSection("dungeons");
        List<Dungeon> broken = new ArrayList<>();
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Dungeon d = new Dungeon();
                d.id = id;
                d.level = s.getInt("level");
                d.world = s.getString("world", "world");
                d.originX = s.getInt("origin-x");
                d.originY = s.getInt("origin-y");
                d.originZ = s.getInt("origin-z");
                d.minX = s.getInt("min-x");
                d.minY = s.getInt("min-y");
                d.minZ = s.getInt("min-z");
                d.maxX = s.getInt("max-x");
                d.maxY = s.getInt("max-y");
                d.maxZ = s.getInt("max-z");
                d.guardX = s.getInt("guard-x");
                d.guardY = s.getInt("guard-y");
                d.guardZ = s.getInt("guard-z");
                d.openAt = s.getLong("open-at");
                d.createdAt = s.getLong("created-at");
                d.openedAt = s.getLong("opened-at");
                d.lootedAt = s.getLong("looted-at");
                d.keysToDrop = s.getInt("keys");
                try {
                    d.state = Dungeon.State.valueOf(s.getString("state", "ACTIVE"));
                } catch (IllegalArgumentException e) {
                    d.state = Dungeon.State.ACTIVE;
                }
                String b = s.getString("boss");
                if (b != null) {
                    try {
                        d.boss = UUID.fromString(b);
                    } catch (IllegalArgumentException ignored) {
                        d.boss = null;
                    }
                }
                for (String c : s.getStringList("chests")) {
                    String[] p = c.split(",");
                    if (p.length < 6) {
                        continue;
                    }
                    Dungeon.ChestInfo ci = new Dungeon.ChestInfo(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    ci.locked = "1".equals(p[3]);
                    ci.mimic = "1".equals(p[4]);
                    ci.opened = "1".equals(p[5]);
                    d.chests.add(ci);
                }
                dungeons.put(id, d);
                if (d.state == Dungeon.State.PASTING) {
                    broken.add(d);
                }
            }
        }
        // Если сервер остановился во время вставки, откатываем недоделанный данж.
        for (Dungeon d : broken) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> remove(d, true, null), 40L);
        }
    }

    public void save() {
        if (data == null) {
            return;
        }
        data.set("dungeons", null);
        for (Dungeon d : dungeons.values()) {
            String p = "dungeons." + d.id + ".";
            data.set(p + "level", d.level);
            data.set(p + "world", d.world);
            data.set(p + "origin-x", d.originX);
            data.set(p + "origin-y", d.originY);
            data.set(p + "origin-z", d.originZ);
            data.set(p + "min-x", d.minX);
            data.set(p + "min-y", d.minY);
            data.set(p + "min-z", d.minZ);
            data.set(p + "max-x", d.maxX);
            data.set(p + "max-y", d.maxY);
            data.set(p + "max-z", d.maxZ);
            data.set(p + "guard-x", d.guardX);
            data.set(p + "guard-y", d.guardY);
            data.set(p + "guard-z", d.guardZ);
            data.set(p + "state", d.state.name());
            data.set(p + "open-at", d.openAt);
            data.set(p + "created-at", d.createdAt);
            data.set(p + "opened-at", d.openedAt);
            data.set(p + "looted-at", d.lootedAt);
            data.set(p + "keys", d.keysToDrop);
            data.set(p + "boss", d.boss == null ? null : d.boss.toString());
            List<String> cs = new ArrayList<>();
            for (Dungeon.ChestInfo c : d.chests) {
                cs.add(c.x + "," + c.y + "," + c.z + "," + (c.locked ? 1 : 0) + ","
                        + (c.mimic ? 1 : 0) + "," + (c.opened ? 1 : 0));
            }
            data.set(p + "chests", cs);
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public void startTasks() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        // Восстанавливаем регионы WorldGuard, если их нет (например, удалили regions.yml)
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureRegions(false), 60L);
    }

    public int ensureRegions() {
        return ensureRegions(false);
    }

    /**
     * Создаёт недостающие регионы WorldGuard для всех данжей. С refresh=true заново выставляет флаги
     * у существующих регионов (например, после смены флагов в конфиге). Возвращает число изменённых.
     */
    public int ensureRegions(boolean refresh) {
        int n = 0;
        for (Dungeon d : dungeons.values()) {
            if (d.state == Dungeon.State.PASTING) {
                continue;
            }
            LevelConfig l = levels.get(d.level);
            if (l == null || !l.wgMode) {
                continue;
            }
            // после рейда регион не нужен, если включено remove-on-raid
            boolean need = d.state == Dungeon.State.ACTIVE || !l.wgRemoveOnRaid;
            boolean has = plugin.regions().exists(d);
            if (need && (!has || refresh)) {
                plugin.regions().sync(d, l, d.state != Dungeon.State.ACTIVE);
                n++;
            } else if (!need && has) {
                plugin.regions().delete(d);
            }
        }
        return n;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (BossBar b : countdownBars.values()) {
            b.removeAll();
        }
        for (BossBar b : bossBars.values()) {
            b.removeAll();
        }
        save();
    }

    // ------------------------------------------------------------------ доступ

    public Map<Integer, LevelConfig> levels() {
        return levels;
    }

    public LevelConfig level(int id) {
        return levels.get(id);
    }

    public Map<String, Dungeon> dungeons() {
        return dungeons;
    }

    public Dungeon at(Location l) {
        for (Dungeon d : dungeons.values()) {
            if (d.contains(l)) {
                return d;
            }
        }
        return null;
    }

    public Dungeon at(Block b) {
        return at(b.getLocation());
    }

    public void fire(String event, Dungeon d, Player p, Location at) {
        plugin.events().fire(event, levels.get(d.level), d, p, at);
    }

    public boolean canModify(Dungeon d, String flag) {
        LevelConfig l = levels.get(d.level);
        if (l == null) {
            return false;
        }
        String key = (d.isOpenPhase() ? "after-open." : "before-open.") + flag;
        return l.prot.getOrDefault(key, false);
    }

    /** Проверка белых и чёрных списков материалов для ломания и установки блоков. */
    public boolean materialAllowed(Dungeon d, boolean place, Material mat) {
        LevelConfig l = levels.get(d.level);
        if (l == null) {
            return false;
        }
        Set<Material> white = place ? l.placeWhite : l.breakWhite;
        Set<Material> black = place ? l.placeBlack : l.breakBlack;
        if (!white.isEmpty() && !white.contains(mat)) {
            return false;
        }
        return !black.contains(mat);
    }

    public List<Dungeon> activeUnopened(World w) {
        List<Dungeon> out = new ArrayList<>();
        for (Dungeon d : dungeons.values()) {
            if (d.state == Dungeon.State.ACTIVE && d.world.equals(w.getName())) {
                out.add(d);
            }
        }
        return out;
    }

    public Location guardLocation(Dungeon d) {
        World w = Bukkit.getWorld(d.world);
        return new Location(w, d.guardX + 0.5, d.guardY + 0.5, d.guardZ + 0.5);
    }

    // ------------------------------------------------------------------ схематики

    public File schemFile(String name) {
        File dir = new File(plugin.getDataFolder(), "schematics");
        File f = new File(dir, name);
        if (!f.exists() && !name.toLowerCase().endsWith(".schem")) {
            f = new File(dir, name + ".schem");
        }
        return f;
    }

    public Schem loadSchem(String name) {
        File f = schemFile(name);
        if (!f.exists()) {
            return null;
        }
        Long stamp = schemStamp.get(name);
        if (stamp != null && stamp == f.lastModified() && schemCache.containsKey(name)) {
            return schemCache.get(name);
        }
        try {
            Schem s = Schem.load(f);
            schemCache.put(name, s);
            schemStamp.put(name, f.lastModified());
            return s;
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось прочитать схематику " + name + ": " + e.getMessage());
            return null;
        }
    }

    private BlockData blockData(String s) {
        BlockData bd = blockCache.get(s);
        if (bd == null) {
            try {
                bd = Bukkit.createBlockData(s);
            } catch (IllegalArgumentException e) {
                bd = Bukkit.createBlockData(Material.AIR);
            }
            blockCache.put(s, bd);
        }
        return bd;
    }

    private int countActive(World w, int levelId) {
        int n = 0;
        for (Dungeon d : dungeons.values()) {
            if (d.world.equals(w.getName()) && d.state != Dungeon.State.LOOTED
                    && (levelId < 0 || d.level == levelId)) {
                n++;
            }
        }
        return n;
    }

    /** Случайный уровень по весам. Учитывает enabled, наличие схематики, миры и лимит max-active. */
    public LevelConfig pickLevel(World w) {
        int total = 0;
        List<LevelConfig> ok = new ArrayList<>();
        for (LevelConfig l : levels.values()) {
            if (!l.enabled || l.weight <= 0 || !schemFile(l.schematic).exists()) {
                continue;
            }
            if (w != null) {
                if (!l.allowsWorld(w.getName())) {
                    continue;
                }
                if (l.maxActive > 0 && countActive(w, l.id) >= l.maxActive) {
                    continue;
                }
            }
            ok.add(l);
            total += l.weight;
        }
        if (ok.isEmpty()) {
            return null;
        }
        int r = rnd.nextInt(total);
        for (LevelConfig l : ok) {
            r -= l.weight;
            if (r < 0) {
                return l;
            }
        }
        return ok.get(0);
    }

    // ------------------------------------------------------------------ вспомогательное

    private void loadChunks(World w, int minX, int minZ, int maxX, int maxZ, Runnable then) {
        List<CompletableFuture<?>> fs = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                fs.add(w.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, then));
    }

    private void runBatches(int total, IntConsumer step, Runnable done) {
        int perTick = Math.max(200, plugin.getConfig().getInt("paste.blocks-per-tick", 4000));
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                try {
                    int end = Math.min(total, i + perTick);
                    for (; i < end; i++) {
                        step.accept(i);
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Ошибка при вставке/откате: " + e);
                    i = total;
                }
                if (i >= total) {
                    cancel();
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private String newId(int level) {
        return "L" + level + "-" + UUID.randomUUID().toString().substring(0, 5);
    }

    // ------------------------------------------------------------------ спавн

    /** Ищет подходящую точку по правилам уровня и спавнит данж. cb получит null при неудаче. */
    public void spawnRandom(LevelConfig lvl, World w, int attempts, Consumer<Dungeon> cb) {
        if (attempts <= 0) {
            cb.accept(null);
            return;
        }
        Runnable retry = () -> spawnRandom(lvl, w, attempts - 1, cb);
        int minX = Math.min(lvl.spawnMinX, lvl.spawnMaxX);
        int maxX = Math.max(lvl.spawnMinX, lvl.spawnMaxX);
        int minZ = Math.min(lvl.spawnMinZ, lvl.spawnMaxZ);
        int maxZ = Math.max(lvl.spawnMinZ, lvl.spawnMaxZ);
        int x = minX + rnd.nextInt(maxX - minX + 1);
        int z = minZ + rnd.nextInt(maxZ - minZ + 1);
        if (lvl.respectBorder && !w.getWorldBorder().isInside(new Location(w, x + 0.5, 64, z + 0.5))) {
            retry.run();
            return;
        }
        for (Dungeon d : dungeons.values()) {
            if (d.world.equals(w.getName())) {
                double dx = d.centerX() - x;
                double dz = d.centerZ() - z;
                if (dx * dx + dz * dz < lvl.minDistanceBetween * lvl.minDistanceBetween) {
                    retry.run();
                    return;
                }
            }
        }
        if (lvl.minDistanceFromSpawn > 0) {
            Location sp = w.getSpawnLocation();
            double dx = sp.getBlockX() - x;
            double dz = sp.getBlockZ() - z;
            if (dx * dx + dz * dz < lvl.minDistanceFromSpawn * lvl.minDistanceFromSpawn) {
                retry.run();
                return;
            }
        }
        w.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, ex) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int top = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    if (top < lvl.minY || top > lvl.maxY) {
                        retry.run();
                        return;
                    }
                    Block surface = w.getBlockAt(x, top, z);
                    if (lvl.avoidLiquid && (surface.isLiquid() || w.getBlockAt(x, top + 1, z).isLiquid())) {
                        retry.run();
                        return;
                    }
                    if (lvl.groundBlacklist.contains(surface.getType())) {
                        retry.run();
                        return;
                    }
                    if (!lvl.biomeWhitelist.isEmpty() || !lvl.biomeBlacklist.isEmpty()) {
                        String key = w.getBiome(x, top, z).getKey().getKey();
                        if ((!lvl.biomeWhitelist.isEmpty() && !lvl.biomeWhitelist.contains(key))
                                || lvl.biomeBlacklist.contains(key)) {
                            retry.run();
                            return;
                        }
                    }
                    paste(lvl, w, x, top + 1 - lvl.sink, z, cb);
                }));
    }

    /** Подготовленная к вставке схематика: палитра, блоки с учётом замен, ядра и сундуков. */
    private static final class Plan {
        String[] palette;
        int[] blocks;
        boolean[] skip;
        int coreIndex = -1;
        int offX;
        int offY;
        int offZ;
    }

    private static final BlockFace[] CHEST_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST};

    private static boolean airAt(int[] work, List<Material> mats, List<Boolean> skip, int i) {
        int pi = work[i];
        return !skip.get(pi) && mats.get(pi).isAir();
    }

    /** Опора: твёрдый блок или область, которая останется как есть (маска). */
    private static boolean supportAt(int[] work, List<Material> mats, List<Boolean> skip, int i) {
        int pi = work[i];
        return skip.get(pi) || mats.get(pi).isSolid();
    }

    private static int paletteIndex(List<String> pal, List<Material> mats, List<Boolean> skip,
                                    String str, Material m) {
        int idx = pal.indexOf(str);
        if (idx >= 0) {
            return idx;
        }
        pal.add(str);
        mats.add(m);
        skip.add(false);
        return pal.size() - 1;
    }

    private Plan buildPlan(LevelConfig lvl, Schem s) {
        Plan p = new Plan();
        if (lvl.pasteOrigin.equals("center")) {
            p.offX = -(s.width / 2);
            p.offZ = -(s.length / 2);
            p.offY = -lvl.groundLayer;
        } else {
            p.offX = s.offX;
            p.offY = s.offY;
            p.offZ = s.offZ;
        }
        List<String> pal = new ArrayList<>();
        List<Material> mats = new ArrayList<>();
        List<Boolean> skip = new ArrayList<>();
        for (String str : s.palette) {
            String cur = str == null ? "minecraft:air" : str;
            Material m = blockData(cur).getMaterial();
            Material rep = lvl.replaceBlocks.get(m);
            if (rep != null) {
                cur = Bukkit.createBlockData(rep).getAsString();
                m = rep;
            }
            pal.add(cur);
            mats.add(m);
            skip.add(lvl.skipBlocks.contains(m));
        }
        int[] work = s.blocks;
        if (lvl.coreCenter || lvl.acEnabled) {
            work = s.blocks.clone();
        }
        if (lvl.coreCenter) {
            int idx = findCore(lvl, s, work, mats, skip);
            if (idx >= 0) {
                work[idx] = paletteIndex(pal, mats, skip,
                        Bukkit.createBlockData(lvl.guardBlock).getAsString(), lvl.guardBlock);
                p.coreIndex = idx;
            } else {
                plugin.getLogger().warning("Уровень " + lvl.id + ": не нашёл место для ядра в центре схематики "
                        + lvl.schematic + " (нужен пол и 2 блока воздуха над ним). Проверьте paste.* и core.*");
            }
        }
        if (p.coreIndex >= 0 && !lvl.shieldLayers.isEmpty()) {
            applyShield(lvl, s, work, pal, mats, skip, p.coreIndex);
        }
        if (lvl.acEnabled) {
            addChests(lvl, s, work, pal, mats, skip, p.coreIndex);
        }
        p.palette = pal.toArray(new String[0]);
        p.blocks = work;
        p.skip = new boolean[skip.size()];
        for (int i = 0; i < p.skip.length; i++) {
            p.skip[i] = skip.get(i);
        }
        return p;
    }

    /** Ищет в центре схематики самую нижнюю клетку: воздух, над ней воздух, под ней пол. */
    private int findCore(LevelConfig lvl, Schem s, int[] work, List<Material> mats, List<Boolean> skip) {
        int w = s.width;
        int h = s.height;
        int l = s.length;
        int step = w * l;
        int cx = w / 2;
        int cz = l / 2;
        for (int r = 0; r <= lvl.coreRadius; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    if (x < 0 || z < 0 || x >= w || z >= l) {
                        continue;
                    }
                    if (lvl.coreY >= 0) {
                        if (lvl.coreY < h) {
                            return x + z * w + lvl.coreY * step;
                        }
                        continue;
                    }
                    for (int y = 1; y < h - 1; y++) {
                        int i = x + z * w + y * step;
                        if (airAt(work, mats, skip, i) && airAt(work, mats, skip, i + step)
                                && supportAt(work, mats, skip, i - step)) {
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Строит вокруг ядра оболочку из слоёв: слой N — это все клетки на расстоянии N от ядра (куб 3×3×3, 5×5×5 и т. д.).
     * Материал каждой клетки выбирается случайно по весам слоя.
     */
    private void applyShield(LevelConfig lvl, Schem s, int[] work, List<String> pal, List<Material> mats,
                             List<Boolean> skip, int coreIdx) {
        int w = s.width;
        int h = s.height;
        int l = s.length;
        int step = w * l;
        int cx = coreIdx % w;
        int cz = (coreIdx / w) % l;
        int cy = coreIdx / step;
        for (int layer = 1; layer <= lvl.shieldLayers.size(); layer++) {
            Map<Material, Integer> weights = lvl.shieldLayers.get(layer - 1);
            int total = 0;
            for (int v : weights.values()) {
                total += Math.max(0, v);
            }
            if (total <= 0) {
                continue;
            }
            for (int dy = -layer; dy <= layer; dy++) {
                for (int dz = -layer; dz <= layer; dz++) {
                    for (int dx = -layer; dx <= layer; dx++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != layer) {
                            continue;
                        }
                        int x = cx + dx;
                        int y = cy + dy;
                        int z = cz + dz;
                        if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= l) {
                            continue;
                        }
                        Material pick = null;
                        int r = rnd.nextInt(total);
                        for (Map.Entry<Material, Integer> e : weights.entrySet()) {
                            r -= Math.max(0, e.getValue());
                            if (r < 0) {
                                pick = e.getKey();
                                break;
                            }
                        }
                        if (pick == null) {
                            continue;
                        }
                        work[x + z * w + y * step] = paletteIndex(pal, mats, skip,
                                Bukkit.createBlockData(pick).getAsString(), pick);
                    }
                }
            }
        }
    }

    /** Добавляет сундуки (до нужного числа) вдоль стен на полу, повёрнутые лицом в комнату. */
    private void addChests(LevelConfig lvl, Schem s, int[] work, List<String> pal, List<Material> mats,
                           List<Boolean> skip, int coreIdx) {
        int w = s.width;
        int h = s.height;
        int l = s.length;
        int step = w * l;
        int existing = 0;
        for (int pi : work) {
            if (!skip.get(pi) && lvl.chestBlocks.contains(mats.get(pi))) {
                existing++;
            }
        }
        int target = lvl.acMin + rnd.nextInt(Math.max(0, lvl.acMax - lvl.acMin) + 1);
        int need = target - existing;
        if (need <= 0) {
            return;
        }
        int cx = -1;
        int cz = -1;
        if (coreIdx >= 0) {
            cx = coreIdx % w;
            cz = (coreIdx / w) % l;
        }
        List<int[]> cand = new ArrayList<>();
        for (int y = Math.max(1, lvl.acMinY); y < h - 1; y++) {
            if (lvl.acMaxY >= 0 && y > lvl.acMaxY) {
                break;
            }
            for (int z = 1; z < l - 1; z++) {
                for (int x = 1; x < w - 1; x++) {
                    int i = x + z * w + y * step;
                    if (!airAt(work, mats, skip, i) || !airAt(work, mats, skip, i + step)
                            || !supportAt(work, mats, skip, i - step)) {
                        continue;
                    }
                    // стена рядом: ищем соседа-опору; сундук смотрит в противоположную сторону
                    int face = -1;
                    if (supportAt(work, mats, skip, i - w) && !airAt(work, mats, skip, i - w)) {
                        face = 1;          // стена на севере, сундук лицом на юг
                    } else if (supportAt(work, mats, skip, i + w) && !airAt(work, mats, skip, i + w)) {
                        face = 0;
                    } else if (supportAt(work, mats, skip, i - 1) && !airAt(work, mats, skip, i - 1)) {
                        face = 3;
                    } else if (supportAt(work, mats, skip, i + 1) && !airAt(work, mats, skip, i + 1)) {
                        face = 2;
                    }
                    if (face < 0) {
                        continue;
                    }
                    if (cx >= 0) {
                        int dx = x - cx;
                        int dz = z - cz;
                        if (dx * dx + dz * dz < lvl.acMinDist * lvl.acMinDist) {
                            continue;
                        }
                    }
                    cand.add(new int[]{i, face});
                }
            }
        }
        Collections.shuffle(cand, rnd);
        List<int[]> chosen = new ArrayList<>();
        for (int[] c : cand) {
            if (chosen.size() >= need) {
                break;
            }
            boolean ok = true;
            int x = c[0] % w;
            int z = (c[0] / w) % l;
            int y = c[0] / step;
            for (int[] o : chosen) {
                int dx = x - o[0] % w;
                int dz = z - (o[0] / w) % l;
                int dy = y - o[0] / step;
                if (dx * dx + dy * dy + dz * dz < lvl.acSpacing * lvl.acSpacing) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                chosen.add(c);
            }
        }
        for (int[] c : chosen) {
            BlockData bd = lvl.acBlock.createBlockData();
            BlockFace f = CHEST_FACES[c[1]];
            if (bd instanceof Directional dir && dir.getFaces().contains(f)) {
                dir.setFacing(f);
            }
            work[c[0]] = paletteIndex(pal, mats, skip, bd.getAsString(), lvl.acBlock);
        }
    }

    /** Вставляет данж в указанную точку (точка соответствует месту, где стоял автор при //copy). */
    public void paste(LevelConfig lvl, World w, int ox, int oy, int oz, Consumer<Dungeon> cb) {
        Schem s = loadSchem(lvl.schematic);
        if (s == null) {
            cb.accept(null);
            return;
        }
        Plan plan = buildPlan(lvl, s);
        int pMinX = ox + plan.offX;
        int pMinY = oy + plan.offY;
        int pMinZ = oz + plan.offZ;
        int cx = pMinX + s.width / 2;
        int cz = pMinZ + s.length / 2;
        int r = lvl.rgRadius;

        Dungeon d = new Dungeon();
        d.id = newId(lvl.id);
        d.level = lvl.id;
        d.world = w.getName();
        d.originX = ox;
        d.originY = oy;
        d.originZ = oz;
        d.minX = Math.min(pMinX, cx - r);
        d.maxX = Math.max(pMinX + s.width - 1, cx + r);
        d.minZ = Math.min(pMinZ, cz - r);
        d.maxZ = Math.max(pMinZ + s.length - 1, cz + r);
        d.minY = w.getMinHeight();
        d.maxY = w.getMaxHeight() - 1;
        d.guardX = Integer.MIN_VALUE;
        d.createdAt = System.currentTimeMillis();
        d.state = Dungeon.State.PASTING;
        dungeons.put(d.id, d);
        save();

        loadChunks(w, d.minX, d.minZ, d.maxX, d.maxZ, () -> {
            File snap = new File(plugin.getDataFolder(), "snapshots/" + d.id + ".snap");
            try {
                writeSnapshot(snap, w, s, pMinX, pMinY, pMinZ);
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось сохранить снимок территории: " + e.getMessage());
                dungeons.remove(d.id);
                save();
                cb.accept(null);
                return;
            }
            boolean pasteAir = lvl.raw.getBoolean("paste.air", true);
            BlockData[] cache = new BlockData[plan.palette.length];
            int[] best = {Integer.MAX_VALUE};
            int total = plan.blocks.length;
            runBatches(total, i -> {
                int x = i % s.width;
                int z = (i / s.width) % s.length;
                int y = i / (s.width * s.length);
                int bx = pMinX + x;
                int by = pMinY + y;
                int bz = pMinZ + z;
                if (by < w.getMinHeight() || by >= w.getMaxHeight()) {
                    return;
                }
                int pi = plan.blocks[i];
                if (pi < 0 || pi >= plan.palette.length || plan.skip[pi]) {
                    return;
                }
                if (cache[pi] == null) {
                    cache[pi] = blockData(plan.palette[pi]);
                }
                BlockData bd = cache[pi];
                Material m = bd.getMaterial();
                if (m.isAir() && !pasteAir) {
                    return;
                }
                w.getBlockAt(bx, by, bz).setBlockData(bd, false);
                if (lvl.chestBlocks.contains(m)) {
                    d.chests.add(new Dungeon.ChestInfo(bx, by, bz));
                } else if (i == plan.coreIndex) {
                    d.guardX = bx;
                    d.guardY = by;
                    d.guardZ = bz;
                } else if (plan.coreIndex < 0 && m == lvl.guardBlock) {
                    int dx = bx - cx;
                    int dz = bz - cz;
                    int dist = dx * dx + dz * dz;
                    if (dist < best[0]) {
                        best[0] = dist;
                        d.guardX = bx;
                        d.guardY = by;
                        d.guardZ = bz;
                    }
                }
            }, () -> finishPaste(d, lvl, w, cx, cz, cb));
        });
    }

    private void writeSnapshot(File f, World w, Schem s, int minX, int minY, int minZ) throws IOException {
        Map<String, Integer> pal = new LinkedHashMap<>();
        int[] idx = new int[s.width * s.height * s.length];
        int i = 0;
        for (int y = 0; y < s.height; y++) {
            for (int z = 0; z < s.length; z++) {
                for (int x = 0; x < s.width; x++) {
                    int by = minY + y;
                    String str = (by < w.getMinHeight() || by >= w.getMaxHeight())
                            ? "minecraft:air"
                            : w.getBlockAt(minX + x, by, minZ + z).getBlockData().getAsString();
                    Integer id = pal.get(str);
                    if (id == null) {
                        id = pal.size();
                        pal.put(str, id);
                    }
                    idx[i++] = id;
                }
            }
        }
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
            out.writeInt(s.width);
            out.writeInt(s.height);
            out.writeInt(s.length);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            out.writeInt(pal.size());
            for (String k : pal.keySet()) {
                out.writeUTF(k);
            }
            for (int v : idx) {
                out.writeInt(v);
            }
        }
    }

    private void finishPaste(Dungeon d, LevelConfig lvl, World w, int cx, int cz, Consumer<Dungeon> cb) {
        if (d.guardX == Integer.MIN_VALUE) {
            d.guardX = cx;
            d.guardY = d.originY;
            d.guardZ = cz;
            w.getBlockAt(cx, d.originY, cz).setType(lvl.guardBlock, false);
            plugin.getLogger().warning("В схематике " + lvl.schematic + " нет блока " + lvl.guardBlock
                    + ", блок привата поставлен в центр.");
        }
        int locked = 0;
        for (Dungeon.ChestInfo c : d.chests) {
            if (rnd.nextDouble() * 100 < lvl.lockedPercent) {
                c.locked = true;
                locked++;
            } else if (rnd.nextDouble() * 100 < lvl.mimicChance) {
                c.mimic = true;
            }
        }
        if (lvl.lockedPercent > 0 && locked == 0 && !d.chests.isEmpty()) {
            Dungeon.ChestInfo c = d.chests.get(rnd.nextInt(d.chests.size()));
            c.locked = true;
            c.mimic = false;
            locked = 1;
        }
        d.keysToDrop = locked;
        List<Block> filled = new ArrayList<>();
        for (Dungeon.ChestInfo c : d.chests) {
            if (!c.mimic) {
                Block b = w.getBlockAt(c.x, c.y, c.z);
                fillChest(b, lvl);
                filled.add(b);
            }
        }
        if (!filled.isEmpty()) {
            for (LevelConfig.DropSpec g : lvl.guaranteed) {
                if (rnd.nextDouble() * 100 < g.chance) {
                    putInChest(filled.get(rnd.nextInt(filled.size())), rollDrop(g));
                }
            }
        }
        plugin.regions().sync(d, lvl, false);
        d.state = Dungeon.State.ACTIVE;
        save();
        plugin.getLogger().info("Появился данж " + d.id + " (уровень " + d.level + ") в "
                + d.world + " " + d.guardX + " " + d.guardY + " " + d.guardZ);
        fire("spawn", d, null, guardLocation(d));
        cb.accept(d);
    }

    private ItemStack rollDrop(LevelConfig.DropSpec g) {
        int amount = g.min + rnd.nextInt(g.max - g.min + 1);
        return new ItemStack(g.material, Math.max(1, Math.min(amount, g.material.getMaxStackSize())));
    }

    private Inventory inventoryOf(Block b) {
        BlockState st = b.getState();
        if (st instanceof Chest ch) {
            return ch.getBlockInventory();
        }
        if (st instanceof Container ct) {
            return ct.getInventory();
        }
        return null;
    }

    private void putInChest(Block b, ItemStack it) {
        Inventory inv = inventoryOf(b);
        if (inv == null) {
            return;
        }
        int slot = inv.firstEmpty();
        if (slot >= 0) {
            inv.setItem(slot, it);
        }
    }

    private void fillChest(Block b, LevelConfig lvl) {
        Inventory inv = inventoryOf(b);
        if (inv == null) {
            return;
        }
        inv.clear();
        int[] range = lvl.lootByType.get(b.getType());
        int min = range != null ? range[0] : lvl.lootMin;
        int max = range != null ? range[1] : lvl.lootMax;
        int size = inv.getSize();
        int count = Math.min(size, min + rnd.nextInt(Math.max(0, max - min) + 1));
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        if (lvl.lootRandom) {
            Collections.shuffle(slots, rnd);
        }
        for (int k = 0; k < count; k++) {
            ItemStack it = plugin.loot().roll(lvl.id);
            if (it == null) {
                break;
            }
            inv.setItem(slots.get(k), it);
        }
    }

    // ------------------------------------------------------------------ открытие

    public void open(Dungeon d, Player by) {
        if (d.state != Dungeon.State.ACTIVE) {
            return;
        }
        LevelConfig lvl = levels.get(d.level);
        World w = Bukkit.getWorld(d.world);
        if (lvl == null || w == null) {
            return;
        }
        w.getBlockAt(d.guardX, d.guardY, d.guardZ).setType(Material.AIR, false);
        d.state = lvl.openDelay > 0 ? Dungeon.State.COUNTDOWN : Dungeon.State.OPEN;
        d.openedAt = System.currentTimeMillis();
        d.openAt = d.openedAt + lvl.openDelay * 1000L;
        if (lvl.wgMode) {
            if (lvl.wgRemoveOnRaid) {
                plugin.regions().delete(d);     // рейд состоялся: приват исчезает
            } else {
                plugin.regions().sync(d, lvl, true);
            }
        }
        Location gl = guardLocation(d);
        if (by != null) {
            addRaid(by);
        }
        if (lvl.alarm) {
            String who = by == null ? plugin.msg().raw("unknown-player") : by.getName();
            Bukkit.broadcast(plugin.msg().get(lvl.alarmMessage, "level", lvl.name, "player", who,
                    "x", d.guardX, "y", d.guardY, "z", d.guardZ, "world", d.world));
            plugin.events().sound(lvl.alarmSound, "all", w, gl, by);
        } else if (by != null) {
            plugin.msg().send(by, "opened", "level", lvl.name);
        }
        if (lvl.openDelay > 0 && by != null) {
            plugin.msg().send(by, "countdown-started", "seconds", lvl.openDelay);
        }
        fire("open", d, by, gl);
        spawnGuardians(d, lvl, w);
        save();
    }

    private void finishCountdown(Dungeon d, LevelConfig lvl, World w) {
        d.state = Dungeon.State.OPEN;
        BossBar b = countdownBars.remove(d.id);
        if (b != null) {
            b.removeAll();
        }
        for (Player p : w.getPlayers()) {
            if (d.contains(p.getLocation())) {
                p.sendMessage(plugin.msg().get("countdown-done", "level", lvl.name));
            }
        }
        fire("countdown-done", d, null, guardLocation(d));
        save();
    }

    private void updateCountdown(Dungeon d, LevelConfig lvl, World w, long leftMs) {
        long sec = Math.max(0, leftMs / 1000);
        double total = Math.max(1, lvl.openDelay * 1000.0);
        String title = Msg.legacy(plugin.msg().raw("countdown-bar", "time", fmt(sec)));
        BossBar bar = countdownBars.computeIfAbsent(d.id,
                k -> Bukkit.createBossBar(title, lvl.countdownColor, lvl.countdownStyle));
        bar.setTitle(title);
        bar.setProgress(Math.max(0, Math.min(1, leftMs / total)));
        bar.removeAll();
        Location gl = guardLocation(d);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(gl) <= lvl.barRadius * lvl.barRadius) {
                bar.addPlayer(p);
            }
        }
        if (lvl.beam) {
            int top = Math.min(w.getMaxHeight(), d.guardY + lvl.beamHeight);
            for (int y = d.guardY; y < top; y += lvl.beamStep) {
                w.spawnParticle(Particle.END_ROD, d.guardX + 0.5, y, d.guardZ + 0.5, 1, 0, 0, 0, 0.0, null, true);
            }
        }
    }

    private static String fmt(long sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    // ------------------------------------------------------------------ стражи, мимики, босс

    private void spawnGuardians(Dungeon d, LevelConfig lvl, World w) {
        int holders = 0;
        for (LevelConfig.MobSpec s : lvl.guardians) {
            if (s.keyHolder) {
                holders += s.amount;
            }
        }
        boolean fallbackHolder = d.keysToDrop > 0 && holders == 0;
        if (fallbackHolder) {
            holders = 1;
        }
        int perHolder = holders == 0 ? 0 : (int) Math.ceil(d.keysToDrop / (double) holders);
        boolean fallbackUsed = false;

        Map<Integer, List<MobJob>> byWave = new TreeMap<>();
        for (LevelConfig.MobSpec s : lvl.guardians) {
            for (int i = 0; i < s.amount; i++) {
                boolean holder = s.keyHolder || (fallbackHolder && !fallbackUsed);
                if (fallbackHolder && !fallbackUsed) {
                    fallbackUsed = true;
                }
                byWave.computeIfAbsent(s.wave, k -> new ArrayList<>()).add(new MobJob(s, holder ? perHolder : 0));
            }
        }
        for (Map.Entry<Integer, List<MobJob>> e : byWave.entrySet()) {
            long delay = (long) (e.getKey() - 1) * lvl.waveDelay * 20L;
            List<MobJob> jobs = e.getValue();
            if (delay <= 0) {
                spawnJobs(d, lvl, w, jobs);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (dungeons.containsKey(d.id) && d.state != Dungeon.State.PASTING
                            && d.state != Dungeon.State.LOOTED) {
                        spawnJobs(d, lvl, w, jobs);
                    }
                }, delay);
            }
        }
        if (lvl.boss != null) {
            int r = Math.max(lvl.boss.radius > 0 ? lvl.boss.radius : 2, lvl.shieldLayers.size() + 2);
            LivingEntity boss = spawnMob(d, w, mobLocation(d, w, r, lvl.shieldLayers.size()), lvl.boss,
                    lvl.boss.keyHolder ? Math.max(1, d.keysToDrop) : 0, true);
            if (boss != null) {
                d.boss = boss.getUniqueId();
            }
        }
    }

    private void spawnJobs(Dungeon d, LevelConfig lvl, World w, List<MobJob> jobs) {
        for (MobJob j : jobs) {
            int r = j.spec().radius > 0 ? j.spec().radius : lvl.guardianRadius;
            spawnMob(d, w, mobLocation(d, w, r, lvl.shieldLayers.size()), j.spec(), j.keys(), false);
        }
    }

    private Location mobLocation(Dungeon d, World w, int radius, int exclude) {
        for (int t = 0; t < 20; t++) {
            int x = d.guardX + rnd.nextInt(radius * 2 + 1) - radius;
            int z = d.guardZ + rnd.nextInt(radius * 2 + 1) - radius;
            for (int dy = -1; dy <= 4; dy++) {
                int y = d.guardY + dy;
                int cheb = Math.max(Math.max(Math.abs(x - d.guardX), Math.abs(y - d.guardY)), Math.abs(z - d.guardZ));
                if (cheb <= exclude) {
                    continue;
                }
                Block feet = w.getBlockAt(x, y, z);
                if (feet.isPassable() && !feet.isLiquid()
                        && w.getBlockAt(x, y + 1, z).isPassable()
                        && !w.getBlockAt(x, y - 1, z).isPassable()) {
                    return new Location(w, x + 0.5, y, z + 0.5);
                }
            }
        }
        return new Location(w, d.guardX + 0.5, d.guardY + 1 + exclude, d.guardZ + 0.5);
    }

    private LivingEntity spawnMob(Dungeon d, World w, Location loc, LevelConfig.MobSpec spec,
                                  int keys, boolean boss) {
        Entity e;
        try {
            e = w.spawnEntity(loc, spec.type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        if (!spec.name.isEmpty()) {
            le.customName(Msg.color(spec.name));
            le.setCustomNameVisible(true);
        }
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && spec.health > 0) {
            hp.setBaseValue(spec.health);
            le.setHealth(Math.min(spec.health, hp.getValue()));
        }
        if (spec.attackDamage >= 0) {
            AttributeInstance ad = le.getAttribute(Attribute.ATTACK_DAMAGE);
            if (ad != null) {
                ad.setBaseValue(spec.attackDamage);
            }
        }
        if (spec.speed > 0) {
            AttributeInstance sp = le.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) {
                sp.setBaseValue(sp.getBaseValue() * spec.speed);
            }
        }
        for (PotionEffect pe : spec.effects) {
            le.addPotionEffect(pe);
        }
        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            if (spec.helmet != null) {
                eq.setHelmet(new ItemStack(spec.helmet));
                eq.setHelmetDropChance(0f);
            }
            if (spec.chest != null) {
                eq.setChestplate(new ItemStack(spec.chest));
                eq.setChestplateDropChance(0f);
            }
            if (spec.legs != null) {
                eq.setLeggings(new ItemStack(spec.legs));
                eq.setLeggingsDropChance(0f);
            }
            if (spec.boots != null) {
                eq.setBoots(new ItemStack(spec.boots));
                eq.setBootsDropChance(0f);
            }
            if (spec.hand != null) {
                eq.setItemInMainHand(new ItemStack(spec.hand));
                eq.setItemInMainHandDropChance(0f);
            }
        }
        le.setRemoveWhenFarAway(false);
        le.setPersistent(true);
        le.setGlowing(spec.glowing);
        var pdc = le.getPersistentDataContainer();
        pdc.set(plugin.items().dungeon, PersistentDataType.STRING, d.id);
        if (keys > 0) {
            pdc.set(plugin.items().keyholder, PersistentDataType.INTEGER, keys);
        }
        if (boss) {
            pdc.set(plugin.items().boss, PersistentDataType.INTEGER, Math.max(0, spec.bonusRolls));
        }
        if (!spec.drops.isEmpty()) {
            pdc.set(plugin.items().drops, PersistentDataType.STRING, LevelConfig.dropsToString(spec.drops));
        }
        if (spec.exp >= 0) {
            pdc.set(plugin.items().exp, PersistentDataType.INTEGER, spec.exp);
        }
        if (spec.clearDrops) {
            pdc.set(plugin.items().clearDrops, PersistentDataType.BYTE, (byte) 1);
        }
        return le;
    }

    /** Сундук-мимик: блок исчезает, появляется моб. */
    public void spawnMimic(Dungeon d, Block chest, Player victim) {
        LevelConfig lvl = levels.get(d.level);
        if (lvl == null) {
            return;
        }
        World w = chest.getWorld();
        Location loc = chest.getLocation().add(0.5, 0, 0.5);
        chest.setType(Material.AIR, false);
        LevelConfig.MobSpec spec = new LevelConfig.MobSpec();
        spec.type = lvl.mimicType;
        spec.name = lvl.mimicName;
        spec.health = lvl.mimicHealth;
        LivingEntity le = spawnMob(d, w, loc, spec, 0, false);
        if (le != null) {
            le.getPersistentDataContainer().set(plugin.items().mimic, PersistentDataType.INTEGER, lvl.mimicRolls);
        }
        plugin.msg().send(victim, "mimic");
        fire("mimic", d, victim, loc);
    }

    public void markOpened(Dungeon d, Dungeon.ChestInfo info, Player by) {
        info.opened = true;
        boolean all = true;
        for (Dungeon.ChestInfo c : d.chests) {
            if (!c.opened) {
                all = false;
                break;
            }
        }
        if (all && d.state == Dungeon.State.OPEN) {
            d.state = Dungeon.State.LOOTED;
            d.lootedAt = System.currentTimeMillis();
            fire("loot-complete", d, by, guardLocation(d));
        }
        save();
    }

    public void trap(Dungeon d, Player p) {
        LevelConfig lvl = levels.get(d.level);
        if (lvl == null || d.state == Dungeon.State.LOOTED || d.state == Dungeon.State.PASTING) {
            return;
        }
        if (lvl.trapDamage <= 0 && lvl.trapArrows <= 0 && lvl.trapEffects.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastTrap.get(p.getUniqueId());
        if (last != null && now - last < lvl.trapCooldownMs) {
            return;
        }
        lastTrap.put(p.getUniqueId(), now);
        if (rnd.nextDouble() * 100 >= lvl.trapChance) {
            return;
        }
        if (lvl.trapDamage > 0) {
            p.damage(lvl.trapDamage);
        }
        for (PotionEffect pe : lvl.trapEffects) {
            p.addPotionEffect(pe);
        }
        for (int i = 0; i < lvl.trapArrows; i++) {
            Location l = p.getLocation().add(rnd.nextDouble() * 4 - 2, 5, rnd.nextDouble() * 4 - 2);
            Arrow a = p.getWorld().spawnArrow(l, new Vector(0, -1, 0), 1.5f, 8f);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        if (lvl.trapMessage) {
            p.sendMessage(plugin.msg().get("trap"));
        }
        fire("trap", d, p, p.getLocation());
    }

    // ------------------------------------------------------------------ удаление и откат

    public void remove(Dungeon d, boolean rollback, Runnable done) {
        World w = Bukkit.getWorld(d.world);
        File snap = new File(plugin.getDataFolder(), "snapshots/" + d.id + ".snap");
        BossBar cb = countdownBars.remove(d.id);
        if (cb != null) {
            cb.removeAll();
        }
        BossBar bb = bossBars.remove(d.id);
        if (bb != null) {
            bb.removeAll();
        }
        d.state = Dungeon.State.PASTING;
        if (w == null || !rollback || !snap.exists()) {
            plugin.regions().delete(d);
            dungeons.remove(d.id);
            snap.delete();
            save();
            if (done != null) {
                done.run();
            }
            return;
        }
        loadChunks(w, d.minX, d.minZ, d.maxX, d.maxZ, () -> {
            clearEntities(d, w);
            int width;
            int length;
            int minX;
            int minY;
            int minZ;
            String[] pal;
            int[] idx;
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(snap)))) {
                width = in.readInt();
                int height = in.readInt();
                length = in.readInt();
                minX = in.readInt();
                minY = in.readInt();
                minZ = in.readInt();
                pal = new String[in.readInt()];
                for (int i = 0; i < pal.length; i++) {
                    pal[i] = in.readUTF();
                }
                idx = new int[width * height * length];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = in.readInt();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось прочитать снимок " + d.id + ": " + e.getMessage());
                plugin.regions().delete(d);
                dungeons.remove(d.id);
                save();
                if (done != null) {
                    done.run();
                }
                return;
            }
            final int fw = width;
            final int fl = length;
            final int fx = minX;
            final int fy = minY;
            final int fz = minZ;
            runBatches(idx.length, i -> {
                int x = i % fw;
                int z = (i / fw) % fl;
                int y = i / (fw * fl);
                int by = fy + y;
                if (by < w.getMinHeight() || by >= w.getMaxHeight()) {
                    return;
                }
                w.getBlockAt(fx + x, by, fz + z).setBlockData(blockData(pal[idx[i]]), false);
            }, () -> {
                clearEntities(d, w);
                plugin.regions().delete(d);
                dungeons.remove(d.id);
                snap.delete();
                save();
                if (done != null) {
                    done.run();
                }
            });
        });
    }

    private void clearEntities(Dungeon d, World w) {
        for (int cx = d.minX >> 4; cx <= d.maxX >> 4; cx++) {
            for (int cz = d.minZ >> 4; cz <= d.maxZ >> 4; cz++) {
                for (Entity e : w.getChunkAt(cx, cz).getEntities()) {
                    String id = e.getPersistentDataContainer().get(plugin.items().dungeon, PersistentDataType.STRING);
                    if (d.id.equals(id)) {
                        e.remove();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ тики, расписание, очистка

    private void tick() {
        nightCheck();
        intervalCheck();
        long now = System.currentTimeMillis();
        for (Dungeon d : new ArrayList<>(dungeons.values())) {
            LevelConfig lvl = levels.get(d.level);
            World w = Bukkit.getWorld(d.world);
            if (lvl == null || w == null || d.state == Dungeon.State.PASTING) {
                continue;
            }
            cleanupTick(d, lvl, now);
            if (d.state == Dungeon.State.PASTING) {
                continue;
            }
            if (d.state == Dungeon.State.COUNTDOWN) {
                long left = d.openAt - now;
                if (left <= 0) {
                    finishCountdown(d, lvl, w);
                } else {
                    updateCountdown(d, lvl, w, left);
                }
            }
            if (!lvl.aura.isEmpty() && lvl.auraPhases.contains(d.state)) {
                for (Player p : w.getPlayers()) {
                    if (d.contains(p.getLocation())) {
                        for (PotionEffect pe : lvl.aura) {
                            p.addPotionEffect(pe);
                        }
                    }
                }
            }
            if (d.boss != null && lvl.boss != null) {
                updateBossBar(d, lvl, w);
            }
        }
        regionMessages();
    }

    private void cleanupTick(Dungeon d, LevelConfig lvl, long now) {
        YamlConfiguration m = lvl.raw;
        switch (d.state) {
            case LOOTED -> {
                String mode = m.getString("cleanup.looted", "night").toLowerCase();
                long delay = m.getLong("cleanup.looted-delay-seconds", 600) * 1000L;
                if (mode.equals("delay") && d.lootedAt > 0 && now - d.lootedAt >= delay) {
                    remove(d, true, null);
                }
            }
            case ACTIVE -> {
                long life = m.getLong("cleanup.unopened-lifetime-minutes", 0) * 60000L;
                if (life > 0 && d.createdAt > 0 && now - d.createdAt >= life) {
                    remove(d, true, null);
                }
            }
            case OPEN, COUNTDOWN -> {
                long life = m.getLong("cleanup.opened-lifetime-minutes", 0) * 60000L;
                if (life > 0 && d.openedAt > 0 && now - d.openedAt >= life) {
                    remove(d, true, null);
                }
            }
            default -> { }
        }
    }

    private void updateBossBar(Dungeon d, LevelConfig lvl, World w) {
        Entity e = Bukkit.getEntity(d.boss);
        BossBar bar = bossBars.get(d.id);
        if (!(e instanceof LivingEntity le) || le.isDead()) {
            if (bar != null) {
                bar.removeAll();
            }
            return;
        }
        String title = Msg.legacy(lvl.boss.name.isEmpty() ? "&bБосс" : lvl.boss.name);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, lvl.bossColor, lvl.bossStyle);
            bossBars.put(d.id, bar);
        }
        AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
        double max = hp == null ? 20 : hp.getValue();
        bar.setProgress(Math.max(0, Math.min(1, le.getHealth() / max)));
        bar.removeAll();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(le.getLocation()) <= lvl.barRadius * lvl.barRadius) {
                bar.addPlayer(p);
            }
        }
    }

    public void bossDied(Dungeon d) {
        d.boss = null;
        BossBar bar = bossBars.remove(d.id);
        if (bar != null) {
            bar.removeAll();
        }
        save();
    }

    private void regionMessages() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Dungeon cur = at(p.getLocation());
            String curId = cur == null || cur.state == Dungeon.State.PASTING ? null : cur.id;
            String prev = playerRegion.get(p.getUniqueId());
            if ((curId == null && prev == null) || (curId != null && curId.equals(prev))) {
                continue;
            }
            if (prev != null) {
                Dungeon pd = dungeons.get(prev);
                showRegion(p, pd == null ? null : levels.get(pd.level), "leave");
            }
            if (curId != null) {
                LevelConfig lvl = levels.get(cur.level);
                showRegion(p, lvl, cur.isOpenPhase() ? "enter-open" : "enter");
                playerRegion.put(p.getUniqueId(), curId);
            } else {
                playerRegion.remove(p.getUniqueId());
            }
        }
    }

    private void showRegion(Player p, LevelConfig lvl, String key) {
        String mode = lvl == null ? "actionbar" : lvl.enterDisplay;
        Component c = plugin.msg().plain(key, "level", lvl == null ? "" : lvl.name);
        switch (mode) {
            case "none" -> { }
            case "chat" -> p.sendMessage(c);
            case "title" -> p.showTitle(Title.title(Component.empty(), c));
            default -> p.sendActionBar(c);
        }
    }

    /** Миры, где работает автоматический спавн: глобальный список и миры из уровней. */
    public Set<String> autoWorlds() {
        Set<String> out = new LinkedHashSet<>(plugin.getConfig().getStringList("worlds"));
        for (LevelConfig l : levels.values()) {
            out.addAll(l.worlds);
        }
        return out;
    }

    private void nightCheck() {
        long nightStart = plugin.getConfig().getLong("night-start-tick", 13000);
        for (String name : autoWorlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) {
                continue;
            }
            long time = w.getTime();
            if (time < nightStart || time > 23000) {
                continue;
            }
            long day = w.getFullTime() / 24000L;
            long last = data.getLong("last-night." + name, -1);
            if (last == day) {
                continue;
            }
            data.set("last-night." + name, day);
            save();
            nightCycle(w, day);
        }
    }

    private void nightCycle(World w, long day) {
        for (Dungeon d : new ArrayList<>(dungeons.values())) {
            if (d.world.equals(w.getName()) && d.state == Dungeon.State.LOOTED) {
                LevelConfig l = levels.get(d.level);
                String mode = l == null ? "night" : l.raw.getString("cleanup.looted", "night");
                if (mode.equalsIgnoreCase("night")) {
                    remove(d, true, null);
                }
            }
        }
        var c = plugin.getConfig();
        String mode = c.getString("schedule.mode", "night").toLowerCase();
        if (!mode.equals("night") && !mode.equals("both")) {
            return;
        }
        int every = Math.max(1, c.getInt("schedule.nights-interval", 1));
        if (day % every != 0) {
            return;
        }
        spawnBatch(w, c.getInt("restart-danges", 7));
    }

    private void intervalCheck() {
        var c = plugin.getConfig();
        String mode = c.getString("schedule.mode", "night").toLowerCase();
        if (!mode.equals("interval") && !mode.equals("both")) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = data.getLong("last-interval", 0);
        if (last == 0) {
            data.set("last-interval", now);
            return;
        }
        long every = Math.max(1, c.getLong("schedule.interval-minutes", 60)) * 60000L;
        if (now - last < every) {
            return;
        }
        data.set("last-interval", now);
        save();
        for (String name : autoWorlds()) {
            World w = Bukkit.getWorld(name);
            if (w != null) {
                spawnBatch(w, c.getInt("schedule.interval-amount", 3));
            }
        }
    }

    /** Спавнит до amount данжей в мире с учётом max-danges и паузы между спавнами. */
    public void spawnBatch(World w, int amount) {
        var c = plugin.getConfig();
        int toSpawn = Math.min(amount, c.getInt("max-danges", 20) - countActive(w, -1));
        int attempts = c.getInt("spawn-attempts", 40);
        long stagger = Math.max(1, c.getLong("schedule.stagger-ticks", 20));
        for (int i = 0; i < toSpawn; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                LevelConfig lvl = pickLevel(w);
                if (lvl == null) {
                    plugin.getLogger().warning("Нет подходящих уровней (схематики, миры или лимиты) — спавн пропущен.");
                    return;
                }
                spawnRandom(lvl, w, attempts, d -> { });
            }, i * stagger + 1L);
        }
    }

    // ------------------------------------------------------------------ статистика и проверка

    private void addRaid(Player p) {
        String path = "stats." + p.getUniqueId();
        data.set(path + ".name", p.getName());
        data.set(path + ".raids", data.getInt(path + ".raids", 0) + 1);
        save();
    }

    /** Топ грабителей: имя -> число вскрытых данжей. */
    public List<Map.Entry<String, Integer>> top() {
        Map<String, Integer> out = new HashMap<>();
        ConfigurationSection s = data.getConfigurationSection("stats");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.put(s.getString(k + ".name", k), s.getInt(k + ".raids"));
            }
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(out.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        return list;
    }

    /** Проверка конфигурации: возвращает список замечаний (пусто — всё хорошо). */
    public List<String> validate() {
        List<String> out = new ArrayList<>();
        if (levels.isEmpty()) {
            out.add("В config.yml нет ни одного уровня (секция levels).");
        }
        for (LevelConfig l : levels.values()) {
            String p = "Уровень " + l.id + ": ";
            for (String w : l.warnings) {
                out.add(p + w);
            }
            File f = schemFile(l.schematic);
            if (!f.exists()) {
                out.add(p + "нет файла схематики " + l.schematic);
            } else {
                Schem s = loadSchem(l.schematic);
                if (s == null) {
                    out.add(p + "схематика " + l.schematic + " не читается (нужен формат Sponge .schem)");
                } else {
                    boolean guard = false;
                    boolean chest = false;
                    String gid = "minecraft:" + l.guardBlock.getKey().getKey();
                    for (String pal : s.palette) {
                        if (pal == null) {
                            continue;
                        }
                        if (pal.equals(gid) || pal.startsWith(gid + "[")) {
                            guard = true;
                        }
                        for (Material cm : l.chestBlocks) {
                            String cid = "minecraft:" + cm.getKey().getKey();
                            if (pal.equals(cid) || pal.startsWith(cid + "[")) {
                                chest = true;
                            }
                        }
                    }
                    if (!guard && !l.coreCenter) {
                        out.add(p + "в схематике нет блока привата " + l.guardBlock.name()
                                + " (включите core.mode: center, чтобы ядро ставилось в центр само)");
                    }
                    if (l.raw.getBoolean("core.shield.enabled", false) && !l.coreCenter) {
                        out.add(p + "core.shield работает только при core.mode: center");
                    }
                    if (!chest && !l.acEnabled) {
                        out.add(p + "в схематике нет контейнеров из chest-blocks (включите auto-chests)");
                    }
                    if (l.pasteOrigin.equals("schematic") && (Math.abs(s.offX) > 200 || Math.abs(s.offZ) > 200)) {
                        out.add(p + "у схематики большое смещение (" + s.offX + " " + s.offY + " " + s.offZ
                                + "): данж появится далеко от точки. Задайте paste.origin: center и paste.ground-layer");
                    }
                }
            }
            if (plugin.loot().entries(l.id).isEmpty()) {
                out.add(p + "лут пуст (loot.yml)");
            }
            if (l.guardians.isEmpty() && l.boss == null) {
                out.add(p + "нет стражей и босса (это допустимо, но данж будет без охраны)");
            }
            if (l.events == null) {
                out.add(p + "в конфиге нет секции events (события отключены)");
            }
        }
        return out;
    }
}