package com.zpkdxgames.plexoncore.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextService {
    private static final Pattern TAG_PATTERN = Pattern.compile("<(/?)([#A-Za-z0-9_:-]+)(?:[^>]*)>");
    private static final Pattern HEX_AMP = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Set<String> ALLOWED_TAGS = Set.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray",
        "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
        "bold", "b", "italic", "i", "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "reset",
        "color", "colour", "gradient", "rainbow"
    );

    private final PluginManager pluginManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final MiniMessage strictMiniMessage = MiniMessage.builder().strict(true).build();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();

    public TextService(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager);
    }

    public Component render(TextMode mode, String input) {
        String safe = input == null ? "" : input;
        return switch (mode == null ? TextMode.SAFE : mode) {
            case SAFE -> Component.text(safe);
            case LEGACY -> legacy.deserialize(normalizeAmpHex(safe));
            case MINIMESSAGE -> miniMessage.deserialize(safe);
        };
    }

    public Component renderTemplate(String trustedTemplate, Map<String, ?> runtimeValues) {
        ArrayList<TagResolver> resolvers = new ArrayList<>();
        if (runtimeValues != null) {
            runtimeValues.forEach((key, value) -> {
                String normalized = normalizeResolverName(key);
                resolvers.add(TagResolver.resolver(normalized, Tag.inserting(Component.text(String.valueOf(value)))));
            });
        }
        return miniMessage.deserialize(trustedTemplate == null ? "" : trustedTemplate,
            TagResolver.resolver(resolvers.toArray(TagResolver[]::new)));
    }

    public Component renderPlaceholderSafe(Player player, String input) {
        return Component.text(applyPlaceholderApi(player, input));
    }

    public Component renderPlaceholderLegacy(Player player, String input) {
        return legacy.deserialize(normalizeAmpHex(applyPlaceholderApi(player, input)));
    }

    public Component renderPlaceholderMiniMessage(Player player, String input) {
        String sanitized = sanitizeMiniMessagePlaceholderOutput(applyPlaceholderApi(player, input));
        return miniMessage.deserialize(sanitized);
    }

    public ValidationResult validateMiniMessage(String template) {
        try {
            strictMiniMessage.deserialize(template == null ? "" : template);
            return new ValidationResult(true, "", -1);
        } catch (Exception ex) {
            return new ValidationResult(false, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), -1);
        }
    }

    public String sanitizeMiniMessagePlaceholderOutput(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher matcher = TAG_PATTERN.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(2);
            String base = token.startsWith("#") ? "#" : token.split(":", 2)[0].toLowerCase(Locale.ROOT);
            boolean allowed = base.equals("#") && token.matches("#[0-9A-Fa-f]{6}") || ALLOWED_TAGS.contains(base);
            matcher.appendReplacement(out, Matcher.quoteReplacement(allowed ? matcher.group() : ""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String applyPlaceholderApi(OfflinePlayer player, String input) {
        String source = input == null ? "" : input;
        if (pluginManager.getPlugin("PlaceholderAPI") == null) return source;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            Object value = method.invoke(null, player, source);
            return value instanceof String string ? string : source;
        } catch (ReflectiveOperationException ex) {
            return source;
        }
    }

    private static String normalizeAmpHex(String input) {
        Matcher matcher = HEX_AMP.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(out, "&#" + matcher.group(1));
        matcher.appendTail(out);
        return out.toString();
    }

    private static String normalizeResolverName(String key) {
        String normalized = Objects.requireNonNull(key).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (normalized.isBlank()) throw new IllegalArgumentException("Empty placeholder resolver name");
        return normalized;
    }

    public enum TextMode { SAFE, LEGACY, MINIMESSAGE }
    public record ValidationResult(boolean valid, String reason, int position) {}
}
