package com.zpkdxgames.plexoncore.event;

import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreEventGatewayDispatchTest {
    @Test
    void successfulCommittedEventRoutesExactlyOnceToEachSubscriber() {
        AtomicInteger skills = new AtomicInteger();
        AtomicInteger jobs = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        var plan = new SubscriptionRegistry.RoutePlan(List.of(
                subscriber("skills", skills),
                subscriber("jobs", jobs)), true, Set.of());

        int delivered = CoreEventGateway.dispatchCommitted(
                plan, context(), false, (module, failure) -> failures.incrementAndGet());

        assertEquals(2, delivered);
        assertEquals(1, skills.get());
        assertEquals(1, jobs.get());
        assertEquals(0, failures.get());
    }

    @Test
    void cancelledEventProducesNoCommittedRouting() {
        AtomicInteger calls = new AtomicInteger();
        var plan = new SubscriptionRegistry.RoutePlan(
                List.of(subscriber("quests", calls)), true, Set.of());

        int delivered = CoreEventGateway.dispatchCommitted(
                plan, context(), true, (module, failure) -> { });

        assertEquals(0, delivered);
        assertEquals(0, calls.get());
    }

    private static SubscriptionRegistry.Subscriber subscriber(String module, AtomicInteger calls) {
        return new SubscriptionRegistry.Subscriber(
                module,
                CoreBlockSubscription.builder().material(Material.STONE).requiresNaturalOrigin(true).build(),
                ignored -> calls.incrementAndGet());
    }

    private static CoreBlockBreakContext context() {
        return new CoreBlockBreakContext(
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "player",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "world",
                1, 64, 1,
                Material.STONE,
                null,
                BlockOrigin.NATURAL,
                100L,
                1L,
                true);
    }
}
