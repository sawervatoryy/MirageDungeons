package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Настройки одного уровня. Берутся из корня config.yml и поверх них накладываются
 * значения из секции levels.N — то есть ЛЮБОЙ глобальный параметр можно переопределить в уровне.
 */
public final class LevelConfig {

    /** Предмет, который падает с моба или кладётся в сундук: MATERIAL:мин-макс:шанс%. */
    public static final class DropSpec {
        public Material material;
        public int min = 1;
        public int max = 1;
        public double chance = 100;
    }

    /** Моб-страж, босс или хранитель ключа. */
    public static final class MobSpec {
        public EntityType type = EntityType.ZOMBIE;
        public int amount = 1;
        public double health = 20;
        public String name = "";
        public boolean keyHolder = false;
        public boolean glowing = false;
        public int bonusRolls = 0;
        public int wave = 1;
        public int radius = -1;
        public double attackDamage = -1;
        public double speed = -1;
        public int exp = -1;
        public boolean clearDrops = false;
        public Material helmet;
        public Material chest;
        public Material legs;
        public Material boots;
        public Material hand;
        public List<PotionEffect> effects = new ArrayList<>();
        public List<DropSpec> drops = new ArrayList<>();
    }

    public int id;
    public YamlConfiguration raw;
    public final List<String> warnings = new ArrayList<>();

    public boolean enabled = true;
    public String name;
    public String schematic;
    public Material guardBlock = Material.LODESTONE;
    public String guardBlockName = "Магнетит";
    public Set<Material> chestBlocks = EnumSet.noneOf(Material.class);

    public int lootMin = 5;
    public int lootMax = 10;
    public boolean lootRandom = true;
    public Map<Material, int[]> lootByType = new HashMap<>();
    public List<DropSpec> guaranteed = new ArrayList<>();

    public int weight = 10;
    public int maxActive = 0;
    public int sink = 0;
    public int rgRadius = 10;
    public double mimicChance = 0;
    public double lockedPercent = 0;
    public int openDelay = 0;
    public boolean alarm = false;
    public String alarmMessage = "alarm";
    public String alarmSound = "";
    public int compassBlur = 0;
    public boolean beam = false;
    public int beamHeight = 120;
    public int beamStep = 3;
    public String enterDisplay = "actionbar";

    // правила спавна
    public List<String> worlds = new ArrayList<>();
    public int spawnMinX = -2000;
    public int spawnMaxX = 2000;
    public int spawnMinZ = -2000;
    public int spawnMaxZ = 2000;
    public int minY = -64;
    public int maxY = 320;
    public double minDistanceBetween = 150;
    public double minDistanceFromSpawn = 0;
    public boolean avoidLiquid = true;
    public boolean respectBorder = true;
    public Set<Material> groundBlacklist = EnumSet.noneOf(Material.class);
    public Set<String> biomeWhitelist = new HashSet<>();
    public Set<String> biomeBlacklist = new HashSet<>();

    // мобы
    public int guardianRadius = 6;
    public int waveDelay = 20;
    public double barRadius = 64;
    public List<MobSpec> guardians = new ArrayList<>();
    public MobSpec boss;
    public BarColor countdownColor = BarColor.WHITE;
    public BarStyle countdownStyle = BarStyle.SOLID;
    public BarColor bossColor = BarColor.BLUE;
    public BarStyle bossStyle = BarStyle.SEGMENTED_10;

    // аура, ловушки, мимики
    public List<PotionEffect> aura = new ArrayList<>();
    public Set<Dungeon.State> auraPhases = EnumSet.of(Dungeon.State.ACTIVE, Dungeon.State.COUNTDOWN, Dungeon.State.OPEN);
    public int trapDamage = 0;
    public int trapArrows = 0;
    public double trapChance = 100;
    public long trapCooldownMs = 3000;
    public boolean trapMessage = true;
    public List<PotionEffect> trapEffects = new ArrayList<>();
    public EntityType mimicType = EntityType.ZOMBIE;
    public String mimicName = "&bМимик";
    public double mimicHealth = 40;
    public int mimicRolls = 2;

