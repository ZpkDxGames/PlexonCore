package com.zpkdxgames.plexoncore.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GuiService implements Listener {
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiService(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, Objects.requireNonNull(plugin));
    }

    public GuiBuilder builder(String moduleId, String guiId, Component title, int rows) {
        return new GuiBuilder(moduleId, guiId, title, rows);
    }

    public Optional<GuiSession> session(UUID playerId) { return Optional.ofNullable(sessions.get(playerId)); }
    public int activeSessions() { return sessions.size(); }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof CoreGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        GuiButton button = holder.buttons.get(event.getRawSlot());
        if (button != null) button.action.accept(new GuiClick(player, event, holder.session));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CoreGuiHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof CoreGuiHolder holder)) return;
        sessions.computeIfPresent(event.getPlayer().getUniqueId(), (id, current) -> current.equals(holder.session) ? null : current);
    }

    public record GuiSession(UUID playerId, String moduleId, String guiId, int page, Instant openedAt) {}
    public record GuiClick(Player player, InventoryClickEvent event, GuiSession session) {}
    public record GuiButton(ItemStack icon, Consumer<GuiClick> action) {
        public GuiButton {
            icon = icon == null ? new ItemStack(Material.BARRIER) : icon.clone();
            action = action == null ? ignored -> {} : action;
        }
    }

    public final class GuiBuilder {
        private final String moduleId;
        private final String guiId;
        private final Component title;
        private final int size;
        private final Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        private ItemStack filler;
        private int page;

        private GuiBuilder(String moduleId, String guiId, Component title, int rows) {
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("GUI rows must be 1..6");
            this.moduleId = requireId(moduleId, "moduleId");
            this.guiId = requireId(guiId, "guiId");
            this.title = title == null ? Component.text("Plexon") : title;
            this.size = rows * 9;
        }

        public GuiBuilder page(int page) { this.page = Math.max(0, page); return this; }
        public GuiBuilder button(int slot, ItemStack icon, Consumer<GuiClick> action) {
            validateSlot(slot);
            buttons.put(slot, new GuiButton(icon, action));
            return this;
        }
        public GuiBuilder filler(ItemStack filler) { this.filler = filler == null ? null : filler.clone(); return this; }
        public GuiBuilder filler(Material material) {
            ItemStack item = new ItemStack(material == null ? Material.GRAY_STAINED_GLASS_PANE : material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
            return filler(item);
        }

        public Inventory open(Player player) {
            Objects.requireNonNull(player, "player");
            GuiSession session = new GuiSession(player.getUniqueId(), moduleId, guiId, page, Instant.now());
            CoreGuiHolder holder = new CoreGuiHolder(session, Map.copyOf(buttons));
            Inventory inventory = Bukkit.createInventory(holder, size, title);
            holder.inventory = inventory;
            if (filler != null) for (int slot = 0; slot < size; slot++) inventory.setItem(slot, filler.clone());
            buttons.forEach((slot, button) -> inventory.setItem(slot, button.icon.clone()));
            sessions.put(player.getUniqueId(), session);
            player.openInventory(inventory);
            return inventory;
        }

        private void validateSlot(int slot) {
            if (slot < 0 || slot >= size) throw new IllegalArgumentException("Slot " + slot + " outside GUI size " + size);
        }
    }

    private static String requireId(String value, String label) {
        String id = Objects.requireNonNull(value, label).trim();
        if (id.isEmpty() || id.length() > 64) throw new IllegalArgumentException(label + " must be 1..64 chars");
        return id;
    }

    private static final class CoreGuiHolder implements InventoryHolder {
        private final GuiSession session;
        private final Map<Integer, GuiButton> buttons;
        private Inventory inventory;
        private CoreGuiHolder(GuiSession session, Map<Integer, GuiButton> buttons) { this.session = session; this.buttons = buttons; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
