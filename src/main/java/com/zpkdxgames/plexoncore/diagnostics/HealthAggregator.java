package com.zpkdxgames.plexoncore.diagnostics;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable aggregation model: one READY contributor can never erase another active degradation. */
public final class HealthAggregator {
    private HealthAggregator() {}

    public enum ContributorState { READY, DEGRADED, INCOMPATIBLE, FAILED }
    public enum OverallState { READY, DEGRADED, FAILED }

    public record Contributor(String id, ContributorState state, String detail) {
        public Contributor {
            id = Objects.requireNonNull(id, "id");
            state = state == null ? ContributorState.READY : state;
            detail = detail == null ? "" : detail;
        }
    }

    public record Snapshot(OverallState state, List<Contributor> contributors) {
        public Snapshot {
            state = state == null ? OverallState.READY : state;
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
        }
    }

    public static Snapshot aggregate(Collection<Contributor> contributors) {
        List<Contributor> ordered = contributors == null ? List.of() : contributors.stream()
                .sorted(Comparator.comparing(Contributor::id)).toList();
        OverallState overall = OverallState.READY;
        for (Contributor contributor : ordered) {
            if (contributor.state() == ContributorState.FAILED || contributor.state() == ContributorState.INCOMPATIBLE) {
                overall = OverallState.FAILED;
                break;
            }
            if (contributor.state() == ContributorState.DEGRADED) overall = OverallState.DEGRADED;
        }
        return new Snapshot(overall, ordered);
    }
}
