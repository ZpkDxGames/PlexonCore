package me.clip.placeholderapi;

import org.bukkit.OfflinePlayer;

/** Test-scope stand-in used to prove TextService caches one provider method per lifecycle. */
public final class PlaceholderAPI {
    private PlaceholderAPI() {}

    public static String setPlaceholders(OfflinePlayer player, String input) {
        if ("__FAIL__".equals(input)) throw new IllegalStateException("synthetic provider failure");
        return "resolved:" + input;
    }
}
