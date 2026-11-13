package org.github.sawervatoryy.mirageDungeons;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class DungeonListener implements Listener {

    private static final String BYPASS = "miragedungeons.bypass";

    private final MirageDungeons plugin;
    private final DungeonManager mgr;
    private final Random rnd = new Random();

    public DungeonListener(MirageDungeons plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
    }

    // ------------------------------------------------------------------ блоки

    // LOWEST: блок привата должен открываться раньше, чем WorldGuard запретит ломать блоки в регионе
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Dungeon d = mgr.at(b);
        if (d == null) {
            return;
        }
        Player p = e.getPlayer();
        if (d.state == Dungeon.State.PASTING) {
            e.setCancelled(true);
            return;
        }
        if (d.isGuard(b) && d.state == Dungeon.State.ACTIVE) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            mgr.open(d, p);
            return;
        }
        if (p.hasPermission(BYPASS)) {
            return;
        }
        if (d.isGuard(b) || d.chestAt(b) != null) {
            e.setCancelled(true);
            plugin.msg().send(p, "chest-protected");
            return;
        }
        LevelConfig lc = mgr.level(d.level);
        boolean wg = lc != null && lc.wgMode;
        if ((!wg && !mgr.canModify(d, "block-break")) || !mgr.materialAllowed(d, false, b.getType())) {
            e.setCancelled(true);
            plugin.msg().send(p, "protected");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Dungeon d = mgr.at(e.getBlock());
        if (d == null || e.getPlayer().hasPermission(BYPASS)) {
            return;
        }
        LevelConfig lc = mgr.level(d.level);
        if (lc != null && lc.antiGrief(d) && lc.agPlace.contains(e.getBlockPlaced().getType())) {
            e.setCancelled(true);
            plugin.msg().send(e.getPlayer(), "antigrief-denied");
            return;
        }
        boolean wg = lc != null && lc.wgMode;
        if (d.state == Dungeon.State.PASTING || (!wg && !mgr.canModify(d, "block-place"))
                || !mgr.materialAllowed(d, true, e.getBlockPlaced().getType())) {
            e.setCancelled(true);
            plugin.msg().send(e.getPlayer(), "protected");
        }
    }

    // ------------------------------------------------------------------ PvP и команды

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (e.getDamager() instanceof Player pl) {
            attacker = pl;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2) {
            attacker = pl2;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        Dungeon d = mgr.at(victim.getLocation());
        if (d == null) {
            return;
        }
        LevelConfig l = mgr.level(d.level);
        if (l == null || l.pvp || l.wgMode) {
            return;
        }
        e.setCancelled(true);
        plugin.msg().send(attacker, "pvp-denied");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(BYPASS)) {
            return;
        }
        Dungeon d = mgr.at(p.getLocation());
        if (d == null) {
            return;
        }
        LevelConfig l = mgr.level(d.level);
        if (l == null || l.blockedCommands.isEmpty()) {
            return;
        }
        String text = e.getMessage().toLowerCase();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        String first = text.split(" ")[0];
        int colon = first.indexOf(':');
        if (colon >= 0) {
            first = first.substring(colon + 1);
        }
        if (l.blockedCommands.contains(first)) {
            e.setCancelled(true);
            plugin.msg().send(p, "command-blocked");
        }
    }

    // ------------------------------------------------------------------ взаимодействие

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        if (a == Action.PHYSICAL) {
            Block pl = e.getClickedBlock();
            if (pl != null && Tag.PRESSURE_PLATES.isTagged(pl.getType())) {
                Dungeon d = mgr.at(pl);
                if (d != null) {
                    mgr.trap(d, p);
                }
            }
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK)
                && plugin.items().isCompass(e.getItem())) {
            e.setCancelled(true);
            useCompass(p);
            return;
        }
        if (a != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Dungeon d = mgr.at(b);
        if (d == null) {
            return;
        }
        Dungeon.ChestInfo info = d.chestAt(b);
        if (info == null) {
            return;
        }
        if (d.state == Dungeon.State.PASTING || d.state == Dungeon.State.ACTIVE) {
            e.setCancelled(true);
            plugin.msg().send(p, "chest-closed");
            return;
        }
        if (d.state == Dungeon.State.COUNTDOWN) {
            e.setCancelled(true);
            long sec = Math.max(0, (d.openAt - System.currentTimeMillis()) / 1000);
            plugin.msg().send(p, "chest-countdown", "time", sec);
            return;
        }
        if (e.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            return;     // открытие уже запретил другой плагин: эффекты и отметку не ставим
        }
        if (info.mimic && !info.opened) {
            e.setCancelled(true);
            mgr.markOpened(d, info, p);
            mgr.spawnMimic(d, b, p);
            return;
        }
        if (info.locked) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (plugin.items().keyLevel(hand) != d.level) {
                e.setCancelled(true);
                plugin.msg().send(p, "chest-locked");
                return;
            }
            if (hand.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                hand.setAmount(hand.getAmount() - 1);
                p.getInventory().setItemInMainHand(hand);
            }
            info.locked = false;
            plugin.msg().send(p, "chest-unlocked");
            mgr.fire("unlock", d, p, b.getLocation().add(0.5, 1, 0.5));
            mgr.save();
        }
        if (!info.opened) {
            mgr.fire("chest-open", d, p, b.getLocation().add(0.5, 1, 0.5));
            mgr.markOpened(d, info, p);
        }
    }

    private void useCompass(Player p) {
        FileConfiguration c = plugin.getConfig();
        long now = System.currentTimeMillis();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Long until = pdc.get(plugin.items().cooldown, PersistentDataType.LONG);
        if (until != null && until > now && !p.hasPermission(BYPASS)) {
            long sec = (until - now) / 1000;
            plugin.msg().send(p, "compass-cooldown",
                    "time", String.format("%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60));
            return;
        }
        List<Integer> only = c.getIntegerList("compass.levels");
        List<Dungeon> list = new ArrayList<>();
        for (Dungeon d : mgr.activeUnopened(p.getWorld())) {
            if (only.isEmpty() || only.contains(d.level)) {
                list.add(d);
            }
        }
        if (list.isEmpty()) {
            plugin.msg().send(p, "compass-none");
            return;
        }
        Dungeon pick;
        if ("nearest".equalsIgnoreCase(c.getString("compass.mode", "random"))) {
            pick = list.get(0);
            double best = Double.MAX_VALUE;
            for (Dungeon d : list) {
                double dx = d.guardX - p.getLocation().getX();
                double dz = d.guardZ - p.getLocation().getZ();
                double dist = dx * dx + dz * dz;
                if (dist < best) {
                    best = dist;
                    pick = d;
                }
            }
        } else {
            pick = list.get(rnd.nextInt(list.size()));
        }
        LevelConfig lvl = mgr.level(pick.level);
        int blur = lvl == null ? 0 : lvl.compassBlur;
        int x = pick.guardX + (blur > 0 ? rnd.nextInt(blur * 2 + 1) - blur : 0);
        int z = pick.guardZ + (blur > 0 ? rnd.nextInt(blur * 2 + 1) - blur : 0);
        if (c.getBoolean("compass.point-needle", true)) {
            p.setCompassTarget(new Location(p.getWorld(), x, pick.guardY, z));
        }
        plugin.msg().send(p, "compass-found", "level", lvl == null ? "" : lvl.name,
                "x", x, "y", pick.guardY, "z", z);
        World w = p.getWorld();
        plugin.events().sound(c.getString("compass.sound", ""), "player", w, p.getLocation(), p);
        if (c.getBoolean("compass.consume", false)) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                hand.setAmount(hand.getAmount() - 1);
                p.getInventory().setItemInMainHand(hand);
            }
        }
        long cd = c.getLong("compass.cooldown-seconds", 7200);
        pdc.set(plugin.items().cooldown, PersistentDataType.LONG, now + cd * 1000L);
    }

    // ------------------------------------------------------------------ защита от лавакастов

    private LevelConfig antiGriefLevel(Block b) {
        if (mgr.dungeons().isEmpty()) {
            return null;
        }
        Dungeon d = mgr.at(b);
        if (d == null) {
            return null;
        }
        LevelConfig l = mgr.level(d.level);
        return l != null && l.antiGrief(d) ? l : null;
    }

    /** Нельзя выливать лаву и воду из ведра в регионе. */
    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (e.getPlayer().hasPermission(BYPASS)) {
            return;
        }
        LevelConfig l = antiGriefLevel(e.getBlock());
        if (l != null && l.agBuckets.contains(e.getBucket())) {
            e.setCancelled(true);
            plugin.msg().send(e.getPlayer(), "antigrief-denied");
        }
    }

    /** Жидкости не затекают в регион и не текут внутри него. */
    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent e) {
        if (!e.getBlock().isLiquid()) {
            return;
        }
        LevelConfig l = antiGriefLevel(e.getToBlock());
        if (l != null && l.agFluidFlow) {
            e.setCancelled(true);
        }
    }

    /** Обсидиан и булыжник не образуются от встречи лавы и воды. */
    @EventHandler(ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        LevelConfig l = antiGriefLevel(e.getBlock());
        if (l != null && l.agGeneration.contains(e.getNewState().getType())) {
            e.setCancelled(true);
        }
    }

    /** Раздатчики не выливают лаву и воду в регион. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        Block disp = e.getBlock();
        Block target = disp;
        if (disp.getBlockData() instanceof Directional dir) {
            target = disp.getRelative(dir.getFacing());
        }
        LevelConfig l = antiGriefLevel(target);
        if (l == null) {
            l = antiGriefLevel(disp);
        }
        if (l != null && l.agBuckets.contains(e.getItem().getType())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ взрывы

    // LOWEST: флаг tnt deny у WorldGuard отменяет взрыв, а блок привата при этом должен открыться
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleExplosion(e.blockList(), e.getEntity(), e.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleExplosion(e.blockList(), null, e.getBlock().getLocation());
    }

    private void handleExplosion(List<Block> blocks, Entity source, Location at) {
        Set<Dungeon> toOpen = new LinkedHashSet<>();
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Dungeon d = mgr.at(b);
            if (d == null) {
                continue;
            }
            LevelConfig l = mgr.level(d.level);
            boolean opens = l != null && l.raw.getBoolean("explosion-opens-guard", true);
            if (d.isGuard(b)) {
                it.remove();
                if (opens && d.state == Dungeon.State.ACTIVE) {
                    toOpen.add(d);
                }
            } else if (d.chestAt(b) != null || (l != null && !l.wgMode && !mgr.canModify(d, "tnt"))) {
                it.remove();
            }
        }
        // Взрыв рядом с блоком привата открывает данж, даже если блок слишком прочный (плачущий обсидиан).
        if (at != null && at.getWorld() != null) {
            for (Dungeon d : new ArrayList<>(mgr.dungeons().values())) {
                if (d.state != Dungeon.State.ACTIVE || !d.world.equals(at.getWorld().getName())) {
                    continue;
                }
                LevelConfig l = mgr.level(d.level);
                if (l == null || !l.raw.getBoolean("explosion-opens-guard", true)) {
                    continue;
                }
                double radius = l.raw.getDouble("explosion-trigger-radius", 0.0);
                if (radius > 0 && mgr.guardLocation(d).distanceSquared(at) <= radius * radius) {
                    toOpen.add(d);
                }
            }
        }
        Player who = null;
        if (source instanceof TNTPrimed t && t.getSource() instanceof Player pl) {
            who = pl;
        }
        for (Dungeon d : toOpen) {
            mgr.open(d, who);
        }
    }

    // ------------------------------------------------------------------ смерть мобов

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        PersistentDataContainer pdc = le.getPersistentDataContainer();
        String id = pdc.get(plugin.items().dungeon, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Dungeon d = mgr.dungeons().get(id);
        if (d == null) {
            return;
        }
        if (pdc.has(plugin.items().clearDrops, PersistentDataType.BYTE)) {
            e.getDrops().clear();
        }
        Integer exp = pdc.get(plugin.items().exp, PersistentDataType.INTEGER);
        if (exp != null) {
            e.setDroppedExp(exp);
        }
        String drops = pdc.get(plugin.items().drops, PersistentDataType.STRING);
        for (LevelConfig.DropSpec ds : LevelConfig.dropsFromString(drops)) {
            if (rnd.nextDouble() * 100 < ds.chance) {
                int amount = ds.min + rnd.nextInt(ds.max - ds.min + 1);
                e.getDrops().add(new ItemStack(ds.material, Math.max(1, Math.min(amount, ds.material.getMaxStackSize()))));
            }
        }
        Integer keys = pdc.get(plugin.items().keyholder, PersistentDataType.INTEGER);
        if (keys != null && keys > 0) {
            e.getDrops().add(plugin.items().createKey(d.level, keys));
        }
        Integer boss = pdc.get(plugin.items().boss, PersistentDataType.INTEGER);
        if (boss != null) {
            for (int i = 0; i < boss; i++) {
                ItemStack it = plugin.loot().roll(d.level);
                if (it != null) {
                    e.getDrops().add(it);
                }
            }
            mgr.bossDied(d);
            mgr.fire("boss-death", d, le.getKiller(), le.getLocation());
        }
        Integer mimic = pdc.get(plugin.items().mimic, PersistentDataType.INTEGER);
        if (mimic != null) {
            for (int i = 0; i < mimic; i++) {
                ItemStack it = plugin.loot().roll(d.level);
                if (it != null) {
                    e.getDrops().add(it);
                }
            }
        }
    }
}
