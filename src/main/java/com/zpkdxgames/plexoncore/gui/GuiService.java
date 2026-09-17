package com.zpkdxgames.plexoncore.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GuiService implements Listener, AutoCloseable {
    private static final Set<ClickType> ACTION_CLICKS = EnumSet.of(ClickType.LEFT, ClickType.RIGHT);

    private final Plugin corePlugin;
    private final GuiSessionRegistry sessions = new GuiSessionRegistry();
    private final AtomicLong generations = new AtomicLong();

    public GuiService(Plugin plugin) {
        this.corePlugin = Objects.requireNonNull(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Legacy API 2.0 ownerless builder. New integrations should use the owner-aware overload. */
    public GuiBuilder builder(String moduleId, String guiId, Component title, int rows) {
        return new GuiBuilder(null, moduleId, guiId, title, rows);
    }

    /** API 2.1 owner-aware builder. */
    public GuiBuilder builder(Plugin owner, String moduleId, String guiId, Component title, int rows) {
        Objects.requireNonNull(owner, "owner");
        if (!owner.isEnabled()) throw new IllegalStateException("Cannot open GUI for disabled plugin " + owner.getName());
        return new GuiBuilder(owner, moduleId, guiId, title, rows);
    }

    public <T> PaginatedGui<T> paginated(String moduleId, String guiId, Component title, int rows,
                                          List<T> entries, Function<T, ItemStack> icon,
                                          BiConsumer<GuiClick, T> action) {
        return new PaginatedGui<>(null, moduleId, guiId, title, rows, entries, icon, action);
    }

    public <T> PaginatedGui<T> paginated(Plugin owner, String moduleId, String guiId, Component title, int rows,
                                          List<T> entries, Function<T, ItemStack> icon,
                                          BiConsumer<GuiClick, T> action) {
        Objects.requireNonNull(owner, "owner");
        return new PaginatedGui<>(owner, moduleId, guiId, title, rows, entries, icon, action);
    }

    public Inventory confirmation(Player player, String moduleId, String guiId, Component title,
                                  ItemStack subject, Consumer<Player> confirm, Consumer<Player> cancel) {
        return confirmation(null, player, moduleId, guiId, title, subject, confirm, cancel);
    }

    public Inventory confirmation(Plugin owner, Player player, String moduleId, String guiId, Component title,
                                  ItemStack subject, Consumer<Player> confirm, Consumer<Player> cancel) {
        Objects.requireNonNull(player, "player");
        Consumer<Player> safeConfirm = confirm == null ? ignored -> {} : confirm;
        Consumer<Player> safeCancel = cancel == null ? Player::closeInventory : cancel;
        GuiBuilder builder = owner == null ? builder(moduleId, guiId, title, 3) : builder(owner, moduleId, guiId, title, 3);
        builder.filler(Material.GRAY_STAINED_GLASS_PANE);
        if (subject != null) builder.button(13, subject, click -> {});
        builder.button(11, controlItem(Material.LIME_CONCRETE, Component.text("Confirm")), click -> safeConfirm.accept(click.player()));
        builder.button(15, controlItem(Material.RED_CONCRETE, Component.text("Cancel")), click -> safeCancel.accept(click.player()));
        return builder.open(player);
    }

    public Optional<GuiSession> session(UUID playerId) {
        return sessions.session(playerId);
    }

    public int activeSessions() { return sessions.size(); }

    /** Removes only active sessions/callback routes owned by the exact plugin instance. */
    public int purgeOwner(Plugin owner) {
        List<UUID> removed = sessions.purgeOwner(owner);
        for (UUID playerId : removed) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder(false) instanceof CoreGuiHolder holder
                    && holder.owner == owner) {
                player.closeInventory();
            }
        }
        return removed.size();
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof CoreGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ACTION_CLICKS.contains(event.getClick())) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!current(holder, player.getUniqueId())) return;
        if (holder.owner != null && !holder.owner.isEnabled()) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        GuiButton button = holder.buttons.get(event.getRawSlot());
        if (button == null) return;
        // Revalidation immediately precedes mutation. A stale holder never becomes authoritative.
        if (!current(holder, player.getUniqueId())) return;
        button.action.accept(new GuiClick(player, event, holder.session));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CoreGuiHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof CoreGuiHolder holder)) return;
        sessions.removeIfCurrent(event.getPlayer().getUniqueId(), holder.session, holder.owner, holder.generation);
    }

    private boolean current(CoreGuiHolder holder, UUID playerId) {
        return sessions.current(playerId, holder.session, holder.owner, holder.generation);
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
        private final Plugin owner;
        private final String moduleId;
        private final String guiId;
        private final Component title;
        private final int rows;
        private final List<T> entries;
        private final Function<T, ItemStack> icon;
        private final BiConsumer<GuiClick, T> action;

        private PaginatedGui(Plugin owner, String moduleId, String guiId, Component title, int rows, List<T> entries,
                             Function<T, ItemStack> icon, BiConsumer<GuiClick, T> action) {
            if (rows < 2 || rows > 6) throw new IllegalArgumentException("Paginated GUI rows must be 2..6");
            this.owner = owner;
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
            GuiBuilder builder = owner == null
                    ? GuiService.this.builder(moduleId, guiId, title, rows)
                    : GuiService.this.builder(owner, moduleId, guiId, title, rows);
            builder.page(page).filler(Material.GRAY_STAINED_GLASS_PANE);
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
        private final Plugin owner;
        private final String moduleId;
        private final String guiId;
        private final Component title;
        private final int size;
        private final Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        private ItemStack filler;
        private int page;

        private GuiBuilder(Plugin owner, String moduleId, String guiId, Component title, int rows) {
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("GUI rows must be 1..6");
            this.owner = owner;
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

        /** Defers a semantic action by one tick and revalidates owner/session before execution. */
        public GuiBuilder buttonDeferred(int slot, ItemStack icon, Consumer<GuiClick> action) {
            validateSlot(slot);
            Consumer<GuiClick> safeAction = action == null ? ignored -> {} : action;
            buttons.put(slot, new GuiButton(icon, click -> {
                GuiSession expected = click.session();
                Bukkit.getScheduler().runTask(corePlugin, () -> {
                    GuiSessionRegistry.Entry current = sessions.get(click.player().getUniqueId());
                    if (current == null || !current.session().equals(expected)) return;
                    if (current.owner() != null && !current.owner().isEnabled()) return;
                    safeAction.accept(click);
                });
            }));
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
            if (owner != null && !owner.isEnabled()) throw new IllegalStateException("Cannot open GUI for disabled plugin " + owner.getName());
            GuiSession session = new GuiSession(player.getUniqueId(), moduleId, guiId, page, Instant.now());
            long generation = generations.incrementAndGet();
            CoreGuiHolder holder = new CoreGuiHolder(session, owner, generation, Map.copyOf(buttons));
            Inventory inventory = Bukkit.createInventory(holder, size, title);
            holder.inventory = inventory;
            if (filler != null) for (int slot = 0; slot < size; slot++) inventory.setItem(slot, filler.clone());
            buttons.forEach((slot, button) -> inventory.setItem(slot, button.icon.clone()));
            sessions.put(player.getUniqueId(), session, owner, generation);
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

    @Override
    public void close() {
        sessions.clear();
    }

    private static final class CoreGuiHolder implements InventoryHolder {
        private final GuiSession session;
        private final Plugin owner;
        private final long generation;
        private final Map<Integer, GuiButton> buttons;
        private Inventory inventory;
        private CoreGuiHolder(GuiSession session, Plugin owner, long generation, Map<Integer, GuiButton> buttons) {
            this.session = session;
            this.owner = owner;
            this.generation = generation;
            this.buttons = buttons;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
