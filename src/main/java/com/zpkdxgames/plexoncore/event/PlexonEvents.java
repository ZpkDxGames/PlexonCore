package com.zpkdxgames.plexoncore.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class PlexonEvents {
    private PlexonEvents() {}
    public interface PlexonTransactionalEvent { String transactionId(); }
    public interface PlexonSourceEvent { String sourceModule(); }

    public static String normalizeToken(String sourceModule, String token) {
        String source = sanitize(sourceModule);
        String value = Objects.requireNonNullElse(token, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Event token cannot be empty");
        return source + ":" + value;
    }

    public static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static final class MemoryDedupe {
        private final long ttlNanos;
        private final Map<String, Long> seen = new ConcurrentHashMap<>();
        public MemoryDedupe(Duration ttl) { this.ttlNanos = Math.max(Duration.ofSeconds(1).toNanos(), Objects.requireNonNull(ttl).toNanos()); }
        public boolean first(String token) {
            long now = System.nanoTime();
            String hash = hashToken(token);
            seen.entrySet().removeIf(entry -> now - entry.getValue() > ttlNanos);
            return seen.putIfAbsent(hash, now) == null;
        }
        public int size() { return seen.size(); }
    }

    private static String sanitize(String source) {
        String normalized = Objects.requireNonNull(source).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (normalized.isBlank()) throw new IllegalArgumentException("Source module cannot be empty");
        return normalized;
    }
}
