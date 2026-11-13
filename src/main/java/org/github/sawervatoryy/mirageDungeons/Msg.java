package org.github.sawervatoryy.mirageDungeons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Сообщения из messages.yml. Цвета: &f и &b (HEX вида &#RRGGBB тоже поддерживается). */
public final class Msg {

    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private final MirageDungeons plugin;
    private FileConfiguration cfg;

    public Msg(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public static Component color(String s) {
        return AMP.deserialize(s == null ? "" : s);
    }

    /** Для названий и описаний предметов (без курсива). */
    public static Component item(String s) {
        return color(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Для BossBar и других мест, где нужна строка со знаком §. */
    public static String legacy(String s) {
        return SECTION.serialize(color(s));
    }

    public static String replace(String s, Object... kv) {
        if (s == null) {
            return "";
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public String raw(String key, Object... kv) {
        return replace(cfg.getString(key, "&f" + key), kv);
    }

    public List<String> rawList(String key, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String s : cfg.getStringList(key)) {
            out.add(replace(s, kv));
        }
        return out;
    }

    /** Сообщение с префиксом. */
    public Component get(String key, Object... kv) {
        return color(raw("prefix") + raw(key, kv));
    }

    /** Сообщение без префикса. */
    public Component plain(String key, Object... kv) {
        return color(raw(key, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(get(key, kv));
    }
}
