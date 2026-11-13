package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "miragedungeons.admin";

    private final MirageDungeons plugin;

    public DungeonCommand(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    private DungeonManager mgr() {
        return plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Msg m = plugin.msg();
        String sub = a.length == 0 ? "help" : a[0].toLowerCase();
        if (sub.equals("top")) {
            top(s);
            return true;
        }
        if (!s.hasPermission(ADMIN)) {
            if (sub.equals("help")) {
                m.send(s, "help-player");
            } else {
                m.send(s, "no-permission");
            }
            return true;
        }
        switch (sub) {
            case "spawn" -> spawn(s, a);
            case "list" -> list(s);
            case "validate" -> validate(s);
            case "open" -> forceOpen(s, a);
            case "regions" -> m.send(s, "regions-synced", "count", mgr().ensureRegions(true));
            case "info" -> info(s, a);
            case "tp" -> tp(s, a);
            case "remove" -> remove(s, a);
            case "removeall" -> removeAll(s);
            case "compass" -> compass(s, a);
            case "key" -> key(s, a);
            case "loot" -> loot(s, a);
            case "reload" -> {
                plugin.reloadAll();
                m.send(s, "reloaded");
                int issues = mgr().validate().size();
                if (issues > 0) {
                    m.send(s, "validate-issues", "count", issues);
                }
            }
            default -> {
                for (String line : m.rawList("help")) {
                    s.sendMessage(Msg.color(line));
                }
            }
        }
        return true;
    }

    private void spawn(CommandSender s, String[] a) {
        Msg m = plugin.msg();
        if (a.length < 2) {
            m.send(s, "usage-spawn");
            return;
        }
        boolean here = a.length > 2 && a[2].equalsIgnoreCase("here");
        World w;
        if (here) {
            if (!(s instanceof Player p)) {
                m.send(s, "players-only");
                return;
            }
            w = p.getWorld();
        } else if (a.length > 2) {
            w = Bukkit.getWorld(a[2]);
        } else if (s instanceof Player p) {
            w = p.getWorld();
        } else {
            List<String> ws = plugin.getConfig().getStringList("worlds");
            w = ws.isEmpty() ? null : Bukkit.getWorld(ws.get(0));
        }
        if (w == null) {
            m.send(s, "no-world");
            return;
        }
        LevelConfig lvl = null;
        String arg = a[1];
        if (arg.equalsIgnoreCase("random")) {
            lvl = mgr().pickLevel(w);
        } else {
            try {
                lvl = mgr().level(Integer.parseInt(arg));
            } catch (NumberFormatException e) {
                for (LevelConfig l : mgr().levels().values()) {
                    if (l.schematic.equalsIgnoreCase(arg) || l.schematic.equalsIgnoreCase(arg + ".schem")) {
                        lvl = l;
                    }
                }
            }
        }
        if (lvl == null) {
            m.send(s, "spawn-unknown", "arg", arg);
            return;
        }
        if (!mgr().schemFile(lvl.schematic).exists()) {
            m.send(s, "schem-missing", "file", lvl.schematic);
            return;
        }
        m.send(s, "spawn-started", "level", lvl.name);
        java.util.function.Consumer<Dungeon> cb = d -> {
            if (d == null) {
                m.send(s, "spawn-failed");
            } else {
                m.send(s, "spawn-ok", "id", d.id, "x", d.guardX, "y", d.guardY, "z", d.guardZ);
            }
        };
        if (here) {
            Location l = ((Player) s).getLocation();
            mgr().paste(lvl, w, l.getBlockX(), l.getBlockY(), l.getBlockZ(), cb);
        } else {
            mgr().spawnRandom(lvl, w, plugin.getConfig().getInt("spawn-attempts", 40), cb);
        }
    }

    /** Имитация рейда: уничтожает ядро данжа, как будто его взорвали. */
    private void forceOpen(CommandSender s, String[] a) {
        Msg m = plugin.msg();
        Dungeon d = a.length > 1 ? mgr().dungeons().get(a[1]) : null;
        if (d == null) {
            m.send(s, "not-found");
            return;
        }
        if (d.state != Dungeon.State.ACTIVE) {
            m.send(s, "open-cmd-bad");
            return;
        }
        mgr().open(d, s instanceof Player p ? p : null);
        m.send(s, "open-cmd-ok", "id", d.id);
    }

    private void validate(CommandSender s) {
        Msg m = plugin.msg();
        List<String> issues = mgr().validate();
        if (issues.isEmpty()) {
            m.send(s, "validate-ok");
            return;
        }
        m.send(s, "validate-header", "count", issues.size());
        for (String i : issues) {
            s.sendMessage(m.plain("validate-line", "text", i));
        }
    }

    private void info(CommandSender s, String[] a) {
        Msg m = plugin.msg();
        Dungeon d = a.length > 1 ? mgr().dungeons().get(a[1]) : null;
        if (d == null) {
            m.send(s, "not-found");
            return;
        }
        LevelConfig l = mgr().level(d.level);
        int opened = 0;
        int locked = 0;
        int mimics = 0;
        for (Dungeon.ChestInfo c : d.chests) {
            if (c.opened) {
                opened++;
            }
            if (c.locked) {
                locked++;
            }
            if (c.mimic) {
                mimics++;
            }
        }
        long ageMin = d.createdAt > 0 ? (System.currentTimeMillis() - d.createdAt) / 60000L : 0;
        for (String line : m.rawList("info", "id", d.id, "level", l == null ? d.level : l.name,
                "state", d.state.name(), "x", d.guardX, "y", d.guardY, "z", d.guardZ, "world", d.world,
                "chests", d.chests.size(), "opened", opened, "locked", locked, "mimics", mimics,
                "boss", d.boss != null ? "да" : "нет", "age", ageMin,
                "region", plugin.regions().id(d) + (plugin.regions().exists(d) ? " (есть)" : " (нет)"))) {
            s.sendMessage(Msg.color(line));
        }
    }

    private void list(CommandSender s) {
        Msg m = plugin.msg();
        if (mgr().dungeons().isEmpty()) {
            m.send(s, "list-empty");
            return;
        }
        for (Dungeon d : mgr().dungeons().values()) {
            s.sendMessage(m.get("list-line", "id", d.id, "level", d.level, "state", d.state.name(),
                    "x", d.guardX, "y", d.guardY, "z", d.guardZ, "world", d.world));
        }
    }

    private void tp(CommandSender s, String[] a) {
        Msg m = plugin.msg();
        if (!(s instanceof Player p)) {
            m.send(s, "players-only");
            return;
        }
        Dungeon d = a.length > 1 ? mgr().dungeons().get(a[1]) : null;
        if (d == null) {
            m.send(s, "not-found");
            return;
        }
        p.teleport(mgr().guardLocation(d).add(0, 2, 0));
        m.send(s, "tp-ok", "id", d.id);
    }

    private void remove(CommandSender s, String[] a) {
        Msg m = plugin.msg();
        Dungeon d = a.length > 1 ? mgr().dungeons().get(a[1]) : null;
        if (d == null) {
            m.send(s, "not-found");
            return;
        }
        mgr().remove(d, true, () -> m.send(s, "removed", "id", a[1]));
    }

    private void removeAll(CommandSender s) {
        List<Dungeon> all = new ArrayList<>(mgr().dungeons().values());
        for (Dungeon d : all) {
            mgr().remove(d, true, null);
        }
        plugin.msg().send(s, "removed-all", "count", all.size());
    }

    private Player target(CommandSender s, String[] a, int idx) {
        if (a.length > idx) {
            Player p = Bukkit.getPlayerExact(a[idx]);
            if (p == null) {
                plugin.msg().send(s, "player-not-found", "player", a[idx]);
            }
            return p;
        }
        if (s instanceof Player p) {
            return p;
        }
        plugin.msg().send(s, "players-only");
        return null;
    }

    private void compass(CommandSender s, String[] a) {
        Player p = target(s, a, 1);
        if (p == null) {
            return;
        }
        p.getInventory().addItem(plugin.items().createCompass());
        plugin.msg().send(s, "compass-given", "player", p.getName());
    }

    private void key(CommandSender s, String[] a) {
        if (a.length < 2) {
            plugin.msg().send(s, "usage-key");
            return;
        }
        int level;
        int amount = 1;
        try {
            level = Integer.parseInt(a[1]);
            if (a.length > 3) {
                amount = Integer.parseInt(a[3]);
            }
        } catch (NumberFormatException e) {
            plugin.msg().send(s, "usage-key");
            return;
        }
        Player p = target(s, a, 2);
        if (p == null) {
            return;
        }
        p.getInventory().addItem(plugin.items().createKey(level, amount));
        plugin.msg().send(s, "key-given", "player", p.getName(), "level", level);
    }

    private void loot(CommandSender s, String[] a) {
        Msg m = plugin.msg();
        if (a.length < 3) {
            m.send(s, "usage-loot");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            m.send(s, "usage-loot");
            return;
        }
        if (mgr().level(level) == null) {
            m.send(s, "loot-bad-level");
            return;
        }
        LootManager lm = plugin.loot();
        switch (a[1].toLowerCase()) {
            case "add" -> {
                if (!(s instanceof Player p)) {
                    m.send(s, "players-only");
                    return;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    m.send(s, "loot-hold-item");
                    return;
                }
                double weight = 10;
                if (a.length > 3) {
                    try {
                        weight = Double.parseDouble(a[3]);
                    } catch (NumberFormatException e) {
                        m.send(s, "usage-loot");
                        return;
                    }
                }
                lm.add(level, hand, weight);
                m.send(s, "loot-added", "level", level, "weight", weight);
            }
            case "list" -> {
                m.send(s, "loot-list-header", "level", level);
                int i = 0;
                for (LootManager.Entry e : lm.entries(level)) {
                    String what = e.item != null
                            ? e.item.getType().name() + " x" + e.item.getAmount()
                            : e.material.name() + " " + e.min + "-" + e.max;
                    s.sendMessage(m.plain("loot-list-line", "index", i++, "item", what, "weight", e.weight));
                }
            }
            case "remove" -> {
                int idx = -1;
                try {
                    idx = a.length > 3 ? Integer.parseInt(a[3]) : -1;
                } catch (NumberFormatException ignored) {
                    idx = -1;
                }
                m.send(s, lm.remove(level, idx) ? "loot-removed" : "loot-bad-index");
            }
            case "clear" -> {
                lm.clear(level);
                m.send(s, "loot-cleared", "level", level);
            }
            case "gui" -> {
                if (!(s instanceof Player p)) {
                    m.send(s, "players-only");
                    return;
                }
                plugin.lootGui().open(p, level);
            }
            case "amount" -> {
                if (a.length < 5) {
                    m.send(s, "usage-loot");
                    return;
                }
                try {
                    int[] r = LevelConfig.range(a[4], 1, 1);
                    m.send(s, lm.setAmount(level, Integer.parseInt(a[3]), r[0], r[1]) ? "loot-amount-set" : "loot-bad-index");
                } catch (NumberFormatException ex) {
                    m.send(s, "usage-loot");
                }
            }
            default -> m.send(s, "usage-loot");
        }
    }

    private void top(CommandSender s) {
        Msg m = plugin.msg();
        List<Map.Entry<String, Integer>> list = mgr().top();
        if (list.isEmpty()) {
            m.send(s, "top-empty");
            return;
        }
        s.sendMessage(m.plain("top-header"));
        int n = 0;
        for (Map.Entry<String, Integer> e : list) {
            if (++n > 10) {
                break;
            }
            s.sendMessage(m.plain("top-line", "pos", n, "player", e.getKey(), "count", e.getValue()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("top");
            if (s.hasPermission(ADMIN)) {
                out.addAll(List.of("spawn", "list", "info", "validate", "open", "regions", "tp", "remove", "removeall", "compass", "key", "loot", "reload", "help"));
            }
        } else if (s.hasPermission(ADMIN)) {
            String sub = a[0].toLowerCase();
            if (a.length == 2) {
                switch (sub) {
                    case "spawn" -> {
                        out.add("random");
                        mgr().levels().keySet().forEach(k -> out.add(String.valueOf(k)));
                    }
                    case "tp", "remove", "info", "open" -> out.addAll(mgr().dungeons().keySet());
                    case "compass" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    case "key" -> mgr().levels().keySet().forEach(k -> out.add(String.valueOf(k)));
                    case "loot" -> out.addAll(List.of("add", "list", "remove", "clear", "gui", "amount"));
                    default -> { }
                }
            } else if (a.length == 3) {
                switch (sub) {
                    case "spawn" -> {
                        out.add("here");
                        Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
                    }
                    case "key" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    case "loot" -> mgr().levels().keySet().forEach(k -> out.add(String.valueOf(k)));
                    default -> { }
                }
            }
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
