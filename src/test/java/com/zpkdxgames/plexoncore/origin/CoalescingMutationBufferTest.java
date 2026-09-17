package com.zpkdxgames.plexoncore.origin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalescingMutationBufferTest {
    @Test
    void repeatedSameKeyMutationsCoalesceWithLastWriteWinning() {
        CoalescingMutationBuffer<String> buffer = new CoalescingMutationBuffer<>();
        buffer.put("block", CoalescingMutationBuffer.State.PRESENT);
        buffer.put("block", CoalescingMutationBuffer.State.ABSENT);
        buffer.put("block", CoalescingMutationBuffer.State.PRESENT);

        assertEquals(1, buffer.pendingSize());
        var batch = buffer.drain(16);
        assertEquals(1, batch.size());
        assertEquals(CoalescingMutationBuffer.State.PRESENT, batch.mutations().getFirst().state());

        buffer.complete(batch);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void insertDeleteAndDeleteInsertOrderingIsDeterministic() {
        CoalescingMutationBuffer<String> buffer = new CoalescingMutationBuffer<>();
        buffer.put("a", CoalescingMutationBuffer.State.PRESENT);
        buffer.put("a", CoalescingMutationBuffer.State.ABSENT);
        assertEquals(CoalescingMutationBuffer.State.ABSENT, buffer.drain(1).mutations().getFirst().state());

        buffer.clear();
        buffer.put("a", CoalescingMutationBuffer.State.ABSENT);
        buffer.put("a", CoalescingMutationBuffer.State.PRESENT);
        assertEquals(CoalescingMutationBuffer.State.PRESENT, buffer.drain(1).mutations().getFirst().state());
    }

    @Test
    void failedBatchRemainsRetryable() {
        CoalescingMutationBuffer<String> buffer = new CoalescingMutationBuffer<>();
        buffer.put("block", CoalescingMutationBuffer.State.PRESENT);
        var first = buffer.drain(16);
        buffer.retry(first);

        assertEquals(1, buffer.pendingSize());
        assertEquals(1, buffer.drain(16).size());
    }

    @Test
    void newerMutationSurvivesCompletionOfOlderInflightBatch() {
        CoalescingMutationBuffer<String> buffer = new CoalescingMutationBuffer<>();
        buffer.put("block", CoalescingMutationBuffer.State.PRESENT);
        var inFlight = buffer.drain(16);
        buffer.put("block", CoalescingMutationBuffer.State.ABSENT);

        buffer.complete(inFlight);

        assertEquals(1, buffer.pendingSize());
        assertEquals(CoalescingMutationBuffer.State.ABSENT, buffer.drain(16).mutations().getFirst().state());
    }
}
