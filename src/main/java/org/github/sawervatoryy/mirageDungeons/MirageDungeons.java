package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MirageDungeons extends JavaPlugin {

    private Msg msg;
    private Items items;
    private LootManager loot;
    private Events events;
    private Regions regions;
    private LootGui lootGui;
    private DungeonManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new File(getDataFolder(), "schematics").mkdirs();
        // Готовые схематики из jar копируем в папку, если их там ещё нет
        for (String n : new String[]{"dang1.schem", "dang2.schem", "dang3.schem"}) {
            if (!new File(getDataFolder(), "schematics/" + n).exists() && getResource("schematics/" + n) != null) {
                saveResource("schematics/" + n, false);
            }
        }
        msg = new Msg(this);
        msg.reload();
        items = new Items(this);
        events = new Events(this);
        regions = new Regions(this);
        loot = new LootManager(this);
        loot.reload();
        manager = new DungeonManager(this);
        manager.load();
        lootGui = new LootGui(this);

        DungeonCommand cmd = new DungeonCommand(this);
        PluginCommand pc = getCommand("randomdang");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(new DungeonListener(this), this);
        getServer().getPluginManager().registerEvents(lootGui, this);
        manager.startTasks();
        getLogger().info("MirageDungeons включён. Уровней: " + manager.levels().size());
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        msg.reload();
        loot.reload();
        manager.reloadLevels();
    }

    public Msg msg() {
        return msg;
    }

    public Items items() {
        return items;
    }

    public LootGui lootGui() {
        return lootGui;
    }

    public Regions regions() {
        return regions;
    }

    public Events events() {
        return events;
    }

    public LootManager loot() {
        return loot;
    }

    public DungeonManager manager() {
        return manager;
    }
}
