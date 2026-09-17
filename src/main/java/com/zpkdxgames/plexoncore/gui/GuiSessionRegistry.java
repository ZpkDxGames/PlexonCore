package com.zpkdxgames.plexoncore.gui;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle authority for active Core GUI sessions. Keeping session identity separate from Bukkit
 * inventory routing makes stale-session rejection and owner cleanup deterministic and testable.
 */
final class GuiSessionRegistry {
    record Entry(GuiService.GuiSession session, Plugin owner, long generation) {
        Entry {
            Objects.requireNonNull(session, "session");
        }
    }

    private final ConcurrentHashMap<UUID, Entry> sessions = new ConcurrentHashMap<>();

    void put(UUID playerId, GuiService.GuiSession session, Plugin owner, long generation) {
        sessions.put(Objects.requireNonNull(playerId, "playerId"), new Entry(session, owner, generation));
    }

    Optional<GuiService.GuiSession> session(UUID playerId) {
        Entry state = sessions.get(playerId);
        return state == null ? Optional.empty() : Optional.of(state.session());
    }

    Entry get(UUID playerId) {
        return sessions.get(playerId);
    }

    boolean current(UUID playerId, GuiService.GuiSession session, Plugin owner, long generation) {
        Entry state = sessions.get(playerId);
        return state != null
                && state.generation() == generation
                && state.session().equals(session)
                && state.owner() == owner;
    }

    boolean removeIfCurrent(UUID playerId, GuiService.GuiSession session, Plugin owner, long generation) {
        Entry state = sessions.get(playerId);
        return state != null
                && state.generation() == generation
                && state.session().equals(session)
                && state.owner() == owner
                && sessions.remove(playerId, state);
    }

    void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    List<UUID> purgeOwner(Plugin owner) {
        if (owner == null) return List.of();
        List<UUID> removed = new ArrayList<>();
        sessions.forEach((playerId, state) -> {
            if (state.owner() == owner && sessions.remove(playerId, state)) removed.add(playerId);
        });
        return List.copyOf(removed);
    }

    int size() {
        return sessions.size();
    }

    void clear() {
        sessions.clear();
    }
}
