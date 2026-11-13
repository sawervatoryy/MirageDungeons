package org.github.sawervatoryy.mirageDungeons;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Редактор лута уровня в виде сундука.
 * Клик по предмету: ЛКМ +1 к весу, ПКМ −1, Shift+ЛКМ +10, Shift+ПКМ −10, Q — удалить.
 * Добавить предмет: Shift+клик по предмету в своём инвентаре или клик предметом с курсора по пустому слоту.
 */
public final class LootGui implements Listener {

    private static final int ENTRY_SLOTS = 45;
    private static final double[] WEIGHTS = {1, 5, 10, 20, 50, 100};

    /** Хозяин инвентаря: по нему мы узнаём наше окно. */
    public static final class Holder implements InventoryHolder {
        final int level;
        int page = 0;
        int weightIdx = 2;
        Inventory inv;

        Holder(int level) {
            this.level = level;
        }

        double weight() {
            return WEIGHTS[weightIdx];
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final MirageDungeons plugin;

    public LootGui(MirageDungeons plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, int level) {
        Holder h = new Holder(level);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.color(plugin.msg().raw("gui-title", "level", level)));
        h.inv = inv;
        render(h);
        p.openInventory(inv);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private void render(Holder h) {
        Inventory inv = h.inv;
        inv.clear();
        List<LootManager.Entry> list = plugin.loot().entries(h.level);
        double total = 0;
        for (LootManager.Entry e : list) {
            total += Math.max(0, e.weight);
        }
        for (int slot = 0; slot < ENTRY_SLOTS; slot++) {
            int idx = h.page * ENTRY_SLOTS + slot;
            if (idx >= list.size()) {
                break;
            }
            inv.setItem(slot, display(list.get(idx), idx, total));
        }
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = ENTRY_SLOTS; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }
        int pages = Math.max(1, (list.size() + ENTRY_SLOTS - 1) / ENTRY_SLOTS);
        if (h.page > 0) {
            inv.setItem(45, button(Material.ARROW, plugin.msg().raw("gui-prev"), List.of()));
        }
        if ((h.page + 1) * ENTRY_SLOTS < list.size()) {
            inv.setItem(53, button(Material.ARROW, plugin.msg().raw("gui-next"), List.of()));
        }
        inv.setItem(48, button(Material.EMERALD,
                plugin.msg().raw("gui-weight-name", "weight", fmt(h.weight())),
                plugin.msg().rawList("gui-weight-lore")));
        inv.setItem(49, button(Material.BOOK, plugin.msg().raw("gui-help-name"),
                plugin.msg().rawList("gui-help-lore", "count", list.size(), "page", h.page + 1, "pages", pages)));
        inv.setItem(50, button(Material.BARRIER, plugin.msg().raw("gui-close"), List.of()));
    }

    private ItemStack display(LootManager.Entry e, int idx, double total) {
        ItemStack it = e.item != null
                ? e.item.clone()
                : new ItemStack(e.material, Math.max(1, Math.min(e.min, e.material.getMaxStackSize())));
        String amount = e.item != null ? String.valueOf(e.item.getAmount())
                : (e.min == e.max ? String.valueOf(e.min) : e.min + "-" + e.max);
        double chance = total <= 0 ? 0 : Math.max(0, e.weight) / total * 100.0;
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        for (String line : plugin.msg().rawList("gui-entry-lore", "weight", fmt(e.weight),
                "chance", String.format("%.1f", chance), "amount", amount, "index", idx)) {
            lore.add(Msg.item(line));
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.item(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Msg.item(s));
        }
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (!p.hasPermission("miragedungeons.admin")) {
            p.closeInventory();
            return;
        }
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) {
            return;
        }
        LootManager lm = plugin.loot();
        if (clicked != top) {
            // нижний инвентарь: Shift+клик добавляет копию предмета в лут
            if (e.isShiftClick()) {
                ItemStack it = e.getCurrentItem();
                if (it != null && !it.getType().isAir()) {
                    lm.add(h.level, it, h.weight());
                    h.page = Math.max(0, (lm.entries(h.level).size() - 1) / ENTRY_SLOTS);
                    render(h);
                    plugin.msg().send(p, "gui-added", "weight", fmt(h.weight()));
                }
            }
            return;
        }
        int slot = e.getSlot();
        if (slot >= ENTRY_SLOTS) {
            switch (slot) {
                case 45 -> {
                    if (h.page > 0) {
                        h.page--;
                        render(h);
                    }
                }
                case 53 -> {
                    if ((h.page + 1) * ENTRY_SLOTS < lm.entries(h.level).size()) {
                        h.page++;
                        render(h);
                    }
                }
                case 48 -> {
                    h.weightIdx = (h.weightIdx + 1) % WEIGHTS.length;
                    render(h);
                }
                case 50 -> p.closeInventory();
                default -> { }
            }
            return;
        }
        int idx = h.page * ENTRY_SLOTS + slot;
        List<LootManager.Entry> list = lm.entries(h.level);
        if (idx >= list.size()) {
            // пустой слот: добавляем предмет с курсора
            ItemStack cur = e.getCursor();
            if (cur != null && !cur.getType().isAir()) {
                lm.add(h.level, cur, h.weight());
                h.page = Math.max(0, (lm.entries(h.level).size() - 1) / ENTRY_SLOTS);
                render(h);
                plugin.msg().send(p, "gui-added", "weight", fmt(h.weight()));
            }
            return;
        }
        LootManager.Entry en = list.get(idx);
        double delta;
        switch (e.getClick()) {
            case LEFT -> delta = 1;
            case RIGHT -> delta = -1;
            case SHIFT_LEFT -> delta = 10;
            case SHIFT_RIGHT -> delta = -10;
            case DROP, CONTROL_DROP -> {
                lm.remove(h.level, idx);
                render(h);
                plugin.msg().send(p, "gui-removed");
                return;
            }
            default -> {
                return;
            }
        }
        en.weight = Math.max(0, en.weight + delta);
        lm.save();
        render(h);
    }
}
