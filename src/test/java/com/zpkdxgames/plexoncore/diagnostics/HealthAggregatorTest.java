package com.zpkdxgames.plexoncore.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthAggregatorTest {
    @Test
    void readyContributorCannotEraseUnrelatedDegradation() {
        var snapshot = HealthAggregator.aggregate(List.of(
                new HealthAggregator.Contributor("scheduler", HealthAggregator.ContributorState.DEGRADED, "failure"),
                new HealthAggregator.Contributor("config", HealthAggregator.ContributorState.READY, "valid")));
        assertEquals(HealthAggregator.OverallState.DEGRADED, snapshot.state());
    }

    @Test
    void incompatibleOrFailedContributorDominatesDegradedAndReady() {
        var incompatible = HealthAggregator.aggregate(List.of(
                new HealthAggregator.Contributor("config", HealthAggregator.ContributorState.READY, "valid"),
                new HealthAggregator.Contributor("module", HealthAggregator.ContributorState.INCOMPATIBLE, "api"),
                new HealthAggregator.Contributor("storage", HealthAggregator.ContributorState.DEGRADED, "retry")));
        assertEquals(HealthAggregator.OverallState.FAILED, incompatible.state());

        var failed = HealthAggregator.aggregate(List.of(
                new HealthAggregator.Contributor("storage", HealthAggregator.ContributorState.FAILED, "offline"),
                new HealthAggregator.Contributor("scheduler", HealthAggregator.ContributorState.READY, "ready")));
        assertEquals(HealthAggregator.OverallState.FAILED, failed.state());
    }

    @Test
    void recoveryRequiresDegradingContributorItselfToBecomeReady() {
        var degraded = HealthAggregator.aggregate(List.of(
                new HealthAggregator.Contributor("origin", HealthAggregator.ContributorState.DEGRADED, "retrying"),
                new HealthAggregator.Contributor("scheduler", HealthAggregator.ContributorState.READY, "ready")));
        assertEquals(HealthAggregator.OverallState.DEGRADED, degraded.state());

        var recovered = HealthAggregator.aggregate(List.of(
                new HealthAggregator.Contributor("origin", HealthAggregator.ContributorState.READY, "ready"),
                new HealthAggregator.Contributor("scheduler", HealthAggregator.ContributorState.READY, "ready")));
        assertEquals(HealthAggregator.OverallState.READY, recovered.state());
    }
}
