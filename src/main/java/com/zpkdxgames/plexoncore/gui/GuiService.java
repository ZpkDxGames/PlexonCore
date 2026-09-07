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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GuiService implements Listener {
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiService(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, Objects.requireNonNull(plugin));
    }

    public GuiBuilder builder(String moduleId, String guiId, Component title, int rows) {
        return new GuiBuilder(moduleId, guiId, title, rows);
    }

    public <T> PaginatedGui<T> paginated(String moduleId, String guiId, Component title, int rows,
                                          List<T> entries, Function<T, ItemStack> icon,
                                          BiConsumer<GuiClick, T> action) {
        return new PaginatedGui<>(moduleId, guiId, title, rows, entries, icon, action);
    }

    public Inventory confirmation(Player player, String moduleId, String guiId, Component title,
                                  ItemStack subject, Consumer<Player> confirm, Consumer<Player> cancel) {
        Objects.requireNonNull(player, "player");
        Consumer<Player> safeConfirm = confirm == null ? ignored -> {} : confirm;
        Consumer<Player> safeCancel = cancel == null ? Player::closeInventory : cancel;
        GuiBuilder builder = builder(moduleId, guiId, title, 3).filler(Material.GRAY_STAINED_GLASS_PANE);
        if (subject != null) builder.button(13, subject, click -> {});
        builder.button(11, controlItem(Material.LIME_CONCRETE, Component.text("Confirm")), click -> safeConfirm.accept(click.player()));
        builder.button(15, controlItem(Material.RED_CONCRETE, Component.text("Cancel")), click -> safeCancel.accept(click.player()));
        return builder.open(player);
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

    public final class PaginatedGui<T> {
        private final String moduleId;
        private final String guiId;
        private final Component title;
        private final int rows;
        private final List<T> entries;
        private final Function<T, ItemStack> icon;
        private final BiConsumer<GuiClick, T> action;

        private PaginatedGui(String moduleId, String guiId, Component title, int rows, List<T> entries,
                             Function<T, ItemStack> icon, BiConsumer<GuiClick, T> action) {
            if (rows < 2 || rows > 6) throw new IllegalArgumentException("Paginated GUI rows must be 2..6");
            this.moduleId = requireId(moduleId, "moduleId");
            this.guiId = requireId(guiId, "guiId");
            this.title = title == null ? Component.text("Plexon") : title;
            this.rows = rows;
            this.entries = List.copyOf(entries == null ? List.of() : entries);
            this.icon = Objects.requireNonNull(icon, "icon");
            this.action = action == null ? (click, entry) -> {} : action;
        }

        public int pageSize() { return (rows - 1) * 9; }
        public int pageCount() { return Math.max(1, (entries.size() + pageSize() - 1) / pageSize()); }

        public Inventory open(Player player, int requestedPage) {
            int page = Math.max(0, Math.min(requestedPage, pageCount() - 1));
            int start = page * pageSize();
            int end = Math.min(entries.size(), start + pageSize());
            int size = rows * 9;
            GuiBuilder builder = builder(moduleId, guiId, title, rows).page(page).filler(Material.GRAY_STAINED_GLASS_PANE);
            for (int index = start; index < end; index++) {
                T entry = entries.get(index);
                int slot = index - start;
                builder.button(slot, icon.apply(entry), click -> action.accept(click, entry));
            }
            if (page > 0) {
                builder.button(size - 9, controlItem(Material.ARROW, Component.text("Previous Page")), click -> open(click.player(), page - 1));
            }
            builder.button(size - 5, controlItem(Material.BARRIER, Component.text("Close")), click -> click.player().closeInventory());
            if (page + 1 < pageCount()) {
                builder.button(size - 1, controlItem(Material.ARROW, Component.text("Next Page")), click -> open(click.player(), page + 1));
            }
            return builder.open(player);
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

    private static ItemStack controlItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
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
