package com.sunil.finintel.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock
    private OutboxRelay relay;

    @Test
    void keepsPublishingWhileBatchesAreFull() {
        when(relay.batchSize()).thenReturn(100);
        when(relay.publishBatch()).thenReturn(100, 100, 40);

        new OutboxScheduler(relay, true, 20).run();

        verify(relay, times(3)).publishBatch();
    }

    @Test
    void stopsAfterMaxRounds() {
        when(relay.batchSize()).thenReturn(100);
        when(relay.publishBatch()).thenReturn(100);

        new OutboxScheduler(relay, true, 5).run();

        verify(relay, times(5)).publishBatch();
    }

    @Test
    void relayErrorEndsTheRun() {
        when(relay.publishBatch()).thenThrow(new IllegalStateException("database down"));

        new OutboxScheduler(relay, true, 20).run();

        verify(relay, times(1)).publishBatch();
    }

    @Test
    void disabledDoesNothing() {
        new OutboxScheduler(relay, false, 20).run();

        verify(relay, never()).publishBatch();
    }
}
