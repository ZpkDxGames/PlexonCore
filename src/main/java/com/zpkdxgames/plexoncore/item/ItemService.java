package com.zpkdxgames.plexoncore.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ItemService {
    public ItemSnapshot snapshot(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return new ItemSnapshot(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
    }

    public boolean matches(ItemStack first, ItemStack second, MatchMode mode) {
        if (first == null || second == null) return first == second;
        MatchMode effective = mode == null ? MatchMode.EXACT : mode;
        return switch (effective) {
            case MATERIAL_ONLY -> first.getType() == second.getType();
            case IGNORE_AMOUNT -> sameBytes(withAmount(first, 1), withAmount(second, 1));
            case EXACT -> sameBytes(first, second);
            case CUSTOM_ID -> customId(first).isPresent() && customId(first).equals(customId(second));
        };
    }

    public String fingerprint(ItemStack item, MatchMode mode) {
        Objects.requireNonNull(item, "item");
        byte[] source = switch (mode == null ? MatchMode.EXACT : mode) {
            case MATERIAL_ONLY -> item.getType().getKey().asString().getBytes(StandardCharsets.UTF_8);
            case IGNORE_AMOUNT -> withAmount(item, 1).serializeAsBytes();
            case EXACT -> item.serializeAsBytes();
            case CUSTOM_ID -> customId(item).orElse("").getBytes(StandardCharsets.UTF_8);
        };
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public Optional<String> customId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            String lower = key.getKey().toLowerCase(Locale.ROOT);
            if (!lower.equals("id") && !lower.equals("custom_id") && !lower.equals("item_id")) continue;
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value != null && !value.isBlank()) return Optional.of(key.getNamespace() + ":" + value);
        }
        return Optional.empty();
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        ItemStack clone = item.clone();
        clone.setAmount(amount);
        return clone;
    }

    private static boolean sameBytes(ItemStack a, ItemStack b) {
        return Arrays.equals(a.serializeAsBytes(), b.serializeAsBytes());
    }

    public enum MatchMode { EXACT, IGNORE_AMOUNT, MATERIAL_ONLY, CUSTOM_ID }

    public record ItemSnapshot(String base64Nbt) {
        public ItemSnapshot {
            Objects.requireNonNull(base64Nbt, "base64Nbt");
            if (base64Nbt.length() > 16_000_000) throw new IllegalArgumentException("Item snapshot exceeds safety limit");
        }
        public ItemStack restore() { return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64Nbt)); }
        public Material material() { return restore().getType(); }
        public int amount() { return restore().getAmount(); }
    }
}