    // вставка схематики
    public String pasteOrigin = "schematic";
    public int groundLayer = 0;
    public Set<Material> skipBlocks = EnumSet.noneOf(Material.class);
    public Map<Material, Material> replaceBlocks = new HashMap<>();
    // ядро (блок привата) в центре
    public boolean coreCenter = false;
    public int coreY = -1;
    public int coreRadius = 3;
    /** Слои оболочки вокруг ядра: от ближнего к дальнему, материал → вес. */
    public List<Map<Material, Integer>> shieldLayers = new ArrayList<>();
    // автоматически добавляемые сундуки
    public boolean acEnabled = false;
    public int acMin = 3;
    public int acMax = 5;
    public Material acBlock = Material.CHEST;
    public int acMinDist = 3;
    public int acSpacing = 3;
    public int acMinY = 0;
    public int acMaxY = -1;

    // защита
    public boolean wgMode = true;
    public int wgPriority = 1000;
    public List<String> flagsBefore = new ArrayList<>();
    public List<String> flagsAfter = new ArrayList<>();
    public final Map<String, Boolean> prot = new HashMap<>();
    public boolean pvp = true;
    public boolean wgRemoveOnRaid = true;
    // защита от лавакастов, обсидиана и древних обломков
    public boolean agEnabled = true;
    public Set<Dungeon.State> agPhases = EnumSet.of(Dungeon.State.ACTIVE);
    public Set<Material> agBuckets = EnumSet.noneOf(Material.class);
    public boolean agFluidFlow = true;
    public Set<Material> agGeneration = EnumSet.noneOf(Material.class);
    public Set<Material> agPlace = EnumSet.noneOf(Material.class);
    public Set<Material> breakWhite = EnumSet.noneOf(Material.class);
    public Set<Material> breakBlack = EnumSet.noneOf(Material.class);
    public Set<Material> placeWhite = EnumSet.noneOf(Material.class);
    public Set<Material> placeBlack = EnumSet.noneOf(Material.class);
    public Set<String> blockedCommands = new HashSet<>();

    public ConfigurationSection events;

    /** Действует ли сейчас защита от лавакастов и блоков-укреплений в этом данже. */
    public boolean antiGrief(Dungeon d) {
        return agEnabled && agPhases.contains(d.state);
    }

    public boolean allowsWorld(String world) {
        return worlds.isEmpty() || worlds.contains(world);
    }

    // ------------------------------------------------------------------ загрузка

