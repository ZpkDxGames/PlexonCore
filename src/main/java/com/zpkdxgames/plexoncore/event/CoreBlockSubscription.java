package com.zpkdxgames.plexoncore.event;

import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record CoreBlockSubscription(Set<Material> materials, boolean requiresNaturalOrigin, Set<String> itemIdentityNamespaces) {
    public CoreBlockSubscription {
        materials = Set.copyOf(Objects.requireNonNull(materials, "materials"));
        if (materials.isEmpty()) throw new IllegalArgumentException("At least one material is required for precompiled routing");
        itemIdentityNamespaces = Set.copyOf(itemIdentityNamespaces == null ? Set.of() : itemIdentityNamespaces);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Set<Material> materials = new LinkedHashSet<>();
        private final Set<String> namespaces = new LinkedHashSet<>();
        private boolean naturalOrigin;

        public Builder material(Material material) {
            materials.add(Objects.requireNonNull(material));
            return this;
        }

        public Builder materials(Collection<Material> values) {
            if (values != null) values.forEach(this::material);
            return this;
        }

        public Builder requiresNaturalOrigin(boolean required) {
            this.naturalOrigin = required;
            return this;
        }

        public Builder requiresMainHandIdentity(String namespace) {
            String normalized = Objects.requireNonNull(namespace).trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid PDC namespace: " + namespace);
            namespaces.add(normalized);
            return this;
        }

        public CoreBlockSubscription build() {
            return new CoreBlockSubscription(materials, naturalOrigin, namespaces);
        }
    }
}
