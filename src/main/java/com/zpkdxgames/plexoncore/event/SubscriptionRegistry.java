package com.zpkdxgames.plexoncore.event;

import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class SubscriptionRegistry {
    private final Map<Material, List<Subscriber>> mutableRoutes = new ConcurrentHashMap<>();
    private final Map<Material, RoutePlan> compiledRoutes = new ConcurrentHashMap<>();

    synchronized SubscriptionHandle subscribe(String moduleId, CoreBlockSubscription subscription, CoreBlockBreakHandler handler) {
        String normalized = normalizeModuleId(moduleId);
        Subscriber subscriber = new Subscriber(normalized, Objects.requireNonNull(subscription), Objects.requireNonNull(handler));
        for (Material material : subscription.materials()) {
            List<Subscriber> list = new ArrayList<>(mutableRoutes.getOrDefault(material, List.of()));
            list.add(subscriber);
            mutableRoutes.put(material, list);
            compile(material, list);
        }
        return new SubscriptionHandle(() -> unsubscribe(subscriber));
    }

    RoutePlan plan(Material material) {
        return compiledRoutes.getOrDefault(material, RoutePlan.EMPTY);
    }

    int routeCount() {
        return compiledRoutes.size();
    }

    private synchronized void unsubscribe(Subscriber subscriber) {
        for (Material material : subscriber.subscription().materials()) {
            List<Subscriber> current = mutableRoutes.get(material);
            if (current == null) continue;
            List<Subscriber> next = new ArrayList<>(current);
            next.remove(subscriber);
            if (next.isEmpty()) {
                mutableRoutes.remove(material);
                compiledRoutes.remove(material);
            } else {
                mutableRoutes.put(material, next);
                compile(material, next);
            }
        }
    }

    private void compile(Material material, List<Subscriber> subscribers) {
        boolean origin = false;
        Set<String> namespaces = new LinkedHashSet<>();
        for (Subscriber subscriber : subscribers) {
            origin |= subscriber.subscription().requiresNaturalOrigin();
            namespaces.addAll(subscriber.subscription().itemIdentityNamespaces());
        }
        compiledRoutes.put(material, new RoutePlan(List.copyOf(subscribers), origin, Set.copyOf(namespaces)));
    }

    private static String normalizeModuleId(String value) {
        String normalized = Objects.requireNonNull(value, "moduleId").trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid module id: " + value);
        return normalized;
    }

    @FunctionalInterface
    interface CoreBlockBreakHandler {
        void handle(CoreBlockBreakContext context);
    }

    record Subscriber(String moduleId, CoreBlockSubscription subscription, CoreBlockBreakHandler handler) {}

    record RoutePlan(List<Subscriber> subscribers, boolean requiresNaturalOrigin, Set<String> itemIdentityNamespaces) {
        static final RoutePlan EMPTY = new RoutePlan(List.of(), false, Set.of());
        boolean empty() { return subscribers.isEmpty(); }
    }

    static final class SubscriptionHandle implements AutoCloseable {
        private final Runnable closeAction;
        private final AtomicBoolean closed = new AtomicBoolean();
        private SubscriptionHandle(Runnable closeAction) { this.closeAction = closeAction; }
        @Override public void close() { if (closed.compareAndSet(false, true)) closeAction.run(); }
    }
}
