package org.github.sawervatoryy.mirageDungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Реакции на события из секции events: звуки, сообщения, титры, салюты и команды.
 * События: spawn, open, countdown-done, chest-open, unlock, loot-complete, boss-death, mimic, trap.
 */
public final class Events {

    private final MirageDungeons plugin;

    public Events(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    public void fire(String event, LevelConfig lvl, Dungeon d, Player p, Location at) {
        if (lvl == null || lvl.events == null) {
            return;
        }
        ConfigurationSection s = lvl.events.getConfigurationSection(event);
        if (s == null || !s.getBoolean("enabled", true)) {
            return;
        }
        World w = at != null ? at.getWorld() : (d != null ? Bukkit.getWorld(d.world) : null);
        int x = at != null ? at.getBlockX() : (d != null ? d.guardX : 0);
        int y = at != null ? at.getBlockY() : (d != null ? d.guardY : 0);
        int z = at != null ? at.getBlockZ() : (d != null ? d.guardZ : 0);
        Object[] kv = {
                "player", p == null ? plugin.msg().raw("unknown-player") : p.getName(),
                "level", lvl.name, "level-id", lvl.id, "id", d == null ? "" : d.id,
                "x", x, "y", y, "z", z, "world", w == null ? "" : w.getName(),
                "block", lvl.guardBlockName
        };

        if (s.getBoolean("explosion", false) && at != null && w != null) {
            w.spawnParticle(Particle.EXPLOSION, at, 3, 0.5, 0.5, 0.5, 0);
        }
        if (s.getBoolean("lightning", false) && at != null && w != null) {
            w.strikeLightningEffect(at);
        }
        for (String line : s.getStringList("particles")) {
            particle(line, w, at);
        }
        for (String line : s.getStringList("sounds")) {
            sound(line, "here", w, at, p);
        }
        if (p != null) {
            for (String line : s.getStringList("messages")) {
                p.sendMessage(Msg.color(Msg.replace(line, kv)));
            }
            String ab = s.getString("actionbar", "");
            if (!ab.isEmpty()) {
                p.sendActionBar(Msg.color(Msg.replace(ab, kv)));
            }
        }
        for (String line : s.getStringList("broadcast")) {
            Bukkit.broadcast(Msg.color(Msg.replace(line, kv)));
        }
        double radius = s.getDouble("nearby-radius", 50);
        for (String line : s.getStringList("nearby-messages")) {
            for (Player o : nearby(w, at, radius)) {
                o.sendMessage(Msg.color(Msg.replace(line, kv)));
            }
        }
        ConfigurationSection t = s.getConfigurationSection("title");
        if (t != null) {
            Title title = Title.title(
                    Msg.color(Msg.replace(t.getString("title", ""), kv)),
                    Msg.color(Msg.replace(t.getString("subtitle", ""), kv)),
                    Title.Times.times(
                            Duration.ofMillis(t.getInt("fade-in", 10) * 50L),
                            Duration.ofMillis(t.getInt("stay", 40) * 50L),
                            Duration.ofMillis(t.getInt("fade-out", 10) * 50L)));
            String target = t.getString("target", "player").toLowerCase();
            if (target.equals("all")) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    o.showTitle(title);
                }
            } else if (target.equals("nearby")) {
                for (Player o : nearby(w, at, t.getDouble("radius", radius))) {
                    o.showTitle(title);
                }
            } else if (p != null) {
                p.showTitle(title);
            }
        }
        ConfigurationSection f = s.getConfigurationSection("firework");
        if (f != null && f.getBoolean("enabled", true) && at != null && w != null) {
            for (int i = 0; i < Math.max(1, f.getInt("count", 1)); i++) {
                firework(f, at);
            }
        }
        for (String cmd : s.getStringList("commands")) {
            runCommand(cmd, kv, p);
        }
    }

    /** Строка звука: имя:громкость:высота:цель (here | player | world | all). Имя без minecraft:. */
    public void sound(String line, String defaultTarget, World w, Location at, Player p) {
        if (line == null || line.isBlank()) {
            return;
        }
        String[] parts = line.trim().split(":");
        String name = parts[0].trim();
        float vol = 1f;
        float pitch = 1f;
        String target = defaultTarget;
        try {
            if (parts.length > 1) {
                vol = Float.parseFloat(parts[1].trim());
            }
            if (parts.length > 2) {
                pitch = Float.parseFloat(parts[2].trim());
            }
        } catch (NumberFormatException ignored) {
            vol = 1f;
            pitch = 1f;
        }
        if (parts.length > 3) {
            target = parts[3].trim().toLowerCase();
        }
        switch (target) {
            case "player" -> {
                if (p != null) {
                    p.playSound(p.getLocation(), name, vol, pitch);
                }
            }
            case "world" -> {
                if (w != null) {
                    for (Player o : w.getPlayers()) {
                        o.playSound(o.getLocation(), name, vol, pitch);
                    }
                }
            }
            case "all" -> {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    o.playSound(o.getLocation(), name, vol, pitch);
                }
            }
            default -> {
                if (w != null && at != null) {
                    w.playSound(at, name, vol, pitch);
                }
            }
        }
    }

    /** Строка частиц: ТИП:количество:разброс X:разброс Y:разброс Z:скорость. */
    private void particle(String line, World w, Location at) {
        if (w == null || at == null || line == null || line.isBlank()) {
            return;
        }
        String[] p = line.trim().split(":");
        Particle type = parseParticle(p[0].trim());
        if (type == null) {
            plugin.getLogger().warning("События: неизвестная частица «" + p[0] + "»");
            return;
        }
        int count = 10;
        double dx = 0.3;
        double dy = 0.3;
        double dz = 0.3;
        double speed = 0.05;
        try {
            if (p.length > 1) {
                count = Integer.parseInt(p[1].trim());
            }
            if (p.length > 2) {
                dx = Double.parseDouble(p[2].trim());
            }
            if (p.length > 3) {
                dy = Double.parseDouble(p[3].trim());
            }
            if (p.length > 4) {
                dz = Double.parseDouble(p[4].trim());
            }
            if (p.length > 5) {
                speed = Double.parseDouble(p[5].trim());
            }
        } catch (NumberFormatException ignored) {
            // значения по умолчанию
        }
        try {
            w.spawnParticle(type, at, count, dx, dy, dz, speed);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("События: частице «" + p[0] + "» нужны дополнительные данные, она не поддерживается");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Particle parseParticle(String name) {
        try {
            return (Particle) Enum.valueOf((Class) Particle.class, name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Player> nearby(World w, Location at, double radius) {
        List<Player> out = new ArrayList<>();
        if (w == null || at == null) {
            return out;
        }
        for (Player o : w.getPlayers()) {
            if (o.getLocation().distanceSquared(at) <= radius * radius) {
                out.add(o);
            }
        }
        return out;
    }

    private void firework(ConfigurationSection f, Location at) {
        Firework fw = at.getWorld().spawn(at, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect.Type type = FireworkEffect.Type.BALL_LARGE;
        try {
            type = FireworkEffect.Type.valueOf(f.getString("type", "BALL_LARGE").toUpperCase());
        } catch (IllegalArgumentException ignored) {
            type = FireworkEffect.Type.BALL_LARGE;
        }
        List<Color> colors = colors(f.getStringList("colors"));
        if (colors.isEmpty()) {
            colors = List.of(Color.WHITE, Color.AQUA);
        }
        FireworkEffect.Builder b = FireworkEffect.builder()
                .with(type)
                .withColor(colors)
                .trail(f.getBoolean("trail", true))
                .flicker(f.getBoolean("flicker", false));
        List<Color> fade = colors(f.getStringList("fade"));
        if (!fade.isEmpty()) {
            b.withFade(fade);
        }
        meta.addEffect(b.build());
        meta.setPower(Math.max(0, f.getInt("power", 0)));
        fw.setFireworkMeta(meta);
        if (f.getBoolean("instant", true)) {
            fw.detonate();
        }
    }

    private static List<Color> colors(List<String> names) {
        List<Color> out = new ArrayList<>();
        for (String n : names) {
            Color c = color(n.trim());
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static Color color(String n) {
        if (n.startsWith("#") && n.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(n.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (n.toUpperCase()) {
            case "WHITE" -> Color.WHITE;
            case "SILVER" -> Color.SILVER;
            case "GRAY" -> Color.GRAY;
            case "BLACK" -> Color.BLACK;
            case "RED" -> Color.RED;
            case "MAROON" -> Color.MAROON;
            case "YELLOW" -> Color.YELLOW;
            case "OLIVE" -> Color.OLIVE;
            case "LIME" -> Color.LIME;
            case "GREEN" -> Color.GREEN;
            case "AQUA" -> Color.AQUA;
            case "TEAL" -> Color.TEAL;
            case "BLUE" -> Color.BLUE;
            case "NAVY" -> Color.NAVY;
            case "FUCHSIA" -> Color.FUCHSIA;
            case "PURPLE" -> Color.PURPLE;
            case "ORANGE" -> Color.ORANGE;
            default -> null;
        };
    }

    private void runCommand(String raw, Object[] kv, Player p) {
        if (p == null && raw.contains("{player}")) {
            return;
        }
        String c = Msg.replace(raw, kv).trim();
        boolean asPlayer = false;
        if (c.regionMatches(true, 0, "[player]", 0, 8)) {
            asPlayer = true;
            c = c.substring(8).trim();
        } else if (c.regionMatches(true, 0, "[console]", 0, 9)) {
            c = c.substring(9).trim();
        }
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        if (c.isEmpty()) {
            return;
        }
        if (asPlayer) {
            if (p != null) {
                Bukkit.dispatchCommand(p, c);
            }
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        }
    }
}
