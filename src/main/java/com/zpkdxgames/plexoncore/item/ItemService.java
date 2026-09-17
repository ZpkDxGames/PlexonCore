package com.zpkdxgames.plexoncore.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
            case CUSTOM_ID -> {
                CustomIdentity a = customIdentity(first);
                CustomIdentity b = customIdentity(second);
                yield a.status() == IdentityStatus.RESOLVED
                        && b.status() == IdentityStatus.RESOLVED
                        && a.id().equals(b.id());
            }
        };
    }

    public String fingerprint(ItemStack item, MatchMode mode) {
        Objects.requireNonNull(item, "item");
        byte[] source = switch (mode == null ? MatchMode.EXACT : mode) {
            case MATERIAL_ONLY -> item.getType().getKey().asString().getBytes(StandardCharsets.UTF_8);
            case IGNORE_AMOUNT -> withAmount(item, 1).serializeAsBytes();
            case EXACT -> item.serializeAsBytes();
            case CUSTOM_ID -> customIdentityFingerprintSource(item);
        };
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Compatibility accessor. Conflicting identity sources now fail closed instead of picking one arbitrarily. */
    public Optional<String> customId(ItemStack item) {
        return customIdentity(item).id();
    }

    /**
     * API 2.1 identity diagnostics. Candidate precedence is deterministic (`id`, `custom_id`,
     * `item_id`, then namespace), but multiple distinct authoritative values are rejected as a
     * conflict rather than silently selecting the first PDC iteration result.
     */
    public CustomIdentity customIdentity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return CustomIdentity.none();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        List<IdentityCandidate> candidates = new ArrayList<>();
        for (NamespacedKey key : pdc.getKeys()) {
            String keyName = key.getKey().toLowerCase(Locale.ROOT);
            int precedence = precedence(keyName);
            if (precedence < 0) continue;
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value == null || value.isBlank()) continue;
            candidates.add(new IdentityCandidate(precedence, key.getNamespace().toLowerCase(Locale.ROOT), keyName, value.trim()));
        }
        return resolveIdentityCandidates(candidates);
    }

    static CustomIdentity resolveIdentityCandidates(List<IdentityCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return CustomIdentity.none();
        List<IdentityCandidate> ordered = candidates.stream()
                .sorted(Comparator.comparingInt(IdentityCandidate::precedence)
                        .thenComparing(IdentityCandidate::namespace)
                        .thenComparing(IdentityCandidate::keyName)
                        .thenComparing(IdentityCandidate::value))
                .toList();
        Set<String> identities = new LinkedHashSet<>();
        List<String> sources = new ArrayList<>();
        for (IdentityCandidate candidate : ordered) {
            String identity = candidate.namespace() + ":" + candidate.value();
            identities.add(identity);
            sources.add(candidate.namespace() + ":" + candidate.keyName() + "=" + candidate.value());
        }
        if (identities.size() > 1) return new CustomIdentity(IdentityStatus.CONFLICT, Optional.empty(), List.copyOf(sources));
        return new CustomIdentity(IdentityStatus.RESOLVED, Optional.of(identities.iterator().next()), List.copyOf(sources));
    }

    private byte[] customIdentityFingerprintSource(ItemStack item) {
        CustomIdentity identity = customIdentity(item);
        if (identity.status() == IdentityStatus.RESOLVED) {
            return ("resolved|" + identity.id().orElseThrow()).getBytes(StandardCharsets.UTF_8);
        }
        byte[] exact = item.serializeAsBytes();
        byte[] prefix = (identity.status().name().toLowerCase(Locale.ROOT) + "|").getBytes(StandardCharsets.UTF_8);
        byte[] combined = Arrays.copyOf(prefix, prefix.length + exact.length);
        System.arraycopy(exact, 0, combined, prefix.length, exact.length);
        return combined;
    }

    private static int precedence(String keyName) {
        return switch (keyName) {
            case "id" -> 0;
            case "custom_id" -> 1;
            case "item_id" -> 2;
            default -> -1;
        };
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
    public enum IdentityStatus { NONE, RESOLVED, CONFLICT }

    static record IdentityCandidate(int precedence, String namespace, String keyName, String value) {
        IdentityCandidate {
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(keyName);
            Objects.requireNonNull(value);
        }
    }

    public record CustomIdentity(IdentityStatus status, Optional<String> id, List<String> sources) {
        public CustomIdentity {
            status = status == null ? IdentityStatus.NONE : status;
            id = id == null ? Optional.empty() : id;
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
        static CustomIdentity none() { return new CustomIdentity(IdentityStatus.NONE, Optional.empty(), List.of()); }
    }

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
