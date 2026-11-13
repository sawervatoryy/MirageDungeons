package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;

/** Компас сокровищниц, ключи и метки на сущностях. */
public final class Items {

    public final NamespacedKey compass;
    public final NamespacedKey key;
    public final NamespacedKey dungeon;
    public final NamespacedKey keyholder;
    public final NamespacedKey boss;
    public final NamespacedKey mimic;
    public final NamespacedKey cooldown;
    public final NamespacedKey drops;
    public final NamespacedKey exp;
    public final NamespacedKey clearDrops;
    private final MirageDungeons plugin;

    public Items(MirageDungeons plugin) {
        this.plugin = plugin;
        this.compass = new NamespacedKey(plugin, "compass");
        this.key = new NamespacedKey(plugin, "dungeon_key");
        this.dungeon = new NamespacedKey(plugin, "dungeon_id");
        this.keyholder = new NamespacedKey(plugin, "keyholder");
        this.boss = new NamespacedKey(plugin, "boss");
        this.mimic = new NamespacedKey(plugin, "mimic");
        this.cooldown = new NamespacedKey(plugin, "compass_until");
        this.drops = new NamespacedKey(plugin, "mob_drops");
        this.exp = new NamespacedKey(plugin, "mob_exp");
        this.clearDrops = new NamespacedKey(plugin, "mob_clear_drops");
    }

    @SuppressWarnings("deprecation")
    public ItemStack createCompass() {
        FileConfiguration c = plugin.getConfig();
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(c.getString("compass.name", "&bКомпас сокровищниц")));
        List<Component> lore = new ArrayList<>();
        for (String s : c.getStringList("compass.lore")) {
            lore.add(Msg.item(s));
        }
        meta.lore(lore);
        int cmd = c.getInt("compass.custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        meta.getPersistentDataContainer().set(compass, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isCompass(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return false;
        }
        return it.getItemMeta().getPersistentDataContainer().has(compass, PersistentDataType.BYTE);
    }

    public ItemStack createKey(int level, int amount) {
        LevelConfig lc = plugin.manager() == null ? null : plugin.manager().level(level);
        FileConfiguration c = lc != null ? lc.raw : plugin.getConfig();
        Material m = Material.matchMaterial(c.getString("key.material", "TRIAL_KEY"));
        if (m == null) {
            m = Material.TRIPWIRE_HOOK;
        }
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(Msg.replace(c.getString("key.name", "&bКлюч сокровищницы"), "level", level)));
        List<Component> lore = new ArrayList<>();
        for (String s : c.getStringList("key.lore")) {
            lore.add(Msg.item(Msg.replace(s, "level", level)));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, level);
        it.setItemMeta(meta);
        return it;
    }

    /** Уровень ключа или -1, если предмет не ключ. */
    public int keyLevel(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return -1;
        }
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        Integer v = pdc.get(key, PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }
}
