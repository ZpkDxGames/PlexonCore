package com.zpkdxgames.plexoncore.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

    /**
     * Bounded insertion-ordered TTL cache. Expiry and size eviction only walk the oldest entries,
     * making normal membership/update work O(1) with amortized O(1) cleanup instead of scanning the
     * full cache on every event.
     */
    public static final class MemoryDedupe {
        private static final int DEFAULT_MAX_ENTRIES = 4096;
        private final long ttlNanos;
        private final int maxEntries;
        private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

        public MemoryDedupe(Duration ttl) {
            this(ttl, DEFAULT_MAX_ENTRIES);
        }

        public MemoryDedupe(Duration ttl, int maxEntries) {
            this.ttlNanos = Math.max(Duration.ofSeconds(1).toNanos(), Objects.requireNonNull(ttl).toNanos());
            if (maxEntries < 16 || maxEntries > 1_000_000) throw new IllegalArgumentException("maxEntries must be 16..1000000");
            this.maxEntries = maxEntries;
        }

        public synchronized boolean first(String token) {
            long now = System.nanoTime();
            purgeExpired(now);
            String hash = hashToken(token);
            Long previous = seen.get(hash);
            if (previous != null && now - previous <= ttlNanos) return false;
            if (previous != null) seen.remove(hash);
            seen.put(hash, now);
            evictOverflow();
            return true;
        }

        private void purgeExpired(long now) {
            Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                if (now - entry.getValue() <= ttlNanos) break;
                iterator.remove();
            }
        }

        private void evictOverflow() {
            Iterator<String> iterator = seen.keySet().iterator();
            while (seen.size() > maxEntries && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        public synchronized int size() { return seen.size(); }
        public int maximumSize() { return maxEntries; }
    }

    private static String sanitize(String source) {
        String normalized = Objects.requireNonNull(source).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (normalized.isBlank()) throw new IllegalArgumentException("Source module cannot be empty");
        return normalized;
    }
}
