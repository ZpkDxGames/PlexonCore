package com.zpkdxgames.plexoncore.event;

import com.zpkdxgames.plexoncore.context.CoreItemIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class CoreItemIdentityResolver {
    private final CoreRuntimeMetrics metrics;

    CoreItemIdentityResolver(CoreRuntimeMetrics metrics) {
        this.metrics = metrics;
    }

    CoreItemIdentity resolve(ItemStack item, Set<String> requestedNamespaces) {
        metrics.itemIdentityInspection();
        if (item == null) return null;
        if (requestedNamespaces == null || requestedNamespaces.isEmpty() || !item.hasItemMeta()) {
            return new CoreItemIdentity(item.getType(), false, Map.of());
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.isEmpty()) return new CoreItemIdentity(item.getType(), false, Map.of());
        Map<String, String> values = new LinkedHashMap<>();
        for (NamespacedKey key : pdc.getKeys()) {
            if (!requestedNamespaces.contains(key.getNamespace())) continue;
            String value = readValue(pdc, key);
            if (value != null) values.put(key.asString(), value);
        }
        return new CoreItemIdentity(item.getType(), true, values);
    }

    private String readValue(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING)) {
            metrics.pdcRead();
            return pdc.get(key, PersistentDataType.STRING);
        }
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            metrics.pdcRead();
            Integer value = pdc.get(key, PersistentDataType.INTEGER);
            return value == null ? null : Integer.toString(value);
        }
        if (pdc.has(key, PersistentDataType.LONG)) {
            metrics.pdcRead();
            Long value = pdc.get(key, PersistentDataType.LONG);
            return value == null ? null : Long.toString(value);
        }
        if (pdc.has(key, PersistentDataType.BYTE)) {
            metrics.pdcRead();
            Byte value = pdc.get(key, PersistentDataType.BYTE);
            return value == null ? null : Byte.toString(value);
        }
        return null;
    }
}