    public static LevelConfig load(int id, ConfigurationSection root, ConfigurationSection lvlSec) {
        YamlConfiguration m = new YamlConfiguration();
        copyLeaves(root, m, true);
        copyLeaves(lvlSec, m, false);

        LevelConfig l = new LevelConfig();
        l.id = id;
        l.raw = m;
        List<String> w = l.warnings;

        l.enabled = m.getBoolean("enabled", true);
        l.name = m.getString("name", "&fСокровищница " + id);
        l.schematic = m.getString("schematic", "dang" + id + ".schem");
        Material gb = mat(m.getString("guard-block", "LODESTONE"));
        if (gb == null) {
            w.add("guard-block: неизвестный материал " + m.getString("guard-block"));
            gb = Material.LODESTONE;
        }
        l.guardBlock = gb;
        l.guardBlockName = m.getString("guard-block-name", gb.name());

        List<String> cb = m.getStringList("chest-blocks");
        if (cb.isEmpty()) {
            cb = List.of("CHEST", "TRAPPED_CHEST", "BARREL");
        }
        l.chestBlocks = mats(cb, w, "chest-blocks");

        int[] la = range(m.getString("loot-amount", "5-10"), 5, 10);
        l.lootMin = la[0];
        l.lootMax = la[1];
        l.lootRandom = !"default".equalsIgnoreCase(m.getString("loot-logical", "random"));
        ConfigurationSection byType = m.getConfigurationSection("loot-amount-by-type");
        if (byType != null) {
            for (String k : byType.getKeys(false)) {
                Material mt = mat(k);
                if (mt == null) {
                    w.add("loot-amount-by-type: неизвестный материал " + k);
                    continue;
                }
                l.lootByType.put(mt, range(byType.getString(k), l.lootMin, l.lootMax));
            }
        }
        for (String s : m.getStringList("guaranteed-loot")) {
            DropSpec d = parseDrop(s);
            if (d == null) {
                w.add("guaranteed-loot: не понял запись " + s);
            } else {
                l.guaranteed.add(d);
            }
        }

        l.weight = m.getInt("spawn-weight", 10);
        l.maxActive = m.getInt("max-active", 0);
        l.sink = m.getInt("sink", 0);
        l.rgRadius = Math.max(1, m.getInt("rg-radius", 10));
        l.mimicChance = m.getDouble("mimic-chance", 0);
        l.lockedPercent = m.getDouble("locked-chests-percent", 0);
        l.openDelay = m.getInt("open-delay", 0);
        l.alarm = m.getBoolean("alarm", false);
        l.alarmMessage = m.getString("alarm-message", "alarm");
        l.alarmSound = m.getString("alarm-sound", "");
        l.compassBlur = m.getInt("compass-blur", 0);
        l.beam = m.getBoolean("beam", false);
        l.beamHeight = m.getInt("beam-height", 120);
        l.beamStep = Math.max(1, m.getInt("beam-step", 3));
        l.enterDisplay = m.getString("enter-display", "actionbar").toLowerCase();

        l.worlds = new ArrayList<>(m.getStringList("worlds"));
        l.spawnMinX = m.getInt("spawn-min-x", -2000);
        l.spawnMaxX = m.getInt("spawn-max-x", 2000);
        l.spawnMinZ = m.getInt("spawn-min-z", -2000);
        l.spawnMaxZ = m.getInt("spawn-max-z", 2000);
        l.minY = m.getInt("spawn-min-y", -64);
        l.maxY = m.getInt("spawn-max-y", 320);
        l.minDistanceBetween = m.getDouble("min-distance-between", 150);
        l.minDistanceFromSpawn = m.getDouble("min-distance-from-spawn", 0);
        l.avoidLiquid = m.getBoolean("avoid-liquid", true);
        l.respectBorder = m.getBoolean("respect-world-border", true);
        l.groundBlacklist = mats(m.getStringList("ground-blacklist"), w, "ground-blacklist");
        for (String b : m.getStringList("biome-whitelist")) {
            l.biomeWhitelist.add(biome(b));
        }
        for (String b : m.getStringList("biome-blacklist")) {
            l.biomeBlacklist.add(biome(b));
        }

        l.guardianRadius = m.getInt("guardian-radius", 6);
        l.waveDelay = Math.max(0, m.getInt("wave-delay", 20));
        l.barRadius = m.getDouble("bar-radius", 64);
        l.countdownColor = en(BarColor.class, m.getString("bars.countdown.color"), BarColor.WHITE);
        l.countdownStyle = en(BarStyle.class, m.getString("bars.countdown.style"), BarStyle.SOLID);
        l.bossColor = en(BarColor.class, m.getString("bars.boss.color"), BarColor.BLUE);
        l.bossStyle = en(BarStyle.class, m.getString("bars.boss.style"), BarStyle.SEGMENTED_10);
        for (Map<?, ?> mm : m.getMapList("guardians")) {
            l.guardians.add(mob(mm, w));
        }
        ConfigurationSection b = m.getConfigurationSection("boss");
        if (b != null) {
            l.boss = mob(b.getValues(false), w);
        }

        l.aura = effects(m.getStringList("aura-effects"), 100, w);
        List<String> phases = m.getStringList("aura-phases");
        if (!phases.isEmpty()) {
            l.auraPhases = EnumSet.noneOf(Dungeon.State.class);
            for (String ph : phases) {
                Dungeon.State st = en(Dungeon.State.class, ph, null);
                if (st != null) {
                    l.auraPhases.add(st);
                }
            }
        }
        l.trapDamage = m.getInt("trap.damage", 0);
        l.trapArrows = m.getInt("trap.arrows", 0);
        l.trapChance = m.getDouble("trap.chance", 100);
        l.trapCooldownMs = (long) (m.getDouble("trap.cooldown-seconds", 3) * 1000);
        l.trapMessage = m.getBoolean("trap.message", true);
        l.trapEffects = effects(m.getStringList("trap.effects"), 0, w);
        l.mimicType = en(EntityType.class, m.getString("mimic.type"), EntityType.ZOMBIE);
        l.mimicName = m.getString("mimic.name", "&bМимик");
        l.mimicHealth = m.getDouble("mimic.health", 40);
        l.mimicRolls = m.getInt("mimic.bonus-rolls", 2);

        for (String phase : new String[]{"before-open", "after-open"}) {
            for (String flag : new String[]{"block-break", "block-place", "tnt"}) {
                boolean def = phase.equals("after-open") && !flag.equals("tnt");
                l.prot.put(phase + "." + flag, m.getBoolean("protection." + phase + "." + flag, def));
            }
        }
        l.pvp = m.getBoolean("protection.pvp", true);
        l.wgMode = !"internal".equalsIgnoreCase(m.getString("protection.mode", "worldguard"));
        l.wgPriority = m.getInt("worldguard.priority", 1000);
        // если в старом config.yml нет секции worldguard, берём разумные флаги по умолчанию
        l.flagsBefore = m.contains("worldguard.flags-before")
                ? new ArrayList<>(m.getStringList("worldguard.flags-before"))
                : new ArrayList<>(List.of("build deny", "tnt deny", "pvp allow", "chest-access deny", "use allow",
                        "interact allow", "lava-flow deny", "water-flow deny"));
        l.flagsAfter = m.contains("worldguard.flags-after")
                ? new ArrayList<>(m.getStringList("worldguard.flags-after"))
                : new ArrayList<>(List.of("build allow", "block-break allow", "block-place allow", "tnt deny",
                        "pvp allow", "use allow", "interact allow", "chest-access allow"));
        l.wgRemoveOnRaid = m.getBoolean("worldguard.remove-on-raid", true);

        l.agEnabled = m.getBoolean("protection.anti-grief.enabled", true);
        List<String> agPh = m.contains("protection.anti-grief.phases")
                ? m.getStringList("protection.anti-grief.phases") : List.of("ACTIVE");
        l.agPhases = EnumSet.noneOf(Dungeon.State.class);
        for (String ph : agPh) {
            Dungeon.State st = en(Dungeon.State.class, ph, null);
            if (st != null) {
                l.agPhases.add(st);
            }
        }
        l.agBuckets = mats(m.contains("protection.anti-grief.deny-buckets")
                ? m.getStringList("protection.anti-grief.deny-buckets")
                : List.of("LAVA_BUCKET", "WATER_BUCKET"), w, "anti-grief.deny-buckets");
        l.agFluidFlow = m.getBoolean("protection.anti-grief.deny-fluid-flow", true);
        l.agGeneration = mats(m.contains("protection.anti-grief.deny-generation")
                ? m.getStringList("protection.anti-grief.deny-generation")
                : List.of("OBSIDIAN", "COBBLESTONE", "STONE", "BASALT"), w, "anti-grief.deny-generation");
        l.agPlace = mats(m.contains("protection.anti-grief.deny-place")
                ? m.getStringList("protection.anti-grief.deny-place")
                : List.of("OBSIDIAN", "CRYING_OBSIDIAN", "ANCIENT_DEBRIS", "NETHERITE_BLOCK"), w, "anti-grief.deny-place");

        l.pasteOrigin = m.getString("paste.origin", "schematic").toLowerCase();
        l.groundLayer = m.getInt("paste.ground-layer", 0);
        l.skipBlocks = mats(m.getStringList("paste.skip-blocks"), w, "paste.skip-blocks");
        ConfigurationSection rb = m.getConfigurationSection("paste.replace");
        if (rb != null) {
            for (String k : rb.getKeys(false)) {
                Material from = mat(k);
                Material to = mat(rb.getString(k));
                if (from == null || to == null) {
                    w.add("paste.replace: неизвестный материал в записи " + k + ": " + rb.getString(k));
                } else {
                    l.replaceBlocks.put(from, to);
                }
            }
        }
        l.coreCenter = "center".equalsIgnoreCase(m.getString("core.mode", "schematic"));
        String coreY = m.getString("core.y", "auto");
        try {
            l.coreY = Integer.parseInt(coreY.trim());
        } catch (NumberFormatException ex) {
            l.coreY = -1;
        }
        l.coreRadius = Math.max(0, m.getInt("core.search-radius", 3));
        if (m.getBoolean("core.shield.enabled", false)) {
            for (Map<?, ?> layer : m.getMapList("core.shield.layers")) {
                Map<Material, Integer> wts = new HashMap<>();
                for (Map.Entry<?, ?> en : layer.entrySet()) {
                    Material mt = mat(String.valueOf(en.getKey()));
                    if (mt == null) {
                        w.add("core.shield.layers: неизвестный материал " + en.getKey());
                    } else {
                        wts.put(mt, num(en.getValue(), 1).intValue());
                    }
                }
                if (!wts.isEmpty()) {
                    l.shieldLayers.add(wts);
                }
            }
        }
        l.acEnabled = m.getBoolean("auto-chests.enabled", false);
        int[] ac = range(m.getString("auto-chests.count", "3-5"), 3, 5);
        l.acMin = ac[0];
        l.acMax = ac[1];
        Material acb = mat(m.getString("auto-chests.block", "CHEST"));
        if (acb == null) {
            w.add("auto-chests.block: неизвестный материал " + m.getString("auto-chests.block"));
            acb = Material.CHEST;
        }
        l.acBlock = acb;
        l.acMinDist = Math.max(0, m.getInt("auto-chests.min-distance-from-core", 3));
        l.acSpacing = Math.max(0, m.getInt("auto-chests.min-spacing", 3));
        l.acMinY = Math.max(0, m.getInt("auto-chests.min-layer", 0));
        l.acMaxY = m.getInt("auto-chests.max-layer", -1);
        l.breakWhite = mats(m.getStringList("protection.break-whitelist"), w, "protection.break-whitelist");
        l.breakBlack = mats(m.getStringList("protection.break-blacklist"), w, "protection.break-blacklist");
        l.placeWhite = mats(m.getStringList("protection.place-whitelist"), w, "protection.place-whitelist");
        l.placeBlack = mats(m.getStringList("protection.place-blacklist"), w, "protection.place-blacklist");
        for (String c : m.getStringList("protection.blocked-commands")) {
            String s = c.toLowerCase().trim();
            l.blockedCommands.add(s.startsWith("/") ? s.substring(1) : s);
        }

        l.events = m.getConfigurationSection("events");
        return l;
    }

