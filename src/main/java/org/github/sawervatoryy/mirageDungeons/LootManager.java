package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Лут по уровням (loot.yml). Предметы добавляются и командой в игре. */
public final class LootManager {

    public static final class Entry {
        public Material material;
        public ItemStack item;
        public int min = 1;
        public int max = 1;
        public double weight = 10;
    }

    private final MirageDungeons plugin;
    private final Random rnd = new Random();
    private final Map<Integer, List<Entry>> loot = new TreeMap<>();
    private File file;

    public LootManager(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), "loot.yml");
        if (!file.exists()) {
            plugin.saveResource("loot.yml", false);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        loot.clear();
        ConfigurationSection root = y.getConfigurationSection("levels");
        if (root == null) {
            return;
        }
        for (String k : root.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(k);
            } catch (NumberFormatException e) {
                continue;
            }
            List<Entry> list = new ArrayList<>();
            for (Map<?, ?> m : root.getMapList(k)) {
                Entry e = new Entry();
                Object w = m.get("weight");
                e.weight = w instanceof Number n ? n.doubleValue() : 10;
                if (m.get("item") instanceof ItemStack st) {
                    e.item = st;
                } else if (m.get("material") != null) {
                    e.material = Material.matchMaterial(String.valueOf(m.get("material")));
                    if (e.material == null) {
                        continue;
                    }
                    e.min = m.get("min") instanceof Number n ? n.intValue() : 1;
                    e.max = m.get("max") instanceof Number n ? n.intValue() : e.min;
                } else {
                    continue;
                }
                list.add(e);
            }
            loot.put(level, list);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<Integer, List<Entry>> en : loot.entrySet()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Entry e : en.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (e.item != null) {
                    m.put("item", e.item);
                } else {
                    m.put("material", e.material.name());
                    m.put("min", e.min);
                    m.put("max", e.max);
                }
                m.put("weight", e.weight);
                out.add(m);
            }
            y.set("levels." + en.getKey(), out);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить loot.yml: " + ex.getMessage());
        }
    }

    public List<Entry> entries(int level) {
        return loot.computeIfAbsent(level, k -> new ArrayList<>());
    }

    public void add(int level, ItemStack stack, double weight) {
        Entry e = new Entry();
        e.item = stack.clone();
        e.weight = weight;
        entries(level).add(e);
        save();
    }

    public boolean remove(int level, int index) {
        List<Entry> l = entries(level);
        if (index < 0 || index >= l.size()) {
            return false;
        }
        l.remove(index);
        save();
        return true;
    }

    /** Меняет количество у записи: для материала min-max, для предмета — размер стопки. */
    public boolean setAmount(int level, int index, int min, int max) {
        List<Entry> l = entries(level);
        if (index < 0 || index >= l.size()) {
            return false;
        }
        Entry e = l.get(index);
        if (e.item != null) {
            e.item.setAmount(Math.max(1, Math.min(min, e.item.getMaxStackSize())));
        } else {
            e.min = Math.max(1, min);
            e.max = Math.max(e.min, max);
        }
        save();
        return true;
    }

    public void clear(int level) {
        entries(level).clear();
        save();
    }

    /** Случайный предмет по весам или null, если лут уровня пуст. */
    public ItemStack roll(int level) {
        List<Entry> l = entries(level);
        double total = 0;
        for (Entry e : l) {
            total += Math.max(0, e.weight);
        }
        if (l.isEmpty() || total <= 0) {
            return null;
        }
        double r = rnd.nextDouble() * total;
        for (Entry e : l) {
            r -= Math.max(0, e.weight);
            if (r <= 0) {
                return build(e);
            }
        }
        return build(l.get(l.size() - 1));
    }

    private ItemStack build(Entry e) {
        if (e.item != null) {
            return e.item.clone();
        }
        int min = Math.max(1, e.min);
        int max = Math.max(min, e.max);
        int amount = min + rnd.nextInt(max - min + 1);
        return new ItemStack(e.material, Math.min(amount, e.material.getMaxStackSize()));
    }
}
