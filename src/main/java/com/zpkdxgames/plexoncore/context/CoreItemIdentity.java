package com.zpkdxgames.plexoncore.context;

import org.bukkit.Material;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record CoreItemIdentity(Material material, boolean hasCustomData, Map<String, String> knownIdentifiers) {
    public CoreItemIdentity {
        material = Objects.requireNonNull(material, "material");
        knownIdentifiers = Map.copyOf(knownIdentifiers == null ? Map.of() : knownIdentifiers);
    }

    public Optional<String> identifier(String key) {
        return Optional.ofNullable(knownIdentifiers.get(key));
    }
}