    private static void copyLeaves(ConfigurationSection from, ConfigurationSection to, boolean skipLevels) {
        for (String k : from.getKeys(true)) {
            if (skipLevels && (k.equals("levels") || k.startsWith("levels."))) {
                continue;
            }
            if (from.isConfigurationSection(k)) {
                continue;
            }
            to.set(k, from.get(k));
        }
    }

    // ------------------------------------------------------------------ разбор значений

    private static String biome(String s) {
        String b = s.toLowerCase().trim();
        return b.startsWith("minecraft:") ? b.substring(10) : b;
    }

    private static Material mat(String s) {
        return s == null ? null : Material.matchMaterial(s.trim());
    }

    private static Set<Material> mats(List<String> list, List<String> warn, String what) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String s : list) {
            Material m = mat(s);
            if (m == null) {
                warn.add(what + ": неизвестный материал " + s);
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private static <E extends Enum<E>> E en(Class<E> c, String s, E def) {
        if (s == null) {
            return def;
        }
        try {
            return Enum.valueOf(c, s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    /** Диапазон "5-10" или одно число. */
    public static int[] range(String s, int dmin, int dmax) {
        if (s == null) {
            return new int[]{dmin, dmax};
        }
        String[] p = s.trim().split("-");
        try {
            int a = Integer.parseInt(p[0].trim());
            int b = p.length > 1 ? Integer.parseInt(p[1].trim()) : a;
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (NumberFormatException e) {
            return new int[]{dmin, dmax};
        }
    }

    public static DropSpec parseDrop(String s) {
        String[] p = s.trim().split(":");
        Material m = mat(p[0]);
        if (m == null) {
            return null;
        }
        DropSpec d = new DropSpec();
        d.material = m;
        if (p.length > 1) {
            int[] r = range(p[1], 1, 1);
            d.min = Math.max(1, r[0]);
            d.max = Math.max(d.min, r[1]);
        }
        if (p.length > 2) {
            try {
                d.chance = Double.parseDouble(p[2].trim());
            } catch (NumberFormatException ignored) {
                d.chance = 100;
            }
        }
        return d;
    }

    public static String dropsToString(List<DropSpec> list) {
        StringBuilder sb = new StringBuilder();
        for (DropSpec d : list) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(d.material.name()).append(':').append(d.min).append('-').append(d.max)
                    .append(':').append(d.chance);
        }
        return sb.toString();
    }

    public static List<DropSpec> dropsFromString(String s) {
        List<DropSpec> out = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String part : s.split(";")) {
            DropSpec d = parseDrop(part);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    private static MobSpec mob(Map<?, ?> m, List<String> warn) {
        MobSpec spec = new MobSpec();
        Object t = m.get("type");
        spec.type = en(EntityType.class, t == null ? "ZOMBIE" : String.valueOf(t), null);
        if (spec.type == null) {
            warn.add("моб: неизвестный тип " + t);
            spec.type = EntityType.ZOMBIE;
        }
        spec.amount = num(m.get("amount"), 1).intValue();
        spec.health = num(m.get("health"), 20).doubleValue();
        spec.name = m.get("name") == null ? "" : String.valueOf(m.get("name"));
        spec.keyHolder = Boolean.parseBoolean(String.valueOf(m.get("key-holder")));
        spec.glowing = Boolean.parseBoolean(String.valueOf(m.get("glowing")));
        spec.clearDrops = Boolean.parseBoolean(String.valueOf(m.get("clear-drops")));
        spec.bonusRolls = num(m.get("bonus-rolls"), 0).intValue();
        spec.wave = Math.max(1, num(m.get("wave"), 1).intValue());
        spec.radius = num(m.get("radius"), -1).intValue();
        spec.attackDamage = num(m.get("attack-damage"), -1).doubleValue();
        spec.speed = num(m.get("speed"), -1).doubleValue();
        spec.exp = num(m.get("exp"), -1).intValue();
        spec.helmet = equip(m.get("helmet"), warn);
        spec.chest = equip(m.get("chestplate"), warn);
        spec.legs = equip(m.get("leggings"), warn);
        spec.boots = equip(m.get("boots"), warn);
        spec.hand = equip(m.get("weapon"), warn);
        if (m.get("effects") instanceof List<?> el) {
            List<String> lines = new ArrayList<>();
            for (Object o : el) {
                lines.add(String.valueOf(o));
            }
            spec.effects = effects(lines, 6000, warn);
        }
        if (m.get("drops") instanceof List<?> dl) {
            for (Object o : dl) {
                DropSpec d = parseDrop(String.valueOf(o));
                if (d == null) {
                    warn.add("моб: не понял запись drops " + o);
                } else {
                    spec.drops.add(d);
                }
            }
        }
        return spec;
    }

    private static Material equip(Object o, List<String> warn) {
        if (o == null) {
            return null;
        }
        Material m = mat(String.valueOf(o));
        if (m == null) {
            warn.add("моб: неизвестный предмет экипировки " + o);
        }
        return m;
    }

    private static Number num(Object o, Number def) {
        if (o instanceof Number n) {
            return n;
        }
        try {
            return o == null ? def : Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Формат строки: ТИП:тики:уровень, например DARKNESS:100:0. */
    @SuppressWarnings("deprecation")
    private static List<PotionEffect> effects(List<String> list, int defaultTicks, List<String> warn) {
        List<PotionEffect> out = new ArrayList<>();
        for (String line : list) {
            String[] p = line.split(":");
            PotionEffectType type = PotionEffectType.getByName(p[0].trim().toUpperCase());
            if (type == null) {
                warn.add("неизвестный эффект " + p[0]);
                continue;
            }
            int ticks = defaultTicks;
            int amp = 0;
            try {
                if (p.length > 1) {
                    ticks = Integer.parseInt(p[1].trim());
                }
                if (p.length > 2) {
                    amp = Integer.parseInt(p[2].trim());
                }
            } catch (NumberFormatException ignored) {
                // значения по умолчанию
            }
            out.add(new PotionEffect(type, Math.max(ticks, 20), amp, true, false, true));
        }
        return out;
    }
}
